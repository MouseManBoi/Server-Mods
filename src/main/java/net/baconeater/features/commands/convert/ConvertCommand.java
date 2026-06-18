package net.baconeater.features.commands.convert;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;

import java.util.Collection;

public final class ConvertCommand {
    private static final DynamicCommandExceptionType INVALID_DAMAGE_TYPE_EXCEPTION = new DynamicCommandExceptionType(
            id -> Component.literal("Unknown damage type: " + id));

    private ConvertCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("convert")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("DamageType", ResourceKeyArgument.key(Registries.DAMAGE_TYPE))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> convertDamageType(
                                        ResourceKeyArgument.getRegistryKey(
                                                context,
                                                "DamageType",
                                                Registries.DAMAGE_TYPE,
                                                INVALID_DAMAGE_TYPE_EXCEPTION),
                                        EntityArgument.getEntities(context, "targets"),
                                        context.getSource())))));
    }

    private static int convertDamageType(
            ResourceKey<DamageType> damageType,
            Collection<? extends Entity> targets,
            CommandSourceStack source) {
        targets.forEach(target -> ConvertState.setDamageType(target, damageType));
        source.sendSuccess(
                () -> Component.literal("Converted outgoing damage to " + damageType.identifier()
                        + " for " + targets.size() + " entit" + (targets.size() == 1 ? "y" : "ies") + "."),
                true);
        return targets.size();
    }
}
