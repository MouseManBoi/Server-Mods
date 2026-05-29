package net.baconeater.features.commands.shader.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DomainDisplayGltfRenderer {
    private static final String TARGET_ANIMATION_NAME = "sukuna";
    private final List<Node> nodes;
    private final List<Mesh> meshes;
    private final List<Channel> channels;
    private final int[] roots;
    private final int cameraNode;

    private DomainDisplayGltfRenderer(List<Node> nodes, List<Mesh> meshes, List<Channel> channels, int[] roots, int cameraNode) {
        this.nodes = nodes;
        this.meshes = meshes;
        this.channels = channels;
        this.roots = roots;
        this.cameraNode = cameraNode;
    }

    static DomainDisplayGltfRenderer load(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        ByteBuffer buffer = readEmbeddedBuffer(root.getAsJsonArray("buffers").get(0).getAsJsonObject());
        JsonArray accessors = root.getAsJsonArray("accessors");
        JsonArray bufferViews = root.getAsJsonArray("bufferViews");

        List<Mesh> meshes = readMeshes(root.getAsJsonArray("meshes"), accessors, bufferViews, buffer);
        List<Node> nodes = readNodes(root.getAsJsonArray("nodes"));
        List<Channel> channels = readAnimation(root.getAsJsonArray("animations"), accessors, bufferViews, buffer, TARGET_ANIMATION_NAME);
        int[] roots = readSceneRoots(root);
        int cameraNode = findCameraNode(nodes);
        return new DomainDisplayGltfRenderer(nodes, meshes, channels, roots, cameraNode);
    }

    NativeImage render(NativeImage skin, int size, float seconds) {
        NativeImage image = new NativeImage(size, size, false);
        clear(image);
        if (nodes.isEmpty()) {
            return image;
        }

        Matrix4f[] nodeTransforms = computeNodeTransforms(seconds);
        boolean useCameraProjection = cameraNode >= 0;
        Matrix4f view = useCameraProjection
                ? new Matrix4f(nodeTransforms[cameraNode]).invert()
                : new Matrix4f().rotateX((float) Math.toRadians(9.0)).rotateY((float) Math.toRadians(-12.0));

        List<Triangle> triangles = collectTriangles(nodeTransforms, view);
        if (triangles.isEmpty()) {
            return image;
        }

        if (useCameraProjection) {
            rasterizeCameraAnchored(image, skin, triangles);
            return image;
        }

        Bounds bounds = bounds(triangles);
        float maxDimension = Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY);
        if (maxDimension <= 0.0001f) {
            return image;
        }
        float scale = size * 0.78f / maxDimension;
        float offsetX = size * 0.5f - (bounds.minX + bounds.maxX) * 0.5f * scale - size * 0.13f;
        float offsetY = size * 0.5f + (bounds.minY + bounds.maxY) * 0.5f * scale + size * 0.02f;
        float[] depth = new float[size * size];
        java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY);

        for (Triangle triangle : triangles) {
            rasterize(image, skin, depth, triangle.project(size, scale, offsetX, offsetY));
        }
        return image;
    }

    private static void rasterizeCameraAnchored(NativeImage image, NativeImage skin, List<Triangle> triangles) {
        int size = image.getWidth();
        float[] depth = new float[size * size];
        java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY);

        float focalLength = size * 0.96f;
        for (Triangle triangle : triangles) {
            rasterize(image, skin, depth, triangle.projectCamera(size, focalLength));
        }
    }

    private Matrix4f[] computeNodeTransforms(float seconds) {
        Pose[] poses = new Pose[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            poses[i] = new Pose(new Vector3f(node.translation), new Quaternionf(node.rotation), new Vector3f(node.scale));
        }
        for (Channel channel : channels) {
            channel.apply(seconds, poses);
        }

        Matrix4f[] transforms = new Matrix4f[nodes.size()];
        for (int root : roots) {
            applyNodeTransform(root, new Matrix4f(), poses, transforms);
        }
        for (int i = 0; i < transforms.length; i++) {
            if (transforms[i] == null) {
                applyNodeTransform(i, new Matrix4f(), poses, transforms);
            }
        }
        return transforms;
    }

    private void applyNodeTransform(int nodeIndex, Matrix4f parent, Pose[] poses, Matrix4f[] transforms) {
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return;
        }
        Node node = nodes.get(nodeIndex);
        Pose pose = poses[nodeIndex];
        Matrix4f local = new Matrix4f()
                .translate(pose.translation)
                .rotate(pose.rotation)
                .scale(pose.scale);
        Matrix4f world = new Matrix4f(parent).mul(local);
        transforms[nodeIndex] = world;
        for (int child : node.children) {
            applyNodeTransform(child, world, poses, transforms);
        }
    }

    private List<Triangle> collectTriangles(Matrix4f[] nodeTransforms, Matrix4f view) {
        List<Triangle> triangles = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.mesh < 0 || node.mesh >= meshes.size()) {
                continue;
            }
            if (i == cameraNode || isDescendantOf(i, cameraNode)) {
                continue;
            }
            Matrix4f transform = new Matrix4f(view).mul(nodeTransforms[i]);
            for (Primitive primitive : meshes.get(node.mesh).primitives) {
                for (int j = 0; j + 2 < primitive.indices.length; j += 3) {
                    int a = primitive.indices[j];
                    int b = primitive.indices[j + 1];
                    int c = primitive.indices[j + 2];
                    if (a >= primitive.positions.length || b >= primitive.positions.length || c >= primitive.positions.length) {
                        continue;
                    }
                    triangles.add(new Triangle(
                            transform(primitive.positions[a], transform),
                            transform(primitive.positions[b], transform),
                            transform(primitive.positions[c], transform),
                            primitive.uvs[a],
                            primitive.uvs[b],
                            primitive.uvs[c],
                            false
                    ));
                }
            }
        }
        return triangles;
    }

    private boolean isDescendantOf(int nodeIndex, int possibleParent) {
        if (nodeIndex < 0 || possibleParent < 0 || possibleParent >= nodes.size()) {
            return false;
        }
        for (int child : nodes.get(possibleParent).children) {
            if (child == nodeIndex || isDescendantOf(nodeIndex, child)) {
                return true;
            }
        }
        return false;
    }

    private static Vector3f transform(Vector3f position, Matrix4f transform) {
        Vector4f out = transform.transform(new Vector4f(position.x, position.y, position.z, 1.0f));
        return new Vector3f(out.x, out.y, out.z);
    }

    private static Bounds bounds(List<Triangle> triangles) {
        Bounds bounds = new Bounds();
        for (Triangle triangle : triangles) {
            bounds.include(triangle.a);
            bounds.include(triangle.b);
            bounds.include(triangle.c);
        }
        return bounds;
    }

    private static void rasterize(NativeImage image, NativeImage skin, float[] depth, Triangle triangle) {
        int width = image.getWidth();
        int height = image.getHeight();
        int minX = clamp((int) Math.floor(Math.min(triangle.a.x, Math.min(triangle.b.x, triangle.c.x))), 0, width - 1);
        int maxX = clamp((int) Math.ceil(Math.max(triangle.a.x, Math.max(triangle.b.x, triangle.c.x))), 0, width - 1);
        int minY = clamp((int) Math.floor(Math.min(triangle.a.y, Math.min(triangle.b.y, triangle.c.y))), 0, height - 1);
        int maxY = clamp((int) Math.ceil(Math.max(triangle.a.y, Math.max(triangle.b.y, triangle.c.y))), 0, height - 1);
        float area = edge(triangle.a, triangle.b, triangle.c.x, triangle.c.y);
        if (Math.abs(area) < 0.0001f) {
            return;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = edge(triangle.b, triangle.c, px, py) / area;
                float w1 = edge(triangle.c, triangle.a, px, py) / area;
                float w2 = edge(triangle.a, triangle.b, px, py) / area;
                if (w0 < -0.001f || w1 < -0.001f || w2 < -0.001f) {
                    continue;
                }

                float z = triangle.a.z * w0 + triangle.b.z * w1 + triangle.c.z * w2;
                int depthIndex = y * width + x;
                if (z <= depth[depthIndex]) {
                    continue;
                }

                float u;
                float v;
                if (triangle.perspective) {
                    float perspectiveWeight = triangle.a.z * w0 + triangle.b.z * w1 + triangle.c.z * w2;
                    if (perspectiveWeight <= 0.000001f) {
                        continue;
                    }
                    u = (triangle.uvA.x * triangle.a.z * w0
                            + triangle.uvB.x * triangle.b.z * w1
                            + triangle.uvC.x * triangle.c.z * w2) / perspectiveWeight;
                    v = (triangle.uvA.y * triangle.a.z * w0
                            + triangle.uvB.y * triangle.b.z * w1
                            + triangle.uvC.y * triangle.c.z * w2) / perspectiveWeight;
                } else {
                    u = triangle.uvA.x * w0 + triangle.uvB.x * w1 + triangle.uvC.x * w2;
                    v = triangle.uvA.y * w0 + triangle.uvB.y * w1 + triangle.uvC.y * w2;
                }
                int color = sampleSkin(skin, u, v);
                if (((color >>> 24) & 0xFF) < 8) {
                    continue;
                }

                depth[depthIndex] = z;
                image.setColorArgb(x, y, color);
            }
        }
    }

    private static int sampleSkin(NativeImage skin, float u, float v) {
        int x = clamp((int) (u * skin.getWidth()), 0, skin.getWidth() - 1);
        int y = clamp((int) (v * skin.getHeight()), 0, skin.getHeight() - 1);
        return skin.getColorArgb(x, y);
    }

    private static float edge(Vector3f a, Vector3f b, float x, float y) {
        return (x - a.x) * (b.y - a.y) - (y - a.y) * (b.x - a.x);
    }

    private static void clear(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setColorArgb(x, y, 0);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ByteBuffer readEmbeddedBuffer(JsonObject buffer) throws IOException {
        String uri = buffer.get("uri").getAsString();
        int comma = uri.indexOf(',');
        if (!uri.startsWith("data:") || comma < 0) {
            throw new IOException("Only embedded glTF buffers are supported");
        }
        return ByteBuffer.wrap(Base64.getDecoder().decode(uri.substring(comma + 1))).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static List<Mesh> readMeshes(JsonArray meshArray, JsonArray accessors, JsonArray bufferViews, ByteBuffer buffer) {
        List<Mesh> meshes = new ArrayList<>();
        for (JsonElement meshElement : meshArray) {
            JsonArray primitives = meshElement.getAsJsonObject().getAsJsonArray("primitives");
            List<Primitive> primitiveList = new ArrayList<>();
            for (JsonElement primitiveElement : primitives) {
                JsonObject primitive = primitiveElement.getAsJsonObject();
                JsonObject attributes = primitive.getAsJsonObject("attributes");
                Vector3f[] positions = readVec3Accessor(attributes.get("POSITION").getAsInt(), accessors, bufferViews, buffer);
                Vector2f[] uvs = readVec2Accessor(attributes.get("TEXCOORD_0").getAsInt(), accessors, bufferViews, buffer);
                int[] indices = readScalarAccessor(primitive.get("indices").getAsInt(), accessors, bufferViews, buffer);
                primitiveList.add(new Primitive(positions, uvs, indices));
            }
            meshes.add(new Mesh(primitiveList));
        }
        return meshes;
    }

    private static List<Node> readNodes(JsonArray nodeArray) {
        List<Node> nodes = new ArrayList<>();
        for (JsonElement element : nodeArray) {
            JsonObject node = element.getAsJsonObject();
            String name = node.has("name") ? node.get("name").getAsString() : "";
            int mesh = node.has("mesh") ? node.get("mesh").getAsInt() : -1;
            int[] children = node.has("children") ? readIntArray(node.getAsJsonArray("children")) : new int[0];
            Vector3f translation = node.has("translation") ? readVector3(node.getAsJsonArray("translation"), 0.0f, 0.0f, 0.0f) : new Vector3f();
            Quaternionf rotation = node.has("rotation") ? readQuaternion(node.getAsJsonArray("rotation")) : new Quaternionf();
            Vector3f scale = node.has("scale") ? readVector3(node.getAsJsonArray("scale"), 1.0f, 1.0f, 1.0f) : new Vector3f(1.0f);
            nodes.add(new Node(name, mesh, children, translation, rotation, scale));
        }
        return nodes;
    }

    private static List<Channel> readAnimation(
            JsonArray animationArray,
            JsonArray accessors,
            JsonArray bufferViews,
            ByteBuffer buffer,
            String targetAnimationName) {
        List<Channel> channels = new ArrayList<>();
        if (animationArray == null) {
            return channels;
        }

        JsonObject selectedAnimation = null;
        for (JsonElement animationElement : animationArray) {
            JsonObject animation = animationElement.getAsJsonObject();
            String name = animation.has("name") ? animation.get("name").getAsString() : "";
            if (targetAnimationName.equals(name)) {
                selectedAnimation = animation;
                break;
            }
        }
        if (selectedAnimation == null && !animationArray.isEmpty()) {
            selectedAnimation = animationArray.get(0).getAsJsonObject();
        }
        if (selectedAnimation == null) {
            return channels;
        }

        JsonArray samplers = selectedAnimation.getAsJsonArray("samplers");
        JsonArray channelArray = selectedAnimation.getAsJsonArray("channels");
        for (JsonElement channelElement : channelArray) {
            JsonObject channel = channelElement.getAsJsonObject();
            JsonObject target = channel.getAsJsonObject("target");
            int samplerIndex = channel.get("sampler").getAsInt();
            JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
            float[] times = readFloatAccessor(sampler.get("input").getAsInt(), accessors, bufferViews, buffer);
            float[] values = readFloatAccessor(sampler.get("output").getAsInt(), accessors, bufferViews, buffer);
            channels.add(new Channel(target.get("node").getAsInt(), target.get("path").getAsString(), times, values));
        }
        return channels;
    }

    private static int[] readSceneRoots(JsonObject root) {
        if (root.has("scenes")) {
            int sceneIndex = root.has("scene") ? root.get("scene").getAsInt() : 0;
            JsonArray nodes = root.getAsJsonArray("scenes").get(sceneIndex).getAsJsonObject().getAsJsonArray("nodes");
            if (nodes != null) {
                return readIntArray(nodes);
            }
        }

        JsonArray nodeArray = root.getAsJsonArray("nodes");
        Set<Integer> children = new HashSet<>();
        for (JsonElement element : nodeArray) {
            JsonObject node = element.getAsJsonObject();
            if (!node.has("children")) {
                continue;
            }
            for (JsonElement child : node.getAsJsonArray("children")) {
                children.add(child.getAsInt());
            }
        }
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < nodeArray.size(); i++) {
            if (!children.contains(i)) {
                roots.add(i);
            }
        }
        return roots.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int findCameraNode(List<Node> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            if ("camera".equalsIgnoreCase(nodes.get(i).name)) {
                return i;
            }
        }
        return -1;
    }

    private static Vector3f[] readVec3Accessor(int index, JsonArray accessors, JsonArray bufferViews, ByteBuffer buffer) {
        Accessor accessor = accessor(index, accessors, bufferViews);
        Vector3f[] values = new Vector3f[accessor.count];
        for (int i = 0; i < accessor.count; i++) {
            int offset = accessor.byteOffset + i * accessor.stride;
            values[i] = new Vector3f(buffer.getFloat(offset), buffer.getFloat(offset + 4), buffer.getFloat(offset + 8));
        }
        return values;
    }

    private static Vector2f[] readVec2Accessor(int index, JsonArray accessors, JsonArray bufferViews, ByteBuffer buffer) {
        Accessor accessor = accessor(index, accessors, bufferViews);
        Vector2f[] values = new Vector2f[accessor.count];
        for (int i = 0; i < accessor.count; i++) {
            int offset = accessor.byteOffset + i * accessor.stride;
            values[i] = new Vector2f(buffer.getFloat(offset), buffer.getFloat(offset + 4));
        }
        return values;
    }

    private static float[] readFloatAccessor(int index, JsonArray accessors, JsonArray bufferViews, ByteBuffer buffer) {
        Accessor accessor = accessor(index, accessors, bufferViews);
        int components = components(accessor.type);
        float[] values = new float[accessor.count * components];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat(accessor.byteOffset + i * Float.BYTES);
        }
        return values;
    }

    private static int[] readScalarAccessor(int index, JsonArray accessors, JsonArray bufferViews, ByteBuffer buffer) {
        Accessor accessor = accessor(index, accessors, bufferViews);
        int[] values = new int[accessor.count];
        for (int i = 0; i < accessor.count; i++) {
            int offset = accessor.byteOffset + i * accessor.stride;
            values[i] = switch (accessor.componentType) {
                case 5121 -> Byte.toUnsignedInt(buffer.get(offset));
                case 5123 -> Short.toUnsignedInt(buffer.getShort(offset));
                case 5125 -> buffer.getInt(offset);
                default -> 0;
            };
        }
        return values;
    }

    private static Accessor accessor(int index, JsonArray accessors, JsonArray bufferViews) {
        JsonObject accessor = accessors.get(index).getAsJsonObject();
        JsonObject bufferView = bufferViews.get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        int byteOffset = getInt(bufferView, "byteOffset", 0) + getInt(accessor, "byteOffset", 0);
        int componentType = accessor.get("componentType").getAsInt();
        int count = accessor.get("count").getAsInt();
        String type = accessor.get("type").getAsString();
        int stride = getInt(bufferView, "byteStride", componentSize(componentType) * components(type));
        return new Accessor(byteOffset, componentType, count, type, stride);
    }

    private static int componentSize(int componentType) {
        return switch (componentType) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            default -> 4;
        };
    }

    private static int components(String type) {
        return switch (type) {
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> 1;
        };
    }

    private static int getInt(JsonObject object, String name, int fallback) {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static int[] readIntArray(JsonArray array) {
        int[] values = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsInt();
        }
        return values;
    }

    private static Vector3f readVector3(JsonArray array, float fallbackX, float fallbackY, float fallbackZ) {
        return new Vector3f(
                array.size() > 0 ? array.get(0).getAsFloat() : fallbackX,
                array.size() > 1 ? array.get(1).getAsFloat() : fallbackY,
                array.size() > 2 ? array.get(2).getAsFloat() : fallbackZ);
    }

    private static Quaternionf readQuaternion(JsonArray array) {
        return new Quaternionf(
                array.size() > 0 ? array.get(0).getAsFloat() : 0.0f,
                array.size() > 1 ? array.get(1).getAsFloat() : 0.0f,
                array.size() > 2 ? array.get(2).getAsFloat() : 0.0f,
                array.size() > 3 ? array.get(3).getAsFloat() : 1.0f);
    }

    private record Accessor(int byteOffset, int componentType, int count, String type, int stride) {
    }

    private record Node(String name, int mesh, int[] children, Vector3f translation, Quaternionf rotation, Vector3f scale) {
    }

    private record Mesh(List<Primitive> primitives) {
    }

    private record Primitive(Vector3f[] positions, Vector2f[] uvs, int[] indices) {
    }

    private record Pose(Vector3f translation, Quaternionf rotation, Vector3f scale) {
    }

    private record Channel(int node, String path, float[] times, float[] values) {
        void apply(float seconds, Pose[] poses) {
            if (node < 0 || node >= poses.length || times.length == 0) {
                return;
            }

            float duration = times[times.length - 1];
            float time = duration > 0.0f ? seconds % duration : 0.0f;
            int index = 0;
            while (index + 1 < times.length && times[index + 1] < time) {
                index++;
            }
            int next = Math.min(index + 1, times.length - 1);
            float span = Math.max(times[next] - times[index], 0.0001f);
            float alpha = next == index ? 0.0f : (time - times[index]) / span;
            Pose pose = poses[node];

            if ("rotation".equals(path)) {
                Quaternionf from = quaternion(index);
                Quaternionf to = quaternion(next);
                pose.rotation.set(from).slerp(to, alpha);
            } else if ("translation".equals(path)) {
                pose.translation.set(vector3(index).lerp(vector3(next), alpha));
            } else if ("scale".equals(path)) {
                pose.scale.set(vector3(index).lerp(vector3(next), alpha));
            }
        }

        private Vector3f vector3(int keyframe) {
            int offset = keyframe * 3;
            return new Vector3f(values[offset], values[offset + 1], values[offset + 2]);
        }

        private Quaternionf quaternion(int keyframe) {
            int offset = keyframe * 4;
            return new Quaternionf(values[offset], values[offset + 1], values[offset + 2], values[offset + 3]);
        }
    }

    private record Triangle(Vector3f a, Vector3f b, Vector3f c, Vector2f uvA, Vector2f uvB, Vector2f uvC, boolean perspective) {
        Triangle project(int size, float scale, float offsetX, float offsetY) {
            return new Triangle(project(a, scale, offsetX, offsetY), project(b, scale, offsetX, offsetY), project(c, scale, offsetX, offsetY), uvA, uvB, uvC, false);
        }

        Triangle projectCamera(int size, float focalLength) {
            return new Triangle(
                    projectCamera(a, size, focalLength),
                    projectCamera(b, size, focalLength),
                    projectCamera(c, size, focalLength),
                    uvA,
                    uvB,
                    uvC,
                    true);
        }

        private static Vector3f project(Vector3f point, float scale, float offsetX, float offsetY) {
            return new Vector3f(point.x * scale + offsetX, -point.y * scale + offsetY, -point.z);
        }

        private static Vector3f projectCamera(Vector3f point, int size, float focalLength) {
            float depth = Math.abs(point.z);
            if (depth < 0.02f) {
                depth = 0.02f;
            }
            return new Vector3f(
                    point.x / depth * focalLength + size * 0.5f,
                    -point.y / depth * focalLength + size * 0.5f,
                    1.0f / depth);
        }
    }

    private static final class Bounds {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        void include(Vector3f point) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
    }
}
