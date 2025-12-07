package net.baconeater.features.commands.hide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

import it.unimi.dsi.fastutil.ints.IntArrayList;

public final class HideCommand {
    private HideCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(createHideCommand());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createHideCommand() {
        return CommandManager.literal("hide")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", EntityArgumentType.entity())
                        .then(CommandManager.argument("viewers", EntityArgumentType.players())
                                .executes(HideCommand::hideEntityFromViewers)));
    }

    private static int hideEntityFromViewers(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Entity target = EntityArgumentType.getEntity(context, "target");
        Collection<ServerPlayerEntity> viewers = EntityArgumentType.getPlayers(context, "viewers");

        IntArrayList ids = new IntArrayList();
        collectSelfAndPassengers(target, ids);

        EntitiesDestroyS2CPacket destroyPacket = new EntitiesDestroyS2CPacket(ids);
        viewers.forEach(player -> player.networkHandler.sendPacket(destroyPacket));

        context.getSource().sendFeedback(
                () -> Text.literal(
                        "Hid " + ids.size() + " entit" + (ids.size() == 1 ? "y" : "ies")
                                + " from " + viewers.size() + " player(s)."),
                true);

        return ids.size();
    }

    private static void collectSelfAndPassengers(Entity entity, IntArrayList ids) {
        ids.add(entity.getId());
        entity.getPassengerList().forEach(passenger -> collectSelfAndPassengers(passenger, ids));
    }
}