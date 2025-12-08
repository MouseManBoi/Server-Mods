package net.baconeater.features.commands.perspective;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.perspective.network.PerspectiveRequestPayload;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.Collection;

public final class PerspectiveCommand {
    private PerspectiveCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(createPerspectiveCommand());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createPerspectiveCommand() {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("perspective")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                        .then(CommandManager.literal("first")
                                .executes(context -> setPerspective(
                                        EntityArgumentType.getPlayers(context, "targets"),
                                        PerspectiveState.FIRST,
                                        context.getSource())))
                        .then(CommandManager.literal("second")
                                .executes(context -> setPerspective(
                                        EntityArgumentType.getPlayers(context, "targets"),
                                        PerspectiveState.SECOND,
                                        context.getSource())))
                        .then(CommandManager.literal("third")
                                .executes(context -> setPerspective(
                                        EntityArgumentType.getPlayers(context, "targets"),
                                        PerspectiveState.THIRD,
                                        context.getSource()))));
        return root;
    }

    private static int setPerspective(
            Collection<ServerPlayerEntity> targets,
            PerspectiveState state,
            ServerCommandSource source) {
        targets.forEach(player -> ServerPlayNetworking.send(player, PerspectiveRequestPayload.set(state)));
        source.sendFeedback(
                () -> Text.literal("Set perspective to " + state.commandName() + " for " + targets.size() + " player(s)."),
                true);
        return targets.size();
    }
}