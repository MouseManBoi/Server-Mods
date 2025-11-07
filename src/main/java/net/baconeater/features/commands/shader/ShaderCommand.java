package net.baconeater.features.commands.shader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;

public final class ShaderCommand {
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
                                .executes(context -> applyShaderUpdate(
                                        EntityArgumentType.getPlayers(context, "targets"),
                                        context.getSource(),
                                        IdentifierArgumentType.getIdentifier(context, "shader"),
                                        action)))));
    }

    private static int applyShaderUpdate(
            Collection<ServerPlayerEntity> players,
            ServerCommandSource source,
            Identifier shader,
            ToggleShaderPayload.ShaderAction action) {
        ToggleShaderPayload payload = switch (action) {
            case TOGGLE -> ToggleShaderPayload.toggle(shader);
            case ENABLE -> ToggleShaderPayload.enable(shader);
            case DISABLE -> ToggleShaderPayload.disable(shader);
        };
        players.forEach(player -> ServerPlayNetworking.send(player, payload));
        String message = switch (action) {
            case TOGGLE -> "Toggled";
            case ENABLE -> "Enabled";
            case DISABLE -> "Disabled";
        };
        source.sendFeedback(
                () -> Text.literal(message + " shader " + shader + " for " + players.size() + " player(s)."),
                true);
        return players.size();
    }
}