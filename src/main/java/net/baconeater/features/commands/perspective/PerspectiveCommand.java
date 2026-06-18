package net.baconeater.features.commands.perspective;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.perspective.network.PerspectiveRequestPayload;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.Collection;

public final class PerspectiveCommand {
    private PerspectiveCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(createPerspectiveCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createPerspectiveCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("perspective")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("first")
                                .executes(context -> setPerspective(
                                        EntityArgument.getPlayers(context, "targets"),
                                        PerspectiveState.FIRST,
                                        context.getSource())))
                        .then(Commands.literal("second")
                                .executes(context -> setPerspective(
                                        EntityArgument.getPlayers(context, "targets"),
                                        PerspectiveState.SECOND,
                                        context.getSource())))
                        .then(Commands.literal("third")
                                .executes(context -> setPerspective(
                                        EntityArgument.getPlayers(context, "targets"),
                                        PerspectiveState.THIRD,
                                        context.getSource()))));
        return root;
    }

    private static int setPerspective(
            Collection<ServerPlayer> targets,
            PerspectiveState state,
            CommandSourceStack source) {
        targets.forEach(player -> ServerPlayNetworking.send(player, PerspectiveRequestPayload.set(state)));
        source.sendSuccess(
                () -> Component.literal("Set perspective to " + state.commandName() + " for " + targets.size() + " player(s)."),
                true);
        return targets.size();
    }
}
