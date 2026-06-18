package net.baconeater.features.commands.attack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;

import java.util.Collection;

public final class AttackCommand {
    private AttackCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("attack")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("disable")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> setAttackEnabled(
                                        EntityArgument.getEntities(context, "targets"),
                                        false,
                                        null,
                                        context.getSource()))
                                .then(Commands.argument("disableRedTint", BoolArgumentType.bool())
                                        .executes(context -> setAttackEnabled(
                                                EntityArgument.getEntities(context, "targets"),
                                                false,
                                                BoolArgumentType.getBool(context, "disableRedTint"),
                                                context.getSource())))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> setAttackEnabled(
                                        EntityArgument.getEntities(context, "targets"),
                                        true,
                                        null,
                                        context.getSource()))
                                .then(Commands.argument("disableRedTint", BoolArgumentType.bool())
                                        .executes(context -> setAttackEnabled(
                                                EntityArgument.getEntities(context, "targets"),
                                                true,
                                                BoolArgumentType.getBool(context, "disableRedTint"),
                                                context.getSource()))))));
    }

    private static int setAttackEnabled(
            Collection<? extends Entity> targets,
            boolean enabled,
            Boolean disableRedTint,
            CommandSourceStack source) {
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

        source.sendSuccess(
                () -> {
                    String tintMessage = disableRedTint == null
                            ? ""
                            : " Red tint " + (disableRedTint ? "disabled" : "enabled") + ".";
                    return Component.literal("Attack " + (enabled ? "enabled" : "disabled")
                            + " for " + targets.size() + " entit" + (targets.size() == 1 ? "y" : "ies") + "."
                            + tintMessage);
                },
                true);
        return targets.size();
    }
}
