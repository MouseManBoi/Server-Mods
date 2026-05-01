package net.baconeater.features.commands.attack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
                .then(CommandManager.literal("disable")
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .executes(context -> setAttackEnabled(
                                        EntityArgumentType.getEntities(context, "targets"),
                                        false,
                                        null,
                                        context.getSource()))
                                .then(CommandManager.argument("disableRedTint", BoolArgumentType.bool())
                                        .executes(context -> setAttackEnabled(
                                                EntityArgumentType.getEntities(context, "targets"),
                                                false,
                                                BoolArgumentType.getBool(context, "disableRedTint"),
                                                context.getSource())))))
                .then(CommandManager.literal("enable")
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .executes(context -> setAttackEnabled(
                                        EntityArgumentType.getEntities(context, "targets"),
                                        true,
                                        null,
                                        context.getSource()))
                                .then(CommandManager.argument("disableRedTint", BoolArgumentType.bool())
                                        .executes(context -> setAttackEnabled(
                                                EntityArgumentType.getEntities(context, "targets"),
                                                true,
                                                BoolArgumentType.getBool(context, "disableRedTint"),
                                                context.getSource()))))));
    }

    private static int setAttackEnabled(
            Collection<? extends Entity> targets,
            boolean enabled,
            Boolean disableRedTint,
            ServerCommandSource source) {
        targets.forEach(target -> {
            if (enabled) {
                AttackState.enable(target);
            } else {
                AttackState.disable(target);
            }
            if (disableRedTint != null) {
                AttackState.setHurtTintDisabled(target, disableRedTint);
            }
        });

        source.sendFeedback(
                () -> {
                    String tintMessage = disableRedTint == null
                            ? ""
                            : " Red tint " + (disableRedTint ? "disabled" : "enabled") + ".";
                    return Text.literal("Attack " + (enabled ? "enabled" : "disabled")
                            + " for " + targets.size() + " entit" + (targets.size() == 1 ? "y" : "ies") + "."
                            + tintMessage);
                },
                true);
        return targets.size();
    }
}
