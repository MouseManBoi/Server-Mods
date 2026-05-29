package net.baconeater.features.commands.shader.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class DomainDisplaySkinAtlasManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("DomainDisplaySkinAtlas");
    private static final Identifier DOMAIN_DISPLAY = Identifier.of("minecraft", "domain_display");
    private static final Identifier DOMAIN_POPUP = Identifier.of("minecraft", "domain_popup");
    private static final Identifier PLAYER_SAMPLER_ID = Identifier.of("minecraft", "domain_popup_player");
    private static final Identifier PLAYER_SAMPLER_RESOURCE = Identifier.of("minecraft", "textures/effect/domain_popup_player.png");
    private static final String BLENDER_PATH = "C:\\Program Files\\Blender Foundation\\Blender 5.1\\blender.exe";
    private static final String FALLBACK_GLTF_PATH = "C:\\Users\\joshu\\Downloads\\Domain Expansion Popup.gltf";
    private static final String DOMAIN_SHADER_DIR_NAME = "domain_shader";
    private static final String FETCHED_SKIN_FILE_NAME = "fetched_skin.png";
    private static final String FALLBACK_SKIN_FILE_NAME = "fallback_skin.png";
    private static final String LEGACY_FALLBACK_SKIN_FILE_NAME = "domain_popup_fallback_skin.png";
    private static final String CURRENT_PLAYER_SKIN_FILE_NAME = "current_player_skin.png";
    private static final String ACTIVE_SKIN_FILE_NAME = "active_skin.png";
    private static final String ATLAS_METADATA_FILE_NAME = "active_atlas.properties";
    private static final Duration RENDER_TIMEOUT = Duration.ofMinutes(3);
    private static final ConcurrentMap<String, CompletableFuture<PreparedAtlas>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Identifier, Long> INVALIDATED_POST_EFFECT_GENERATIONS = new ConcurrentHashMap<>();
    private static final String ATLAS_VERSION = "runtime_skin_v1";
    private static final int RUNTIME_TEXTURE_SIZE = 512;
    private static String registeredAtlasKey;
    private static long registeredAtlasGeneration;
    private static long nextPreloadAttemptNanos;
    private static String lastPreloadAttemptKey;
    private static NativeImage runtimeSkinImage;
    private static String runtimeSkinKey;
    private static long animationStartNanos;
    private static int lastAnimationFrame = -1;
    private static NativeImageBackedTexture playerSamplerTexture;
    private static NativeImageBackedTexture playerSamplerResourceTexture;
    private static DomainDisplayGltfRenderer gltfRenderer;
    private static long gltfRendererModifiedMillis = -1;

    private DomainDisplaySkinAtlasManager() {
    }

    public static void preloadCurrentPlayer(MinecraftClient client) {
        if (client == null || System.nanoTime() < nextPreloadAttemptNanos) {
            return;
        }
        nextPreloadAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        try {
            migrateLegacySkinFiles(client);
            Path source = activeSkinSource(client);
            String skinHash = runtimeSkinKey(source);
            if (skinHash.equals(registeredAtlasKey) || skinHash.equals(lastPreloadAttemptKey) || IN_FLIGHT.containsKey(skinHash)) {
                return;
            }
            lastPreloadAttemptKey = skinHash;
            registerRuntimeSkinTexture(client, skinHash, source);
        } catch (Throwable ignored) {
            // The command path still handles missing skins/fallbacks; preload is only an optimization.
        }
    }

    public static void prepareThenRun(
            MinecraftClient client,
            Identifier shaderId,
            Runnable action) {
        if (client == null || action == null) {
            return;
        }
        if (!usesDomainPlayerAtlas(shaderId)) {
            action.run();
            return;
        }

        Path skinPath;
        String skinHash;
        try {
            migrateLegacySkinFiles(client);
            skinPath = activeSkinSource(client);
            skinHash = runtimeSkinKey(skinPath);
            if (skinHash.equals(registeredAtlasKey)) {
                restartAnimation();
                action.run();
                return;
            }
            LOGGER.info("Using domain popup skin source {}", skinPath);
            registerRuntimeSkinTexture(client, skinHash, skinPath);
        } catch (Throwable ignored) {
            action.run();
            return;
        }

        restartAnimation();
        action.run();
    }

    private static void restartAnimation() {
        animationStartNanos = System.nanoTime();
        lastAnimationFrame = -1;
    }


    public static void renderRuntimeAnimation(MinecraftClient client) {
        if (client == null || runtimeSkinImage == null || runtimeSkinKey == null) {
            return;
        }

        long now = System.nanoTime();
        if (animationStartNanos == 0) {
            animationStartNanos = now;
        }

        float elapsedSeconds = (now - animationStartNanos) / 1_000_000_000.0f;
        int frame = (int) (elapsedSeconds * 60.0f);
        if (frame == lastAnimationFrame) {
            return;
        }

        lastAnimationFrame = frame;
        try {
            NativeImage player = renderRuntimePlayerTexture(client, runtimeSkinImage, elapsedSeconds);
            registerAtlas(client, player);
        } catch (Throwable throwable) {
            LOGGER.info("Could not update domain popup runtime animation", throwable);
        }
    }

    public static void clear() {
        IN_FLIGHT.clear();
        INVALIDATED_POST_EFFECT_GENERATIONS.clear();
        registeredAtlasKey = null;
        registeredAtlasGeneration++;
        lastPreloadAttemptKey = null;
        nextPreloadAttemptNanos = 0;
        runtimeSkinKey = null;
        animationStartNanos = 0;
        lastAnimationFrame = -1;
        if (runtimeSkinImage != null) {
            runtimeSkinImage.close();
            runtimeSkinImage = null;
        }
        gltfRenderer = null;
        gltfRendererModifiedMillis = -1;
    }

    public static boolean shouldInvalidateCachedPostEffect(Identifier shaderId) {
        if (!usesDomainPlayerAtlas(shaderId)) {
            return false;
        }
        long generation = registeredAtlasGeneration;
        if (generation <= 0) {
            return false;
        }
        Long previousGeneration = INVALIDATED_POST_EFFECT_GENERATIONS.put(shaderId, generation);
        return previousGeneration == null || previousGeneration != generation;
    }

    private static boolean usesDomainPlayerAtlas(Identifier shaderId) {
        if (DOMAIN_DISPLAY.equals(shaderId) || DOMAIN_POPUP.equals(shaderId)) {
            return true;
        }
        if (shaderId == null) {
            return false;
        }
        String path = shaderId.getPath();
        return path.contains("domain_display") || path.contains("domain_popup");
    }

    private static void registerRuntimeSkinTexture(MinecraftClient client, String skinKey, Path skinPath) throws IOException {
        try (InputStream stream = Files.newInputStream(skinPath)) {
            NativeImage skin = NativeImage.read(stream);
            try {
                if (!skinKey.equals(runtimeSkinKey)) {
                    if (runtimeSkinImage != null) {
                        runtimeSkinImage.close();
                    }
                    runtimeSkinImage = new NativeImage(skin.getWidth(), skin.getHeight(), false);
                    runtimeSkinImage.copyFrom(skin);
                    runtimeSkinKey = skinKey;
                    animationStartNanos = System.nanoTime();
                    lastAnimationFrame = -1;
                }

                NativeImage player = renderRuntimePlayerTexture(client, skin, 0.0f);
                registerAtlas(client, new PreparedAtlas(skinKey, player));
            } finally {
                skin.close();
            }
        }
    }

    private static NativeImage renderRuntimePlayerTexture(MinecraftClient client, NativeImage skin, float time) {
        DomainDisplayGltfRenderer renderer = getGltfRenderer(client);
        if (renderer != null) {
            return renderer.render(skin, RUNTIME_TEXTURE_SIZE, time);
        }
        return renderFrontFacingPlayer(skin, time);
    }

    private static DomainDisplayGltfRenderer getGltfRenderer(MinecraftClient client) {
        try {
            Path model = modelPath(client);
            if (!Files.isRegularFile(model)) {
                return null;
            }

            long modifiedMillis = Files.getLastModifiedTime(model).toMillis();
            if (gltfRenderer != null && gltfRendererModifiedMillis == modifiedMillis) {
                return gltfRenderer;
            }

            gltfRenderer = DomainDisplayGltfRenderer.load(model);
            gltfRendererModifiedMillis = modifiedMillis;
            LOGGER.info("Loaded domain popup Blockbench model {}", model);
            return gltfRenderer;
        } catch (Throwable throwable) {
            gltfRenderer = null;
            gltfRendererModifiedMillis = -1;
            LOGGER.info("Could not load domain popup Blockbench model; using 2D fallback", throwable);
            return null;
        }
    }

    private static NativeImage renderFrontFacingPlayer(NativeImage skin, float time) {
        NativeImage image = new NativeImage(RUNTIME_TEXTURE_SIZE, RUNTIME_TEXTURE_SIZE, false);
        clear(image);

        int scale = 6;
        int centerX = RUNTIME_TEXTURE_SIZE / 2;
        float phase = (float) Math.sin(time * Math.PI * 2.0 / 1.15);
        float settle = Math.min(time / 0.18f, 1.0f);
        float bob = (float) Math.sin(time * Math.PI * 2.0 / 0.58) * 2.0f * settle;
        float bodyAngle = -0.18f + phase * 0.045f * settle;
        float headAngle = bodyAngle * 0.55f - phase * 0.050f * settle;
        float rightArmAngle = -0.82f + phase * 0.120f * settle;
        float leftArmAngle = 0.60f - phase * 0.100f * settle;
        float rightLegAngle = 0.08f - phase * 0.045f * settle;
        float leftLegAngle = -0.06f + phase * 0.045f * settle;

        float bodyPivotX = centerX;
        float bodyPivotY = 82.0f + bob;
        float headPivotX = bodyPivotX;
        float headPivotY = bodyPivotY - 1.0f * scale;
        float rightArmPivotX = bodyPivotX - 4.0f * scale;
        float rightArmPivotY = bodyPivotY;
        float leftArmPivotX = bodyPivotX + 4.0f * scale;
        float leftArmPivotY = bodyPivotY;
        float rightLegPivotX = bodyPivotX - 2.0f * scale;
        float leftLegPivotX = bodyPivotX + 2.0f * scale;
        float legPivotY = bodyPivotY + 12.0f * scale;

        drawSkinPart(image, skin, 20, 20, 8, 12, bodyPivotX, bodyPivotY, 4.0f, 0.0f, scale, bodyAngle, false);
        drawSkinPart(image, skin, 4, 20, 4, 12, rightLegPivotX, legPivotY, 2.0f, 0.0f, scale, rightLegAngle, false);
        drawSkinPart(image, skin, 20, 52, 4, 12, leftLegPivotX, legPivotY, 2.0f, 0.0f, scale, leftLegAngle, false);
        drawSkinPart(image, skin, 44, 20, 4, 12, rightArmPivotX, rightArmPivotY, 2.0f, 0.0f, scale, rightArmAngle, false);
        drawSkinPart(image, skin, 36, 52, 4, 12, leftArmPivotX, leftArmPivotY, 2.0f, 0.0f, scale, leftArmAngle, false);
        drawSkinPart(image, skin, 8, 8, 8, 8, headPivotX, headPivotY, 4.0f, 8.0f, scale, headAngle, false);

        drawSkinPart(image, skin, 20, 36, 8, 12, bodyPivotX, bodyPivotY, 4.0f, 0.0f, scale, bodyAngle, true);
        drawSkinPart(image, skin, 4, 36, 4, 12, rightLegPivotX, legPivotY, 2.0f, 0.0f, scale, rightLegAngle, true);
        drawSkinPart(image, skin, 4, 52, 4, 12, leftLegPivotX, legPivotY, 2.0f, 0.0f, scale, leftLegAngle, true);
        drawSkinPart(image, skin, 44, 36, 4, 12, rightArmPivotX, rightArmPivotY, 2.0f, 0.0f, scale, rightArmAngle, true);
        drawSkinPart(image, skin, 52, 52, 4, 12, leftArmPivotX, leftArmPivotY, 2.0f, 0.0f, scale, leftArmAngle, true);
        drawSkinPart(image, skin, 40, 8, 8, 8, headPivotX, headPivotY, 4.0f, 8.0f, scale, headAngle, true);

        return image;
    }

    private static void clear(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setColorArgb(x, y, 0);
            }
        }
    }

    private static void drawSkinPart(
            NativeImage destination,
            NativeImage skin,
            int sourceX,
            int sourceY,
            int width,
            int height,
            float destinationPivotX,
            float destinationPivotY,
            float pivotX,
            float pivotY,
            int scale,
            float angle,
            boolean overlay) {
        if (sourceX + width > skin.getWidth() || sourceY + height > skin.getHeight()) {
            return;
        }

        float scaledWidth = width * scale;
        float scaledHeight = height * scale;
        float pivotScaledX = pivotX * scale;
        float pivotScaledY = pivotY * scale;
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        float[] xs = new float[] {0.0f, scaledWidth, scaledWidth, 0.0f};
        float[] ys = new float[] {0.0f, 0.0f, scaledHeight, scaledHeight};
        int minX = destination.getWidth();
        int minY = destination.getHeight();
        int maxX = 0;
        int maxY = 0;
        for (int i = 0; i < 4; i++) {
            float localX = xs[i] - pivotScaledX;
            float localY = ys[i] - pivotScaledY;
            int cornerX = (int) Math.floor(destinationPivotX + localX * cos - localY * sin);
            int cornerY = (int) Math.floor(destinationPivotY + localX * sin + localY * cos);
            minX = Math.min(minX, cornerX);
            minY = Math.min(minY, cornerY);
            maxX = Math.max(maxX, cornerX);
            maxY = Math.max(maxY, cornerY);
        }

        minX = Math.max(0, minX - scale);
        minY = Math.max(0, minY - scale);
        maxX = Math.min(destination.getWidth() - 1, maxX + scale);
        maxY = Math.min(destination.getHeight() - 1, maxY + scale);

        for (int destinationY = minY; destinationY <= maxY; destinationY++) {
            for (int destinationX = minX; destinationX <= maxX; destinationX++) {
                float dx = destinationX + 0.5f - destinationPivotX;
                float dy = destinationY + 0.5f - destinationPivotY;
                float localX = dx * cos + dy * sin + pivotScaledX;
                float localY = -dx * sin + dy * cos + pivotScaledY;
                if (localX < 0.0f || localY < 0.0f || localX >= scaledWidth || localY >= scaledHeight) {
                    continue;
                }

                int skinX = sourceX + (int) (localX / scale);
                int skinY = sourceY + (int) (localY / scale);
                int color = skin.getColorArgb(skinX, skinY);
                if (((color >>> 24) & 0xFF) == 0 || (overlay && ((color >>> 24) & 0xFF) < 8)) {
                    continue;
                }
                destination.setColorArgb(destinationX, destinationY, color);
            }
        }
    }

    private static void copyActiveSkin(MinecraftClient client, Path skinPath, boolean logSource) throws IOException {
        migrateLegacySkinFiles(client);
        Path source = activeSkinSource(client);

        Files.createDirectories(domainShaderDir(client));
        Files.createDirectories(skinPath.getParent());
        if (source.equals(legacyFallbackSkinPath(client))) {
            Files.copy(source, fallbackSkinPath(client), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            source = fallbackSkinPath(client);
        }
        if (!Files.isRegularFile(skinPath) || !hashFile(source).equals(hashFile(skinPath))) {
            Files.copy(source, skinPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (logSource) {
            LOGGER.info("Using domain popup skin source {}", source);
        }
    }

    private static Path activeSkinSource(MinecraftClient client) throws IOException {
        Path source = currentPlayerSkinPath(client);
        if (copyCurrentPlayerSkin(client, source)) {
            return source;
        }

        source = fallbackSkinPath(client);
        if (!Files.isRegularFile(source)) {
            source = legacyFallbackSkinPath(client);
        }
        if (!Files.isRegularFile(source)) {
            source = fetchedSkinPath(client);
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("No domain popup skin source found");
        }
        return source;
    }

    private static boolean copyCurrentPlayerSkin(MinecraftClient client, Path destination) {
        if (client == null || client.player == null) {
            return false;
        }

        try {
            if (client.getNetworkHandler() == null) {
                return false;
            }
            var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry == null) {
                return false;
            }
            Identifier skinTexture = entry.getSkinTextures().body().texturePath();
            Files.createDirectories(destination.getParent());

            try (InputStream stream = client.getResourceManager()
                    .getResource(skinTexture)
                    .orElseThrow()
                    .getInputStream()) {
                NativeImage skin = NativeImage.read(stream);
                try {
                    writeIfChanged(destination, skin);
                    return true;
                } finally {
                    skin.close();
                }
            } catch (Throwable ignored) {
                AbstractTexture texture = client.getTextureManager().getTexture(skinTexture);
                if (texture instanceof NativeImageBackedTexture backedTexture && backedTexture.getImage() != null) {
                    NativeImage copy = new NativeImage(backedTexture.getImage().getWidth(), backedTexture.getImage().getHeight(), false);
                    try {
                        copy.copyFrom(backedTexture.getImage());
                        writeIfChanged(destination, copy);
                        return true;
                    } finally {
                        copy.close();
                    }
                }
            }
        } catch (Throwable ignored) {
            return false;
        }

        return false;
    }

    private static void writeIfChanged(Path destination, NativeImage image) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        image.writeTo(temporary);
        if (!Files.isRegularFile(destination) || !hashFile(temporary).equals(hashFile(destination))) {
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.deleteIfExists(temporary);
    }

    private static void renderAtlas(MinecraftClient client, Path skinPath, Path atlasPath) throws IOException, InterruptedException {
        Path blender = Path.of(BLENDER_PATH);
        Path gltf = modelPath(client);
        Path script = workspacePath(client).resolve("tools").resolve("render_domain_popup_atlas.py");
        if (!Files.isRegularFile(blender) || !Files.isRegularFile(gltf) || !Files.isRegularFile(script)) {
            LOGGER.info("Cannot render domain popup atlas; missing blender={}, gltf={}, script={}", blender, gltf, script);
            return;
        }

        Files.createDirectories(atlasPath.getParent());
        Path renderLog = Files.createTempFile("domain_popup_atlas_", ".log");
        Process process = new ProcessBuilder(
                blender.toString(),
                "--background",
                "--python",
                script.toString(),
                "--",
                "--gltf",
                gltf.toString(),
                "--skin",
                skinPath.toString(),
                "--output",
                atlasPath.toString(),
                "--fps",
                "60",
                "--duration",
                "2.6",
                "--cell-size",
                "256",
                "--columns",
                "16",
                "--rows",
                "16",
                "--use-scene-camera",
                "--camera-x",
                "-2.6",
                "--camera-y",
                "2.9",
                "--camera-z",
                "1.55",
                "--target-x",
                "0.0",
                "--target-y",
                "0.0",
                "--target-z",
                "0.55",
                "--ortho-scale",
                "0.72",
                "--model-yaw",
                "180",
                "--no-flip-x"
        )
                .redirectErrorStream(true)
                .redirectOutput(renderLog.toFile())
                .start();

        boolean finished = process.waitFor(RENDER_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            LOGGER.info("Domain popup atlas render timed out for {}", atlasPath);
            Files.deleteIfExists(renderLog);
            return;
        }
        String output = Files.readString(renderLog);
        Files.deleteIfExists(renderLog);
        if (output.contains("Using imported scene camera")
                || output.contains("Using animated camera marker")
                || output.contains("Using Blockbench camera marker")
                || output.contains("Using animated camera node directly")
                || output.contains("Using raw Blockbench camera transform")
                || output.contains("WARNING: no real glTF/Blender camera was imported")) {
            LOGGER.info("Domain popup atlas render: {}", output.lines()
                    .filter(line -> line.contains("Using imported scene camera")
                            || line.contains("Using animated camera marker")
                            || line.contains("Using Blockbench camera marker")
                            || line.contains("Using animated camera node directly")
                            || line.contains("Using raw Blockbench camera transform")
                            || line.contains("WARNING: no real glTF/Blender camera was imported"))
                    .findFirst()
                    .orElse("camera status unavailable"));
        }
        if (process.exitValue() != 0) {
            LOGGER.info("Domain popup atlas render failed for {}", atlasPath);
            Files.deleteIfExists(atlasPath);
        }
    }

    private static void scheduleAtlasRegistration(MinecraftClient client, String atlasKey, Path atlasPath, Runnable action) {
        IN_FLIGHT.computeIfAbsent(atlasKey, hash -> CompletableFuture.supplyAsync(() -> {
            try {
                return loadAtlas(client, hash, atlasPath);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        })).whenComplete((atlas, throwable) -> {
            IN_FLIGHT.remove(atlasKey);
            client.execute(() -> {
                if (throwable == null && atlas != null) {
                    registerAtlas(client, atlas);
                    if (action != null) {
                        action.run();
                    }
                    return;
                }
                if (throwable != null) {
                    LOGGER.info("Could not prepare domain popup atlas {}", atlasPath, throwable);
                }
            });
        });
    }

    private static PreparedAtlas loadAtlas(MinecraftClient client, String atlasKey, Path atlasPath) throws IOException {
        copyToRunResourcePack(client, atlasKey, atlasPath);
        try (InputStream stream = Files.newInputStream(atlasPath)) {
            return new PreparedAtlas(atlasKey, NativeImage.read(stream));
        }
    }

    private static void registerAtlas(MinecraftClient client, PreparedAtlas atlas) {
        try {
            registerAtlas(client, atlas.image());
            registeredAtlasKey = atlas.key();
            registeredAtlasGeneration++;
            INVALIDATED_POST_EFFECT_GENERATIONS.clear();
        } catch (Throwable throwable) {
            atlas.image().close();
            LOGGER.info("Could not register domain popup atlas {}", atlas.key(), throwable);
        }
    }

    private static void registerAtlas(MinecraftClient client, NativeImage image) {
        NativeImage imageForResourceId = new NativeImage(image.getWidth(), image.getHeight(), false);
        imageForResourceId.copyFrom(image);
        playerSamplerTexture = registerAtlas(client, PLAYER_SAMPLER_ID, image, playerSamplerTexture);
        playerSamplerResourceTexture = registerAtlas(client, PLAYER_SAMPLER_RESOURCE, imageForResourceId, playerSamplerResourceTexture);
    }

    private static NativeImageBackedTexture registerAtlas(
            MinecraftClient client,
            Identifier id,
            NativeImage image,
            NativeImageBackedTexture existingTexture) {
        NativeImageBackedTexture texture = existingTexture;
        if (texture == null) {
            texture = new NativeImageBackedTexture(() -> id.toString(), image);
            client.getTextureManager().destroyTexture(id);
            client.getTextureManager().registerTexture(id, texture);
        } else {
            texture.setImage(image);
        }
        texture.upload();
        return texture;
    }

    private static Path fetchedSkinPath(MinecraftClient client) {
        return domainShaderDir(client).resolve(FETCHED_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path fallbackSkinPath(MinecraftClient client) {
        return domainShaderDir(client).resolve(FALLBACK_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path currentPlayerSkinPath(MinecraftClient client) {
        return domainShaderDir(client).resolve(CURRENT_PLAYER_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path legacyFallbackSkinPath(MinecraftClient client) {
        return client.runDirectory.toPath().resolve(LEGACY_FALLBACK_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path activeSkinPath(MinecraftClient client) {
        return cacheDir(client).resolve(ACTIVE_SKIN_FILE_NAME);
    }

    private static Path atlasPath(MinecraftClient client, String skinHash) {
        return cacheDir(client).resolve(skinHash + "_" + ATLAS_VERSION + "_atlas.png");
    }

    private static String runtimeSkinKey(Path skinPath) throws IOException {
        return hashFile(skinPath) + "_" + ATLAS_VERSION;
    }

    private static Path atlasMetadataPath(MinecraftClient client) {
        return cacheDir(client).resolve(ATLAS_METADATA_FILE_NAME);
    }

    private static Path cacheDir(MinecraftClient client) {
        return domainShaderDir(client).resolve("generated").toAbsolutePath().normalize();
    }

    private static Path domainShaderDir(MinecraftClient client) {
        return client.runDirectory.toPath().resolve(DOMAIN_SHADER_DIR_NAME).toAbsolutePath().normalize();
    }

    private static void migrateLegacySkinFiles(MinecraftClient client) throws IOException {
        Files.createDirectories(domainShaderDir(client));
        copyIfMissing(legacyFallbackSkinPath(client), fallbackSkinPath(client));
    }

    private static void copyIfMissing(Path source, Path destination) throws IOException {
        if (!Files.isRegularFile(source) || Files.isRegularFile(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path workspacePath(MinecraftClient client) {
        Path runDir = client.runDirectory.toPath().toAbsolutePath().normalize();
        Path parent = runDir.getParent();
        return parent == null ? runDir : parent;
    }

    private static Path modelPath(MinecraftClient client) {
        Path runModel = client.runDirectory.toPath().resolve("domain_popup_model.gltf").toAbsolutePath().normalize();
        if (Files.isRegularFile(runModel)) {
            return runModel;
        }
        return Path.of(FALLBACK_GLTF_PATH);
    }

    private static Path resourcePackAtlasPath(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("resourcepacks")
                .resolve("1.21-Resource-Pack")
                .resolve("assets")
                .resolve("minecraft")
                .resolve("textures")
                .resolve("effect")
                .resolve("domain_popup_player.png")
                .toAbsolutePath()
                .normalize();
    }

    private static void copyToRunResourcePack(MinecraftClient client, String atlasKey, Path atlasPath) throws IOException {
        Path resourcePack = client.runDirectory.toPath()
                .resolve("resourcepacks")
                .resolve("1.21-Resource-Pack");
        Path looseDestination = resourcePackAtlasPath(client);
        Files.createDirectories(looseDestination.getParent());
        Files.copy(atlasPath, looseDestination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        writeAtlasMetadata(client, atlasKey);

        Path zipPack = resourcePack.resolve("1.21-Resource-Pack.zip").toAbsolutePath().normalize();
        if (!Files.isRegularFile(zipPack)) {
            return;
        }
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + zipPack.toUri()), Map.of())) {
            Path zipDestination = zip.getPath(
                    "assets",
                    "minecraft",
                    "textures",
                    "effect",
                    "domain_popup_player.png");
            Files.createDirectories(zipDestination.getParent());
            Files.copy(atlasPath, zipDestination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeAtlasMetadata(MinecraftClient client, String atlasKey) throws IOException {
        Path metadata = atlasMetadataPath(client);
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, "version=" + ATLAS_VERSION + System.lineSeparator()
                + "skinHash=" + atlasKey + System.lineSeparator());
    }

    private static boolean restoreCachedAtlasFromResourcePack(MinecraftClient client, String atlasKey, Path cachedAtlas) throws IOException {
        Path looseAtlas = resourcePackAtlasPath(client);
        Path metadata = atlasMetadataPath(client);
        if (!Files.isRegularFile(looseAtlas) || !Files.isRegularFile(metadata)) {
            return false;
        }

        String contents = Files.readString(metadata);
        if (!contents.contains("version=" + ATLAS_VERSION) || !contents.contains("skinHash=" + atlasKey)) {
            return false;
        }

        Files.createDirectories(cachedAtlas.getParent());
        Files.copy(looseAtlas, cachedAtlas, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static String hashFile(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
    }

    private record PreparedAtlas(String key, NativeImage image) {
    }
}
