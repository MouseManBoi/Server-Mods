package net.baconeater.features.commands.shader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class DomainDisplaySkinAtlasManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("DomainDisplaySkinAtlas");
    private static final Identifier DOMAIN_DISPLAY = Identifier.fromNamespaceAndPath("minecraft", "domain_display");
    private static final Identifier DOMAIN_POPUP = Identifier.fromNamespaceAndPath("minecraft", "domain_popup");
    private static final Identifier PLAYER_SAMPLER_ID = Identifier.fromNamespaceAndPath("minecraft", "domain_popup/domain_popup_player");
    private static final Identifier PLAYER_SAMPLER_RESOURCE = Identifier.fromNamespaceAndPath("minecraft", "textures/effect/domain_popup/domain_popup_player.png");
    private static final Identifier MODEL_RESOURCE = Identifier.fromNamespaceAndPath("minecraft", "models/effect/domain_popup_model.gltf");
    private static final String DOMAIN_SHADER_DIR_NAME = "domain_shader";
    private static final String FETCHED_SKIN_FILE_NAME = "fetched_skin.png";
    private static final String FALLBACK_SKIN_FILE_NAME = "fallback_skin.png";
    private static final String LEGACY_FALLBACK_SKIN_FILE_NAME = "domain_popup_fallback_skin.png";
    private static final String CURRENT_PLAYER_SKIN_FILE_NAME = "current_player_skin.png";
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
    private static DynamicTexture playerSamplerTexture;
    private static DynamicTexture playerSamplerResourceTexture;
    private static DomainDisplayGltfRenderer gltfRenderer;
    private static long gltfRendererModifiedMillis = -1;

    private DomainDisplaySkinAtlasManager() {
    }

    public static void preloadCurrentPlayer(Minecraft client) {
        if (client == null || System.nanoTime() < nextPreloadAttemptNanos) {
            return;
        }
        nextPreloadAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        try {
            migrateLegacySkinFiles(client);
            Path source = activeSkinSource(client);
            String skinHash = runtimeSkinKey(source);
            if (skinHash.equals(registeredAtlasKey) || skinHash.equals(lastPreloadAttemptKey)) {
                return;
            }
            lastPreloadAttemptKey = skinHash;
            registerRuntimeSkinTexture(client, skinHash, source);
        } catch (Throwable ignored) {
            // The command path still handles missing skins/fallbacks; preload is only an optimization.
        }
    }

    public static void prepareThenRun(
            Minecraft client,
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


    public static void renderRuntimeAnimation(Minecraft client) {
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

    private static void registerRuntimeSkinTexture(Minecraft client, String skinKey, Path skinPath) throws IOException {
        try (InputStream stream = Files.newInputStream(skinPath)) {
            NativeImage skin = NativeImage.read(stream);
            NativeImage renderSkin = copyRenderableSkin(skin);
            try {
                if (!skinKey.equals(runtimeSkinKey)) {
                    if (runtimeSkinImage != null) {
                        runtimeSkinImage.close();
                    }
                    runtimeSkinImage = new NativeImage(renderSkin.getWidth(), renderSkin.getHeight(), false);
                    runtimeSkinImage.copyFrom(renderSkin);
                    runtimeSkinKey = skinKey;
                    animationStartNanos = System.nanoTime();
                    lastAnimationFrame = -1;
                }

                NativeImage player = renderRuntimePlayerTexture(client, renderSkin, 0.0f);
                registerAtlas(client, new PreparedAtlas(skinKey, player));
            } finally {
                renderSkin.close();
                skin.close();
            }
        }
    }

    private static NativeImage copyRenderableSkin(NativeImage skin) {
        NativeImage renderSkin = new NativeImage(skin.getWidth(), skin.getHeight(), false);
        renderSkin.copyFrom(skin);
        normalizeSlimArmRegions(renderSkin);
        return renderSkin;
    }

    private static void normalizeSlimArmRegions(NativeImage skin) {
        if (skin.getWidth() < 64 || skin.getHeight() < 64 || !looksLikeSlimSkin(skin)) {
            return;
        }

        NativeImage source = new NativeImage(skin.getWidth(), skin.getHeight(), false);
        try {
            source.copyFrom(skin);
            expandSlimArmRegion(source, skin, 40, 16);
            expandSlimArmRegion(source, skin, 40, 32);
            expandSlimArmRegion(source, skin, 32, 48);
            expandSlimArmRegion(source, skin, 48, 48);
        } finally {
            source.close();
        }
    }

    private static boolean looksLikeSlimSkin(NativeImage skin) {
        return transparentCoverage(skin, 54, 20, 2, 12) > 0.85f
                || transparentCoverage(skin, 46, 52, 2, 12) > 0.85f;
    }

    private static float transparentCoverage(NativeImage skin, int x, int y, int width, int height) {
        int transparent = 0;
        int total = 0;
        for (int yy = y; yy < y + height && yy < skin.getHeight(); yy++) {
            for (int xx = x; xx < x + width && xx < skin.getWidth(); xx++) {
                total++;
                int alpha = (skin.getPixel(xx, yy) >>> 24) & 0xFF;
                if (alpha < 8) {
                    transparent++;
                }
            }
        }
        return total == 0 ? 0.0f : (float) transparent / (float) total;
    }

    private static void expandSlimArmRegion(NativeImage source, NativeImage destination, int u, int v) {
        if (u + 15 >= source.getWidth() || v + 15 >= source.getHeight()) {
            return;
        }

        copyRect(source, destination, u, v + 4, u, v + 4, 4, 12);
        copyScaledRect(source, destination, u + 4, v, u + 4, v, 3, 4, 4, 4);
        copyScaledRect(source, destination, u + 7, v, u + 8, v, 3, 4, 4, 4);
        copyScaledRect(source, destination, u + 4, v + 4, u + 4, v + 4, 3, 12, 4, 12);
        copyRect(source, destination, u + 7, v + 4, u + 8, v + 4, 4, 12);
        copyScaledRect(source, destination, u + 11, v + 4, u + 12, v + 4, 3, 12, 4, 12);
    }

    private static void copyRect(
            NativeImage source,
            NativeImage destination,
            int sourceX,
            int sourceY,
            int destinationX,
            int destinationY,
            int width,
            int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                destination.setPixel(destinationX + x, destinationY + y, source.getPixel(sourceX + x, sourceY + y));
            }
        }
    }

    private static void copyScaledRect(
            NativeImage source,
            NativeImage destination,
            int sourceX,
            int sourceY,
            int destinationX,
            int destinationY,
            int sourceWidth,
            int sourceHeight,
            int destinationWidth,
            int destinationHeight) {
        for (int y = 0; y < destinationHeight; y++) {
            int sy = sourceY + Math.min(sourceHeight - 1, y * sourceHeight / destinationHeight);
            for (int x = 0; x < destinationWidth; x++) {
                int sx = sourceX + Math.min(sourceWidth - 1, x * sourceWidth / destinationWidth);
                destination.setPixel(destinationX + x, destinationY + y, source.getPixel(sx, sy));
            }
        }
    }

    private static NativeImage renderRuntimePlayerTexture(Minecraft client, NativeImage skin, float time) {
        DomainDisplayGltfRenderer renderer = getGltfRenderer(client);
        if (renderer != null) {
            return renderer.render(skin, RUNTIME_TEXTURE_SIZE, time);
        }
        return renderFrontFacingPlayer(skin, time);
    }

    private static DomainDisplayGltfRenderer getGltfRenderer(Minecraft client) {
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
                image.setPixel(x, y, 0);
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
                int color = skin.getPixel(skinX, skinY);
                if (((color >>> 24) & 0xFF) == 0 || (overlay && ((color >>> 24) & 0xFF) < 8)) {
                    continue;
                }
                destination.setPixel(destinationX, destinationY, color);
            }
        }
    }

    private static Path activeSkinSource(Minecraft client) throws IOException {
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

    private static boolean copyCurrentPlayerSkin(Minecraft client, Path destination) {
        if (client == null || client.player == null) {
            return false;
        }

        try {
            if (client.getConnection() == null) {
                return false;
            }
            var entry = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (entry == null) {
                return false;
            }
            Identifier skinTexture = entry.getSkin().body().texturePath();
            Files.createDirectories(destination.getParent());

            try (InputStream stream = client.getResourceManager()
                    .getResource(skinTexture)
                    .orElseThrow()
                    .open()) {
                NativeImage skin = NativeImage.read(stream);
                try {
                    writeIfChanged(destination, skin);
                    return true;
                } finally {
                    skin.close();
                }
            } catch (Throwable ignored) {
                AbstractTexture texture = client.getTextureManager().getTexture(skinTexture);
                if (texture instanceof DynamicTexture backedTexture && backedTexture.getPixels() != null) {
                    NativeImage copy = new NativeImage(backedTexture.getPixels().getWidth(), backedTexture.getPixels().getHeight(), false);
                    try {
                        copy.copyFrom(backedTexture.getPixels());
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
        image.writeToFile(temporary);
        if (!Files.isRegularFile(destination) || !hashFile(temporary).equals(hashFile(destination))) {
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.deleteIfExists(temporary);
    }

    private static void registerAtlas(Minecraft client, PreparedAtlas atlas) {
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

    private static void registerAtlas(Minecraft client, NativeImage image) {
        NativeImage imageForResourceId = new NativeImage(image.getWidth(), image.getHeight(), false);
        imageForResourceId.copyFrom(image);
        playerSamplerTexture = registerAtlas(client, PLAYER_SAMPLER_ID, image, playerSamplerTexture);
        playerSamplerResourceTexture = registerAtlas(client, PLAYER_SAMPLER_RESOURCE, imageForResourceId, playerSamplerResourceTexture);
    }

    private static DynamicTexture registerAtlas(
            Minecraft client,
            Identifier id,
            NativeImage image,
            DynamicTexture existingTexture) {
        DynamicTexture texture = existingTexture;
        if (texture == null) {
            texture = new DynamicTexture(() -> id.toString(), image);
            client.getTextureManager().release(id);
            client.getTextureManager().register(id, texture);
        } else {
            texture.setPixels(image);
        }
        texture.upload();
        return texture;
    }

    private static Path fetchedSkinPath(Minecraft client) {
        return domainShaderDir(client).resolve(FETCHED_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path fallbackSkinPath(Minecraft client) {
        return domainShaderDir(client).resolve(FALLBACK_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path currentPlayerSkinPath(Minecraft client) {
        return domainShaderDir(client).resolve(CURRENT_PLAYER_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path legacyFallbackSkinPath(Minecraft client) {
        return client.gameDirectory.toPath().resolve(LEGACY_FALLBACK_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static String runtimeSkinKey(Path skinPath) throws IOException {
        return hashFile(skinPath) + "_" + ATLAS_VERSION;
    }

    private static Path cacheDir(Minecraft client) {
        return domainShaderDir(client).resolve("generated").toAbsolutePath().normalize();
    }

    private static Path domainShaderDir(Minecraft client) {
        return client.gameDirectory.toPath().resolve(DOMAIN_SHADER_DIR_NAME).toAbsolutePath().normalize();
    }

    private static void migrateLegacySkinFiles(Minecraft client) throws IOException {
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

    private static Path modelPath(Minecraft client) throws IOException {
        Path resourceModel = cachedResourceModelPath(client);
        if (copyResourceModel(client, resourceModel)) {
            return resourceModel;
        }

        throw new IOException("No domain popup model resource found at " + MODEL_RESOURCE);
    }

    private static boolean copyResourceModel(Minecraft client, Path destination) {
        if (client == null || client.getResourceManager() == null) {
            return false;
        }

        try (InputStream stream = client.getResourceManager()
                .getResource(MODEL_RESOURCE)
                .orElseThrow()
                .open()) {
            writeIfChanged(destination, stream.readAllBytes());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writeIfChanged(Path destination, byte[] contents) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.write(temporary, contents);
        if (!Files.isRegularFile(destination) || !hashFile(temporary).equals(hashFile(destination))) {
            Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        Files.deleteIfExists(temporary);
    }

    private static Path cachedResourceModelPath(Minecraft client) {
        return cacheDir(client).resolve("domain_popup_model.gltf").toAbsolutePath().normalize();
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
