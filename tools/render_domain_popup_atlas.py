import argparse
import base64
import json
import math
import os
import sys
import tempfile

import bpy
from mathutils import Matrix, Quaternion, Vector


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
    parser.add_argument("--frame-offset-x", type=int, default=0)
    parser.add_argument("--frame-offset-y", type=int, default=0)
    parser.add_argument("--use-scene-camera", action=argparse.BooleanOptionalAction, default=True)
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
    for node in skin_nodes:
        node.interpolation = "Closest"

    if not skin_nodes:
        return

    skin_image = skin_nodes[0].image
    nodes.clear()
    texture_node = nodes.new(type="ShaderNodeTexImage")
    texture_node.image = skin_image
    texture_node.interpolation = "Closest"

    transparent_node = nodes.new(type="ShaderNodeBsdfTransparent")
    emission_node = nodes.new(type="ShaderNodeEmission")
    mix_node = nodes.new(type="ShaderNodeMixShader")
    output_node = nodes.new(type="ShaderNodeOutputMaterial")

    links.new(texture_node.outputs["Color"], emission_node.inputs["Color"])
    links.new(texture_node.outputs["Alpha"], mix_node.inputs["Fac"])
    links.new(transparent_node.outputs["BSDF"], mix_node.inputs[1])
    links.new(emission_node.outputs["Emission"], mix_node.inputs[2])
    links.new(mix_node.outputs["Shader"], output_node.inputs["Surface"])


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


def hide_camera_marker_meshes():
    for obj in bpy.context.scene.objects:
        if obj.type == "MESH" and obj.name.lower().startswith("camera"):
            obj.hide_render = True
            obj.hide_viewport = True


def configure_render_scene(cell_size):
    hide_camera_marker_meshes()
    for obj in bpy.context.scene.objects:
        if obj.type == "MESH":
            obj.visible_shadow = False
            if hasattr(obj, "cycles_visibility"):
                obj.cycles_visibility.shadow = False

    scene = bpy.context.scene
    scene.render.resolution_x = cell_size
    scene.render.resolution_y = cell_size
    scene.render.film_transparent = True
    if hasattr(scene.eevee, "use_gtao"):
        scene.eevee.use_gtao = False
    scene.eevee.taa_render_samples = 16
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1


def find_imported_camera():
    if bpy.context.scene.camera is not None:
        return bpy.context.scene.camera
    cameras = [obj for obj in bpy.context.scene.objects if obj.type == "CAMERA"]
    named = [obj for obj in cameras if obj.name.lower() == "camera"]
    if named:
        return named[0]
    if cameras:
        return cameras[0]
    return None


def find_camera_marker():
    for obj in bpy.context.scene.objects:
        if obj.type != "CAMERA" and obj.name.lower() == "camera":
            return obj
    for obj in bpy.context.scene.objects:
        if obj.type != "CAMERA" and obj.name.lower().startswith("camera"):
            return obj
    return None


def read_gltf_accessor(gltf, accessor_index):
    accessor = gltf["accessors"][accessor_index]
    view = gltf["bufferViews"][accessor["bufferView"]]
    buffer_info = gltf["buffers"][view["buffer"]]
    if not buffer_info["uri"].startswith("data:"):
        return []

    data = base64.b64decode(buffer_info["uri"].split(",", 1)[1])
    components = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[accessor["type"]]
    stride = view.get("byteStride", components * 4)
    offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    rows = []
    for i in range(accessor["count"]):
        row = []
        for component in range(components):
            start = offset + i * stride + component * 4
            row.append(float(__import__("struct").unpack_from("<f", data, start)[0]))
        rows.append(row)
    return rows


def convert_blockbench_vector(vector):
    x, y, z = vector
    return Vector((x, z, y))


def rotate_quaternion_vector(quaternion, vector):
    x, y, z, w = quaternion
    vx, vy, vz = vector
    tx = 2.0 * (y * vz - z * vy)
    ty = 2.0 * (z * vx - x * vz)
    tz = 2.0 * (x * vy - y * vx)
    return (
        vx + w * tx + (y * tz - z * ty),
        vy + w * ty + (z * tx - x * tz),
        vz + w * tz + (x * ty - y * tx),
    )


def find_blockbench_camera_transform(gltf_path):
    try:
        with open(gltf_path, "r", encoding="utf-8") as file:
            gltf = json.load(file)
    except Exception:
        return None

    nodes = gltf.get("nodes", [])
    camera_index = next(
        (index for index, node in enumerate(nodes) if node.get("name", "").lower() == "camera"),
        None,
    )
    if camera_index is None:
        return None

    node = nodes[camera_index]
    translation = node.get("translation")
    rotation = node.get("rotation")
    for animation in gltf.get("animations", []):
        for channel in animation.get("channels", []):
            target = channel.get("target", {})
            if target.get("node") != camera_index:
                continue
            sampler = animation["samplers"][channel["sampler"]]
            values = read_gltf_accessor(gltf, sampler["output"])
            if not values:
                continue
            if target.get("path") == "translation":
                translation = values[0]
            elif target.get("path") == "rotation":
                rotation = values[0]

    if translation is None or rotation is None:
        return None

    position = convert_blockbench_vector(translation)
    forward = convert_blockbench_vector(rotate_quaternion_vector(rotation, (0.0, 0.0, -1.0)))
    up = convert_blockbench_vector(rotate_quaternion_vector(rotation, (0.0, 1.0, 0.0)))
    return position, forward, up


def create_camera_from_marker(marker, ortho_scale):
    min_corner, max_corner = get_scene_bounds()
    size = max((max_corner - min_corner).x, (max_corner - min_corner).y, (max_corner - min_corner).z)

    camera_data = bpy.data.cameras.new("domain_popup_marker_camera")
    camera = bpy.data.objects.new("domain_popup_marker_camera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera["domain_camera_marker"] = marker.name
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(0.1, size * ortho_scale)
    return camera


def create_camera_from_raw_transform(position, forward, up, ortho_scale):
    min_corner, max_corner = get_scene_bounds()
    size = max((max_corner - min_corner).x, (max_corner - min_corner).y, (max_corner - min_corner).z)

    camera_data = bpy.data.cameras.new("domain_popup_raw_camera")
    camera = bpy.data.objects.new("domain_popup_raw_camera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera.location = position
    camera.rotation_euler = forward.to_track_quat("-Z", "Y").to_euler()
    camera_data.type = "ORTHO"
    camera_data.ortho_scale = max(0.1, size * ortho_scale)
    return camera


def update_marker_camera_transform(camera):
    marker_name = camera.get("domain_camera_marker")
    marker = bpy.data.objects.get(marker_name) if marker_name else None
    if marker is None:
        return

    # Blockbench marker nodes describe a camera transform, while Blender cameras look along local -Z.
    # This correction maps the marker's authored local direction onto Blender's camera basis.
    camera.matrix_world = marker.matrix_world @ Matrix.Rotation(math.radians(180.0), 4, "Y")


def setup_camera(gltf_path, cell_size, camera_offset, target_offset, ortho_scale, use_scene_camera):
    configure_render_scene(cell_size)
    imported_camera = find_imported_camera() if use_scene_camera else None
    if imported_camera is not None:
        print(f"Using imported scene camera: {imported_camera.name}")
        bpy.context.scene.camera = imported_camera
        return
    raw_camera = find_blockbench_camera_transform(gltf_path) if use_scene_camera else None
    if raw_camera is not None:
        position, forward, up = raw_camera
        camera = create_camera_from_raw_transform(position, forward, up, ortho_scale)
        print(
            "Using raw Blockbench camera transform at "
            f"{tuple(round(v, 4) for v in position)} forward {tuple(round(v, 4) for v in forward)}"
        )
        bpy.context.scene.camera = camera
        return
    camera_marker = find_camera_marker() if use_scene_camera else None
    if camera_marker is not None:
        marker_camera = create_camera_from_marker(camera_marker, ortho_scale)
        update_marker_camera_transform(marker_camera)
        print(f"Using animated camera node directly: {camera_marker.name}")
        bpy.context.scene.camera = marker_camera
        return
    if use_scene_camera:
        print("WARNING: no real glTF/Blender camera was imported; using generated fallback camera")

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
    camera_data.ortho_scale = max(0.1, size * ortho_scale)
    bpy.context.scene.camera = camera


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
    if scene.camera is not None:
        update_marker_camera_transform(scene.camera)
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def pack_atlas(frame_paths, output, cell_size, columns, rows, flip_x, frame_offset):
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
            frame_y = cell_size - 1 - y - frame_offset[1]
            if frame_y < 0 or frame_y >= cell_size:
                continue
            for x in range(cell_size):
                source_x = x - frame_offset[0]
                if source_x < 0 or source_x >= cell_size:
                    continue
                frame_x = cell_size - 1 - source_x if flip_x else source_x
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
        args.gltf,
        args.cell_size,
        (args.camera_x, args.camera_y, args.camera_z),
        (args.target_x, args.target_y, args.target_z),
        args.ortho_scale,
        args.use_scene_camera,
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
        pack_atlas(
            frame_paths,
            args.output,
            args.cell_size,
            args.columns,
            args.rows,
            args.flip_x,
            (args.frame_offset_x, args.frame_offset_y),
        )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
