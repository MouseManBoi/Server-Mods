package net.baconeater.features.commands.heal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

public final class HealCommand {
    private HealCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(createHealCommand());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createHealCommand() {
        return CommandManager.literal("heal")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> healPlayers(
                                        EntityArgumentType.getPlayers(context, "targets"),
                                        IntegerArgumentType.getInteger(context, "amount"),
                                        context.getSource()))));
    }

    private static int healPlayers(
            Collection<ServerPlayerEntity> players,
            int healthPoints,
            ServerCommandSource source) {
        float healthAmount = (float) healthPoints;
        players.forEach(player -> player.heal(healthAmount));
        source.sendFeedback(
                () -> Text.literal("Healed " + players.size() + " player(s) for " + healthPoints + " health."),
                true);
        return players.size();
    }
}