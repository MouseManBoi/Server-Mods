package net.baconeater.features.commands.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;

public final class SkinCommand {
    private static final String DOMAIN_SHADER_DIR_NAME = "domain_shader";
    private static final String SKIN_FILE_NAME = "fetched_skin.png";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private SkinCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("skin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("fetch")
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .executes(context -> fetchSkins(
                                        EntityArgumentType.getEntities(context, "targets"),
                                        context.getSource())))));
    }

    private static int fetchSkins(Collection<? extends Entity> targets, ServerCommandSource source) {
        Path skinPath = source.getServer()
                .getRunDirectory()
                .resolve(DOMAIN_SHADER_DIR_NAME)
                .resolve(SKIN_FILE_NAME)
                .toAbsolutePath()
                .normalize();

        int saved = 0;
        int skipped = 0;
        for (Entity target : targets) {
            if (!(target instanceof ServerPlayerEntity player)) {
                skipped++;
                continue;
            }

            String skinUrl = getSkinUrl(player);
            if (skinUrl == null) {
                skipped++;
                continue;
            }

            try {
                downloadSkin(skinUrl, skinPath);
                saved++;
            } catch (IOException ignored) {
                skipped++;
            }
        }

        int finalSaved = saved;
        int finalSkipped = skipped;
        if (saved == 0) {
            try {
                Files.deleteIfExists(skinPath);
            } catch (IOException ignored) {
                // Leaving a stale fetched skin is worse than failing quietly, but command feedback covers the miss.
            }
        }
        source.sendFeedback(
                () -> Text.literal("Fetched " + finalSaved + " skin(s) to " + skinPath
                        + (finalSkipped > 0 ? "; skipped " + finalSkipped + " target(s)" : "")
                        + (finalSaved == 0 ? "; cleared fetched skin so the shader uses fallback." : ".")),
                true);
        return saved;
    }

    private static String getSkinUrl(ServerPlayerEntity player) {
        String profileSkinUrl = player.getGameProfile()
                .properties()
                .get("textures")
                .stream()
                .map(property -> extractSkinUrl(property.value()))
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
        if (profileSkinUrl != null) {
            return profileSkinUrl;
        }

        return getUsernameSkinUrl(player.getGameProfile().name());
    }

    private static String getUsernameSkinUrl(String username) {
        if (username == null || username.isBlank() || username.matches("Player\\d+")) {
            return null;
        }

        try {
            String profileResponse = readUrl("https://api.mojang.com/users/profiles/minecraft/" + username);
            JsonObject profile = JsonParser.parseString(profileResponse).getAsJsonObject();
            if (!profile.has("id")) {
                return null;
            }

            String sessionResponse = readUrl("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + profile.get("id").getAsString());
            JsonObject sessionProfile = JsonParser.parseString(sessionResponse).getAsJsonObject();
            if (!sessionProfile.has("properties")) {
                return null;
            }

            return sessionProfile.getAsJsonArray("properties")
                    .asList()
                    .stream()
                    .map(property -> property.getAsJsonObject())
                    .filter(property -> property.has("name") && "textures".equals(property.get("name").getAsString()))
                    .filter(property -> property.has("value"))
                    .map(property -> extractSkinUrl(property.get("value").getAsString()))
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }

    private static String readUrl(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        try (InputStream stream = connection.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String extractSkinUrl(String encodedTextures) {
        if (encodedTextures == null || encodedTextures.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(encodedTextures), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject()
                    .getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) {
                return null;
            }

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null || !skin.has("url")) {
                return null;
            }
            return skin.get("url").getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void downloadSkin(String skinUrl, Path outputPath) throws IOException {
        URI uri = URI.create(skinUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Unsupported skin URL scheme");
        }

        Files.createDirectories(outputPath.getParent());
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        if (connection instanceof HttpURLConnection httpConnection) {
            httpConnection.setInstanceFollowRedirects(true);
        }

        try (InputStream stream = connection.getInputStream()) {
            Files.copy(stream, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
