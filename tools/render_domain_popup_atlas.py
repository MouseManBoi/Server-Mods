import argparse
import math
import os
import sys
import tempfile

import bpy
from mathutils import Vector


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gltf", required=True)
    parser.add_argument("--skin")
    parser.add_argument("--output", required=True)
    parser.add_argument("--fps", type=int, default=60)
    parser.add_argument("--duration", type=float, default=2.6)
    parser.add_argument("--cell-size", type=int, default=256)
    parser.add_argument("--columns", type=int, default=16)
    parser.add_argument("--rows", type=int, default=16)
    parser.add_argument("--reverse", action=argparse.BooleanOptionalAction, default=False)
    parser.add_argument("--camera-x", type=float, default=-3.0)
    parser.add_argument("--camera-y", type=float, default=5.0)
    parser.add_argument("--camera-z", type=float, default=2.8)
    parser.add_argument("--target-x", type=float, default=0.0)
    parser.add_argument("--target-y", type=float, default=0.0)
    parser.add_argument("--target-z", type=float, default=0.0)
    parser.add_argument("--ortho-scale", type=float, default=1.45)
    parser.add_argument("--model-yaw", type=float, default=0.0)
    parser.add_argument("--flip-x", action=argparse.BooleanOptionalAction, default=True)
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    return parser.parse_args(argv)


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete()


def import_gltf(path):
    bpy.ops.import_scene.gltf(filepath=path)


def rotate_model(yaw_degrees):
    if yaw_degrees == 0.0:
        return
    angle = math.radians(yaw_degrees)
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.transform.rotate(value=angle, orient_axis="Z", center_override=(0.0, 0.0, 0.0))
    bpy.ops.object.select_all(action="DESELECT")


def replace_skin_texture(path):
    if not path:
        return

    skin = bpy.data.images.load(path, check_existing=False)
    skin.alpha_mode = "CHANNEL_PACKED"
    skin.use_fake_user = True
    replaced = False
    for image in bpy.data.images:
        if image == skin:
            continue
        if image.name.lower().startswith("skin") or tuple(image.size[:]) == (64, 64):
            image.filepath = skin.filepath
            image.source = "FILE"
            image.reload()
            replaced = True

    for material in bpy.data.materials:
        if not material.use_nodes:
            continue
        material_uses_skin = False
        for node in material.node_tree.nodes:
            if node.type != "TEX_IMAGE":
                continue
            if node.image is None or node.image.name.lower().startswith("skin") or tuple(node.image.size[:]) == (64, 64):
                node.image = skin
                node.interpolation = "Closest"
                material_uses_skin = True
        if material_uses_skin:
            configure_skin_material(material)
            replaced = True

    if not replaced:
        print("WARNING: no 64x64 skin texture was found in the GLTF; using embedded textures")


def configure_skin_material(material):
    material.use_nodes = True
    material.use_backface_culling = False
    if hasattr(material, "blend_method"):
        material.blend_method = "CLIP"
    if hasattr(material, "surface_render_method"):
        material.surface_render_method = "DITHERED"
    if hasattr(material, "alpha_threshold"):
        material.alpha_threshold = 0.05
    if hasattr(material, "show_transparent_back"):
        material.show_transparent_back = True

    nodes = material.node_tree.nodes
    links = material.node_tree.links
    skin_nodes = [node for node in nodes if node.type == "TEX_IMAGE" and node.image]
    bsdf_nodes = [node for node in nodes if node.type == "BSDF_PRINCIPLED"]
    for node in skin_nodes:
        node.interpolation = "Closest"
    for bsdf in bsdf_nodes:
        alpha_input = bsdf.inputs.get("Alpha")
        if alpha_input is None:
            continue
        for node in skin_nodes:
            alpha_output = node.outputs.get("Alpha")
            if alpha_output is not None and not alpha_input.is_linked:
                links.new(alpha_output, alpha_input)
                break


def get_scene_bounds():
    min_corner = Vector((math.inf, math.inf, math.inf))
    max_corner = Vector((-math.inf, -math.inf, -math.inf))
    found = False
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        found = True
        for corner in obj.bound_box:
            world = obj.matrix_world @ Vector(corner)
            min_corner.x = min(min_corner.x, world.x)
            min_corner.y = min(min_corner.y, world.y)
            min_corner.z = min(min_corner.z, world.z)
            max_corner.x = max(max_corner.x, world.x)
            max_corner.y = max(max_corner.y, world.y)
            max_corner.z = max(max_corner.z, world.z)
    if not found:
        return Vector((-1, -1, -1)), Vector((1, 1, 1))
    return min_corner, max_corner


def setup_camera(cell_size, camera_offset, target_offset, ortho_scale):
    min_corner, max_corner = get_scene_bounds()
    center = (min_corner + max_corner) * 0.5
    target = center + Vector(target_offset)
    size = max((max_corner - min_corner).x, (max_corner - min_corner).y, (max_corner - min_corner).z)

    camera_data = bpy.data.cameras.new("domain_popup_camera")
    camera = bpy.data.objects.new("domain_popup_camera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera.location = target + Vector(camera_offset)
    camera.rotation_euler = (target - camera.location).to_track_quat("-Z", "Y").to_euler()
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(1.6, size * ortho_scale)
    bpy.context.scene.camera = camera

    light_data = bpy.data.lights.new("domain_popup_key", "SUN")
    light = bpy.data.objects.new("domain_popup_key", light_data)
    bpy.context.collection.objects.link(light)
    light.rotation_euler = (math.radians(45), 0.0, math.radians(35))
    light_data.energy = 2.0

    scene = bpy.context.scene
    scene.render.resolution_x = cell_size
    scene.render.resolution_y = cell_size
    scene.render.film_transparent = True
    scene.eevee.taa_render_samples = 16
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "Medium High Contrast"
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1


def configure_animation(fps, duration):
    scene = bpy.context.scene
    scene.frame_start = 0
    scene.frame_end = max(1, round(duration * fps) - 1)
    scene.render.fps = fps
    for action in bpy.data.actions:
        action.use_fake_user = True


def render_frame(path, frame, source_frame):
    scene = bpy.context.scene
    scene.frame_set(source_frame)
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def pack_atlas(frame_paths, output, cell_size, columns, rows, flip_x):
    width = cell_size * columns
    height = cell_size * rows
    atlas = bpy.data.images.new("domain_popup_player_atlas", width, height, alpha=True, float_buffer=False)
    pixels = [0.0] * (width * height * 4)

    for frame_index, path in enumerate(frame_paths):
        frame_image = bpy.data.images.load(path, check_existing=False)
        frame_pixels = list(frame_image.pixels)
        col = frame_index % columns
        row = frame_index // columns
        dst_x = col * cell_size
        dst_y_from_top = row * cell_size

        for y in range(cell_size):
            atlas_y = height - 1 - (dst_y_from_top + y)
            frame_y = cell_size - 1 - y
            for x in range(cell_size):
                frame_x = cell_size - 1 - x if flip_x else x
                src = (frame_y * cell_size + frame_x) * 4
                dst = (atlas_y * width + dst_x + x) * 4
                pixels[dst:dst + 4] = frame_pixels[src:src + 4]
        bpy.data.images.remove(frame_image)

    atlas.pixels.foreach_set(pixels)
    atlas.filepath_raw = output
    atlas.file_format = "PNG"
    atlas.save()


def main():
    args = parse_args()
    output_dir = os.path.dirname(os.path.abspath(args.output))
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    clear_scene()
    import_gltf(args.gltf)
    rotate_model(args.model_yaw)
    replace_skin_texture(args.skin)
    setup_camera(
        args.cell_size,
        (args.camera_x, args.camera_y, args.camera_z),
        (args.target_x, args.target_y, args.target_z),
        args.ortho_scale,
    )
    configure_animation(args.fps, args.duration)

    frame_count = min(args.columns * args.rows, max(1, round(args.fps * args.duration)))
    with tempfile.TemporaryDirectory(prefix="domain_popup_frames_") as temp_dir:
        frame_paths = []
        for frame in range(frame_count):
            path = os.path.join(temp_dir, f"frame_{frame:04d}.png")
            source_frame = frame_count - 1 - frame if args.reverse else frame
            render_frame(path, frame, source_frame)
            frame_paths.append(path)
        pack_atlas(frame_paths, args.output, args.cell_size, args.columns, args.rows, args.flip_x)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
