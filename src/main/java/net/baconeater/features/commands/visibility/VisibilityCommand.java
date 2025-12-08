package net.baconeater.features.commands.visibility;

import com.mojang.brigadier.CommandDispatcher;
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
                                .then(CommandManager.argument("viewer", EntityArgumentType.player())
                                        .executes(context -> changeVisibility(
                                                EntityArgumentType.getEntities(context, "target"),
                                                EntityArgumentType.getPlayer(context, "viewer"),
                                                true,
                                                context.getSource())))))
                .then(CommandManager.literal("enable")
                        .then(CommandManager.argument("target", EntityArgumentType.entities())
                                .then(CommandManager.argument("viewer", EntityArgumentType.player())
                                        .executes(context -> changeVisibility(
                                                EntityArgumentType.getEntities(context, "target"),
                                                EntityArgumentType.getPlayer(context, "viewer"),
                                                false,
                                                context.getSource()))))));
    }

    private static int changeVisibility(
            Collection<? extends Entity> targets,
            ServerPlayerEntity viewer,
            boolean hide,
            ServerCommandSource source) {
        int total = 0;
        for (Entity target : targets) {
            total += sendVisibilityUpdate(target, viewer, hide);
        }

        int finalTotal = total;
        source.sendFeedback(
                () -> Text.literal("Visibility " + (hide ? "disabled" : "enabled") +
                        " for " + finalTotal + " entit" + (finalTotal == 1 ? "y" : "ies") +
                        " to " + viewer.getName().getString()),
                true);
        return total;
    }

    private static int sendVisibilityUpdate(Entity target, ServerPlayerEntity viewer, boolean hide) {
        VisibilityTogglePayload payload = hide
                ? VisibilityTogglePayload.disable(target.getId())
                : VisibilityTogglePayload.enable(target.getId());
        ServerPlayNetworking.send(viewer, payload);

        int count = 1;
        for (Entity passenger : target.getPassengerList()) {
            count += sendVisibilityUpdate(passenger, viewer, hide);
        }
        return count;
    }
}