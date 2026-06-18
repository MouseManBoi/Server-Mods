package net.baconeater.features.commands.heal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Collection;

public final class HealCommand {
    private HealCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(createHealCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createHealCommand() {
        return Commands.literal("heal")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> healPlayers(
                                        EntityArgument.getPlayers(context, "targets"),
                                        IntegerArgumentType.getInteger(context, "amount"),
                                        context.getSource()))));
    }

    private static int healPlayers(
            Collection<ServerPlayer> players,
            int healthPoints,
            CommandSourceStack source) {
        float healthAmount = (float) healthPoints;
        players.forEach(player -> player.heal(healthAmount));
        source.sendSuccess(
                () -> Component.literal("Healed " + players.size() + " player(s) for " + healthPoints + " health."),
                true);
        return players.size();
    }
}