package net.baconeater.features.commands.visibility;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.Collection;

public final class VisibilityCommand {
    private VisibilityCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("visibility")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("disable")
                        .then(CommandManager.argument("target", EntityArgumentType.entities())
                                .then(CommandManager.argument("viewer", EntityArgumentType.players())
                                        .executes(context -> changeVisibility(
                                                EntityArgumentType.getEntities(context, "target"),
                                                EntityArgumentType.getPlayers(context, "viewer"),
                                                true,
                                                false,
                                                true,
                                                context.getSource()))
                                        .then(perspectiveOption("ignore_perspective", true, false))
                                        .then(perspectiveOption("inline_perspective", true, true)))))
                .then(CommandManager.literal("enable")
                        .then(CommandManager.argument("target", EntityArgumentType.entities())
                                .then(CommandManager.argument("viewer", EntityArgumentType.players())
                                        .executes(context -> changeVisibility(
                                                EntityArgumentType.getEntities(context, "target"),
                                                EntityArgumentType.getPlayers(context, "viewer"),
                                                false,
                                                false,
                                                true,
                                                context.getSource()))
                                        .then(perspectiveOption("ignore_perspective", false, false))
                                        .then(perspectiveOption("inline_perspective", false, true)))))
                );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> perspectiveOption(
            String name,
            boolean hide,
            boolean renderOutsideFirstPerson) {
        return CommandManager.literal(name)
                .executes(context -> changeVisibility(
                        EntityArgumentType.getEntities(context, "target"),
                        EntityArgumentType.getPlayers(context, "viewer"),
                        hide,
                        renderOutsideFirstPerson,
                        true,
                        context.getSource()))
                .then(passengerOption("render_passengers", hide, renderOutsideFirstPerson, false))
                .then(passengerOption("hide_passengers", hide, renderOutsideFirstPerson, true));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> passengerOption(
            String name,
            boolean hide,
            boolean renderOutsideFirstPerson,
            boolean hidePassengers) {
        return CommandManager.literal(name)
                .executes(context -> changeVisibility(
                        EntityArgumentType.getEntities(context, "target"),
                        EntityArgumentType.getPlayers(context, "viewer"),
                        hide,
                        renderOutsideFirstPerson,
                        hidePassengers,
                        context.getSource()));
    }

    private static int changeVisibility(
            Collection<? extends Entity> targets,
            Collection<ServerPlayerEntity> viewers,
            boolean hide,
            boolean renderOutsideFirstPerson,
            boolean hidePassengers,
            ServerCommandSource source) {
        int total = 0;
        for (ServerPlayerEntity viewer : viewers) {
            for (Entity target : targets) {
                total += sendVisibilityUpdate(target, viewer, hide, renderOutsideFirstPerson, hidePassengers);
            }
        }

        int finalTotal = total;
        String perspectiveSuffix = hide && renderOutsideFirstPerson
                ? " (still rendered in 2nd/3rd person)"
                : "";
        source.sendFeedback(
                () -> Text.literal("Visibility " + (hide ? "disabled" : "enabled") +
                        " for " + finalTotal + " entit" + (finalTotal == 1 ? "y" : "ies") +
                        " across " + viewers.size() + " viewer" + (viewers.size() == 1 ? "" : "s") +
                        perspectiveSuffix),
                true);
        return total;
    }

    private static int sendVisibilityUpdate(
            Entity target,
            ServerPlayerEntity viewer,
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

        for (Entity passenger : target.getPassengerList()) {
            count += sendVisibilityUpdate(passenger, viewer, hide, renderOutsideFirstPerson, true);
        }
        return count;
    }
}
