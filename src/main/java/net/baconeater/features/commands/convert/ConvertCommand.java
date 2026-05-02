package net.baconeater.features.commands.convert;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.RegistryKeyArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Collection;

public final class ConvertCommand {
    private static final DynamicCommandExceptionType INVALID_DAMAGE_TYPE_EXCEPTION = new DynamicCommandExceptionType(
            id -> Text.literal("Unknown damage type: " + id));

    private ConvertCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("convert")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("damageType", RegistryKeyArgumentType.registryKey(RegistryKeys.DAMAGE_TYPE))
                        .then(CommandManager.argument("targets", EntityArgumentType.entities())
                                .executes(context -> convertDamageType(
                                        RegistryKeyArgumentType.getKey(
                                                context,
                                                "damageType",
                                                RegistryKeys.DAMAGE_TYPE,
                                                INVALID_DAMAGE_TYPE_EXCEPTION),
                                        EntityArgumentType.getEntities(context, "targets"),
                                        context.getSource())))));
    }

    private static int convertDamageType(
            RegistryKey<DamageType> damageType,
            Collection<? extends Entity> targets,
            ServerCommandSource source) {
        targets.forEach(target -> ConvertState.setDamageType(target, damageType));
        source.sendFeedback(
                () -> Text.literal("Converted outgoing damage to " + damageType.getValue()
                        + " for " + targets.size() + " entit" + (targets.size() == 1 ? "y" : "ies") + "."),
                true);
        return targets.size();
    }
}
