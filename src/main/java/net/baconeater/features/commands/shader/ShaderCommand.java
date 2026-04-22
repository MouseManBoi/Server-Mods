package net.baconeater.features.commands.shader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
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

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShaderCommand {
    private static final ResourceFinder POST_EFFECT_FINDER = ResourceFinder.json("post_effect");
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
                                        ShaderState.NONE))
                                .then(CommandManager.argument("state", StringArgumentType.word())
                                        .suggests(ShaderCommand::suggestShaderStates)
                                        .executes(context -> applyShaderUpdate(
                                                EntityArgumentType.getPlayers(context, "targets"),
                                                context.getSource(),
                                                IdentifierArgumentType.getIdentifier(context, "shader"),
                                                action,
                                                ShaderState.fromCommandName(StringArgumentType.getString(context, "state"))))))));
    }

    private static CompletableFuture<Suggestions> suggestShaders(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder builder) {
        Set<String> suggestions = new LinkedHashSet<>();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Identifier shaderId : getAvailableShaderIds()) {
            String fullId = shaderId.toString();
            if (CommandSource.shouldSuggest(remaining, fullId.toLowerCase(Locale.ROOT))) {
                suggestions.add(fullId);
            }
        }

        return CommandSource.suggestMatching(suggestions, builder);
    }

    private static List<Identifier> getAvailableShaderIds() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return List.of();
        }

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

            return POST_EFFECT_FINDER.findResources(manager)
                    .keySet()
                    .stream()
                    .map(POST_EFFECT_FINDER::toResourceId)
                    .distinct()
                    .sorted(Comparator.comparing(Identifier::toString))
                    .toList();
        } catch (ReflectiveOperationException ignored) {
            return List.of();
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
            ShaderState state) {
        ToggleShaderPayload payload = switch (action) {
            case TOGGLE -> ToggleShaderPayload.toggle(shader, state);
            case ENABLE -> ToggleShaderPayload.enable(shader, state);
            case DISABLE -> ToggleShaderPayload.disable(shader, state);
        };
        players.forEach(player -> ServerPlayNetworking.send(player, payload));
        String message = switch (action) {
            case TOGGLE -> "Toggled";
            case ENABLE -> "Enabled";
            case DISABLE -> "Disabled";
        };
        source.sendFeedback(
                () -> Text.literal(message
                        + " shader "
                        + shader
                        + (state.isSpecified() ? " (" + state.commandName() + ")" : "")
                        + " for "
                        + players.size()
                        + " player(s)."),
                true);
        return players.size();
    }
}
