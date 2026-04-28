package net.baconeater.features.commands.attack;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Collection;

public final class AttackCommand {
    private AttackCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("attack")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                        .then(CommandManager.literal("disable")
                                .executes(context -> setAttackEnabled(
                                        EntityArgumentType.getEntities(context, "targets"),
                                        false,
                                        context.getSource())))
                        .then(CommandManager.literal("enable")
                                .executes(context -> setAttackEnabled(
                                        EntityArgumentType.getEntities(context, "targets"),
                                        true,
                                        context.getSource())))));
    }

    private static int setAttackEnabled(
            Collection<? extends Entity> targets,
            boolean enabled,
            ServerCommandSource source) {
        targets.forEach(target -> {
            if (enabled) {
                AttackState.enable(target);
            } else {
                AttackState.disable(target);
            }
        });

        source.sendFeedback(
                () -> Text.literal("Attack " + (enabled ? "enabled" : "disabled")
                        + " for " + targets.size() + " entit" + (targets.size() == 1 ? "y" : "ies") + "."),
                true);
        return targets.size();
    }
}
