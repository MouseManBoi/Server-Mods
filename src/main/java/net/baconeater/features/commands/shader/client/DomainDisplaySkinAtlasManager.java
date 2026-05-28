package net.baconeater.features.commands.shader.client;

import net.minecraft.client.MinecraftClient;
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
    private static final String ACTIVE_SKIN_FILE_NAME = "active_skin.png";
    private static final Duration RENDER_TIMEOUT = Duration.ofMinutes(3);
    private static final ConcurrentMap<String, CompletableFuture<PreparedAtlas>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Identifier, Long> INVALIDATED_POST_EFFECT_GENERATIONS = new ConcurrentHashMap<>();
    private static final String ATLAS_VERSION = "model_v20_domain_shader_paths";
    private static String registeredAtlasKey;
    private static long registeredAtlasGeneration;
    private static long nextPreloadAttemptNanos;

    private DomainDisplaySkinAtlasManager() {
    }

    public static void preloadCurrentPlayer(MinecraftClient client) {
        if (client == null || System.nanoTime() < nextPreloadAttemptNanos) {
            return;
        }
        nextPreloadAttemptNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

        try {
            migrateLegacySkinFiles(client);
            Path skinPath = activeSkinPath(client);
            copyActiveSkin(client, skinPath);
            String skinHash = hashFile(skinPath);
            if (skinHash.equals(registeredAtlasKey) || IN_FLIGHT.containsKey(skinHash)) {
                return;
            }

            Path cachedAtlas = atlasPath(client, skinHash);
            if (Files.isRegularFile(cachedAtlas)) {
                LOGGER.info("Preloading cached domain popup atlas {}", cachedAtlas);
                scheduleAtlasRegistration(client, skinHash, cachedAtlas, null);
            }
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
        Path cachedAtlas;
        try {
            migrateLegacySkinFiles(client);
            skinPath = activeSkinPath(client);
            copyActiveSkin(client, skinPath);
            skinHash = hashFile(skinPath);
            cachedAtlas = atlasPath(client, skinHash);
            if (skinHash.equals(registeredAtlasKey)) {
                action.run();
                return;
            }
            if (Files.isRegularFile(cachedAtlas)) {
                LOGGER.info("Using cached domain popup atlas {}", cachedAtlas);
                action.run();
                scheduleAtlasRegistration(client, skinHash, cachedAtlas, action);
                return;
            }
        } catch (Throwable ignored) {
            action.run();
            return;
        }

        action.run();
        IN_FLIGHT.computeIfAbsent(skinHash, hash -> CompletableFuture.supplyAsync(() -> {
            try {
                Path atlas = atlasPath(client, hash);
                if (Files.isRegularFile(atlas)) {
                    LOGGER.info("Using cached domain popup atlas {}", atlas);
                    return loadAtlas(client, hash, atlas);
                }
                LOGGER.info("Rendering domain popup atlas {} from skin {}", atlas, skinPath);
                renderAtlas(client, skinPath, atlas);
                return loadAtlas(client, hash, atlas);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        })).whenComplete((atlas, throwable) -> {
            IN_FLIGHT.remove(skinHash);
            client.execute(() -> {
                if (throwable == null && atlas != null) {
                    registerAtlas(client, atlas);
                    action.run();
                }
            });
        });
    }

    public static void clear() {
        IN_FLIGHT.clear();
        INVALIDATED_POST_EFFECT_GENERATIONS.clear();
        registeredAtlasKey = null;
        registeredAtlasGeneration++;
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

    private static void copyActiveSkin(MinecraftClient client, Path skinPath) throws IOException {
        migrateLegacySkinFiles(client);
        Path source = fetchedSkinPath(client);
        if (!Files.isRegularFile(source)) {
            source = fallbackSkinPath(client);
        }
        if (!Files.isRegularFile(source)) {
            source = legacyFallbackSkinPath(client);
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("No domain popup skin source found");
        }

        Files.createDirectories(domainShaderDir(client));
        Files.createDirectories(skinPath.getParent());
        if (source.equals(legacyFallbackSkinPath(client))) {
            Files.copy(source, fallbackSkinPath(client), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            source = fallbackSkinPath(client);
        }
        Files.copy(source, skinPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Using domain popup skin source {}", source);
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
                "--target-x",
                "0.72",
                "--target-y",
                "-0.18",
                "--target-z",
                "0.22",
                "--ortho-scale",
                "1.08"
        )
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();

        boolean finished = process.waitFor(RENDER_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            LOGGER.info("Domain popup atlas render timed out for {}", atlasPath);
            return;
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
        copyToRunResourcePack(client, atlasPath);
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
        registerAtlas(client, PLAYER_SAMPLER_ID, image);
        registerAtlas(client, PLAYER_SAMPLER_RESOURCE, imageForResourceId);
    }

    private static void registerAtlas(MinecraftClient client, Identifier id, NativeImage image) {
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        client.getTextureManager().destroyTexture(id);
        client.getTextureManager().registerTexture(id, texture);
        texture.upload();
    }

    private static Path fetchedSkinPath(MinecraftClient client) {
        return domainShaderDir(client).resolve(FETCHED_SKIN_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path fallbackSkinPath(MinecraftClient client) {
        return domainShaderDir(client).resolve(FALLBACK_SKIN_FILE_NAME).toAbsolutePath().normalize();
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

    private static void copyToRunResourcePack(MinecraftClient client, Path atlasPath) throws IOException {
        Path resourcePack = client.runDirectory.toPath()
                .resolve("resourcepacks")
                .resolve("1.21-Resource-Pack");
        Path looseDestination = resourcePack
                .resolve("assets")
                .resolve("minecraft")
                .resolve("textures")
                .resolve("effect")
                .resolve("domain_popup_player.png")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(looseDestination.getParent());
        Files.copy(atlasPath, looseDestination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

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
