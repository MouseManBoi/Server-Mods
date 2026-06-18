package net.baconeater.features.commands.visibility;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.Collection;

public final class VisibilityCommand {
    private VisibilityCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("visibility")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("disable")
                        .then(Commands.argument("target", EntityArgument.entities())
                                .then(Commands.argument("viewer", EntityArgument.players())
                                        .executes(context -> changeVisibility(
                                                EntityArgument.getEntities(context, "target"),
                                                EntityArgument.getPlayers(context, "viewer"),
                                                true,
                                                false,
                                                true,
                                                context.getSource()))
                                        .then(perspectiveOption("ignore_perspective", true, false))
                                        .then(perspectiveOption("inline_perspective", true, true)))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("target", EntityArgument.entities())
                                .then(Commands.argument("viewer", EntityArgument.players())
                                        .executes(context -> changeVisibility(
                                                EntityArgument.getEntities(context, "target"),
                                                EntityArgument.getPlayers(context, "viewer"),
                                                false,
                                                false,
                                                true,
                                                context.getSource()))
                                        .then(perspectiveOption("ignore_perspective", false, false))
                                        .then(perspectiveOption("inline_perspective", false, true)))))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> perspectiveOption(
            String name,
            boolean hide,
            boolean renderOutsideFirstPerson) {
        return Commands.literal(name)
                .executes(context -> changeVisibility(
                        EntityArgument.getEntities(context, "target"),
                        EntityArgument.getPlayers(context, "viewer"),
                        hide,
                        renderOutsideFirstPerson,
                        true,
                        context.getSource()))
                .then(passengerOption("render_passengers", hide, renderOutsideFirstPerson, false))
                .then(passengerOption("hide_passengers", hide, renderOutsideFirstPerson, true));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> passengerOption(
            String name,
            boolean hide,
            boolean renderOutsideFirstPerson,
            boolean hidePassengers) {
        return Commands.literal(name)
                .executes(context -> changeVisibility(
                        EntityArgument.getEntities(context, "target"),
                        EntityArgument.getPlayers(context, "viewer"),
                        hide,
                        renderOutsideFirstPerson,
                        hidePassengers,
                        context.getSource()));
    }

    private static int changeVisibility(
            Collection<? extends Entity> targets,
            Collection<ServerPlayer> viewers,
            boolean hide,
            boolean renderOutsideFirstPerson,
            boolean hidePassengers,
            CommandSourceStack source) {
        int total = 0;
        for (ServerPlayer viewer : viewers) {
            for (Entity target : targets) {
                total += sendVisibilityUpdate(target, viewer, hide, renderOutsideFirstPerson, hidePassengers);
            }
        }

        int finalTotal = total;
        String perspectiveSuffix = hide && renderOutsideFirstPerson
                ? " (still rendered in 2nd/3rd person)"
                : "";
        source.sendSuccess(
                () -> Component.literal("Visibility " + (hide ? "disabled" : "enabled") +
                        " for " + finalTotal + " entit" + (finalTotal == 1 ? "y" : "ies") +
                        " across " + viewers.size() + " viewer" + (viewers.size() == 1 ? "" : "s") +
                        perspectiveSuffix),
                true);
        return total;
    }

    private static int sendVisibilityUpdate(
            Entity target,
            ServerPlayer viewer,
            boolean hide,
            boolean renderOutsideFirstPerson,
            boolean hidePassengers) {
        VisibilityTogglePayload payload = hide
                ? VisibilityTogglePayload.disable(target.getId(), renderOutsideFirstPerson)
                : VisibilityTogglePayload.enable(target.getId());
        ServerPlayNetworking.send(viewer, payload);

        int count = 1;
        if (!hidePassengers) {
            return count;
        }

        for (Entity passenger : target.getPassengers()) {
            count += sendVisibilityUpdate(passenger, viewer, hide, renderOutsideFirstPerson, true);
        }
        return count;
    }
}
