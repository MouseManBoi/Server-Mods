package net.baconeater.features.commands.shader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShaderCommand {
    private static final ResourceFinder POST_EFFECT_FINDER = ResourceFinder.json("post_effect");
    private static final ResourceFinder LEGACY_POST_EFFECT_FINDER = ResourceFinder.json("shaders/post");
    private static final String[] SHADER_STATES = {"in", "out"};

    private ShaderCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(createShaderCommand());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createShaderCommand() {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("shader")
                .requires(source -> source.hasPermissionLevel(2));

        registerShaderAction(root, "toggle", ToggleShaderPayload.ShaderAction.TOGGLE);
        registerShaderAction(root, "enable", ToggleShaderPayload.ShaderAction.ENABLE);
        registerShaderAction(root, "disable", ToggleShaderPayload.ShaderAction.DISABLE);

        return root;
    }

    private static void registerShaderAction(
            LiteralArgumentBuilder<ServerCommandSource> root,
            String name,
            ToggleShaderPayload.ShaderAction action) {
        root.then(CommandManager.literal(name)
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                        .then(CommandManager.argument("shader", IdentifierArgumentType.identifier())
                                .suggests(ShaderCommand::suggestShaders)
                                .executes(context -> applyShaderUpdate(
                                        EntityArgumentType.getPlayers(context, "targets"),
                                        context.getSource(),
                                        IdentifierArgumentType.getIdentifier(context, "shader"),
                                        action,
                                        ShaderState.NONE,
                                        false))
                                .then(CommandManager.argument("renderOnTop", BoolArgumentType.bool())
                                        .executes(context -> applyShaderUpdate(
                                                EntityArgumentType.getPlayers(context, "targets"),
                                                context.getSource(),
                                                IdentifierArgumentType.getIdentifier(context, "shader"),
                                                action,
                                                ShaderState.NONE,
                                                BoolArgumentType.getBool(context, "renderOnTop")))
                                        .then(CommandManager.argument("state", StringArgumentType.word())
                                                .suggests(ShaderCommand::suggestShaderStates)
                                                .executes(context -> applyShaderUpdate(
                                                        EntityArgumentType.getPlayers(context, "targets"),
                                                        context.getSource(),
                                                        IdentifierArgumentType.getIdentifier(context, "shader"),
                                                        action,
                                                        ShaderState.fromCommandName(StringArgumentType.getString(context, "state")),
                                                        BoolArgumentType.getBool(context, "renderOnTop"))))))));
    }

    private static CompletableFuture<Suggestions> suggestShaders(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder builder) {
        Set<String> suggestions = new LinkedHashSet<>();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Identifier shaderId : getAvailableShaderIds(context.getSource())) {
            String fullId = shaderId.toString();
            if (CommandSource.shouldSuggest(remaining, fullId.toLowerCase(Locale.ROOT))) {
                suggestions.add(fullId);
            }
        }

        return CommandSource.suggestMatching(suggestions, builder);
    }

    private static List<Identifier> getAvailableShaderIds(ServerCommandSource source) {
        Set<Identifier> shaderIds = new LinkedHashSet<>();
        ResourceManager resourceManager = source.getServer().getResourceManager();
        if (resourceManager != null) {
            shaderIds.addAll(getAvailableShaderIds(resourceManager));
        }

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            shaderIds.addAll(getClientShaderIds());
        }
        shaderIds.addAll(getPackagedShaderIds());

        return shaderIds.stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    private static List<Identifier> getClientShaderIds() {
        try {
            Class<?> minecraftClientClass = Class.forName("net.minecraft.client.MinecraftClient");
            Object client = minecraftClientClass.getMethod("getInstance").invoke(null);
            if (client == null) {
                return List.of();
            }

            Object resourceManager = minecraftClientClass.getMethod("getResourceManager").invoke(client);
            if (!(resourceManager instanceof ResourceManager manager)) {
                return List.of();
            }

            return getAvailableShaderIds(manager);
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    private static List<Identifier> getAvailableShaderIds(ResourceManager resourceManager) {
        Set<Identifier> shaderIds = new LinkedHashSet<>();
        shaderIds.addAll(POST_EFFECT_FINDER.findResources(resourceManager)
                .keySet()
                .stream()
                .map(POST_EFFECT_FINDER::toResourceId)
                .distinct()
                .toList());
        shaderIds.addAll(LEGACY_POST_EFFECT_FINDER.findResources(resourceManager).keySet());
        return shaderIds.stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    private static List<Identifier> getPackagedShaderIds() {
        List<Identifier> shaderIds = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path rootPath : mod.getRootPaths()) {
                shaderIds.addAll(getPackagedShaderIds(rootPath));
            }
        }
        return shaderIds;
    }

    private static List<Identifier> getPackagedShaderIds(Path rootPath) {
        if (rootPath == null || !Files.isDirectory(rootPath)) {
            return List.of();
        }

        try (var paths = Files.walk(rootPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(rootPath::relativize)
                    .map(ShaderCommand::toPackagedPostEffectShaderId)
                    .filter(shaderId -> shaderId != null)
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static Identifier toPackagedPostEffectShaderId(Path relativePath) {
        if (relativePath.getNameCount() < 4
                || !"assets".equals(relativePath.getName(0).toString())) {
            return null;
        }

        String fileName = relativePath.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return null;
        }

        String namespace = relativePath.getName(1).toString();
        Path shaderPath;
        boolean legacyPath = relativePath.getNameCount() >= 5
                && "shaders".equals(relativePath.getName(2).toString())
                && "post".equals(relativePath.getName(3).toString());
        if ("post_effect".equals(relativePath.getName(2).toString())) {
            shaderPath = relativePath.subpath(3, relativePath.getNameCount());
        } else if (legacyPath) {
            shaderPath = relativePath.subpath(2, relativePath.getNameCount());
        } else {
            return null;
        }

        String path = shaderPath.toString().replace('\\', '/');
        if (!legacyPath) {
            path = path.substring(0, path.length() - ".json".length());
        }
        try {
            return Identifier.of(namespace, path);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static CompletableFuture<Suggestions> suggestShaderStates(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(SHADER_STATES, builder);
    }

    private static int applyShaderUpdate(
            Collection<ServerPlayerEntity> players,
            ServerCommandSource source,
            Identifier shader,
            ToggleShaderPayload.ShaderAction action,
            ShaderState state,
            boolean renderOnTop) {
        ToggleShaderPayload payload = switch (action) {
            case TOGGLE -> ToggleShaderPayload.toggle(shader, state, renderOnTop);
            case ENABLE -> ToggleShaderPayload.enable(shader, state, renderOnTop);
            case DISABLE -> ToggleShaderPayload.disable(shader, state, renderOnTop);
        };
        int sentCount = 0;
        for (ServerPlayerEntity player : players) {
            if (ServerPlayNetworking.canSend(player, ToggleShaderPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
                sentCount++;
            }
        }

        int skippedCount = players.size() - sentCount;
        String message = switch (action) {
            case TOGGLE -> "Toggled";
            case ENABLE -> "Enabled";
            case DISABLE -> "Disabled";
        };
        int finalSentCount = sentCount;
        int finalSkippedCount = skippedCount;
        source.sendFeedback(
                () -> Text.literal(message
                        + " shader "
                        + shader
                        + (state.isSpecified() ? " (" + state.commandName() + ")" : "")
                        + (renderOnTop ? " on top" : "")
                        + " for "
                        + finalSentCount
                        + " player(s)"
                        + (finalSkippedCount > 0
                        ? "; skipped " + finalSkippedCount + " unsupported player(s)."
                        : ".")),
                true);
        return sentCount;
    }
}
