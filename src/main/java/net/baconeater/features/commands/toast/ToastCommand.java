package net.baconeater.features.commands.toast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.toast.network.ToastPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

public final class ToastCommand {
    private ToastCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(createToastCommand(registryAccess));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createToastCommand(CommandRegistryAccess registryAccess) {
        return CommandManager.literal("toast")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.players())
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .then(CommandManager.argument("title", TextArgumentType.text(registryAccess))
                                        .then(CommandManager.argument("subtitle", TextArgumentType.text(registryAccess))
                                                .executes(context -> sendToast(
                                                        EntityArgumentType.getPlayers(context, "player"),
                                                        ItemStackArgumentType.getItemStackArgument(context, "item").createStack(1, false),
                                                        TextArgumentType.getTextArgument(context, "title"),
                                                        TextArgumentType.getTextArgument(context, "subtitle"),
                                                        context.getSource()))))));
    }

    private static int sendToast(
            Collection<ServerPlayerEntity> players,
            ItemStack icon,
            Text title,
            Text subtitle,
            ServerCommandSource source) {
        ToastPayload payload = new ToastPayload(icon, title, subtitle);
        players.forEach(player -> ServerPlayNetworking.send(player, payload));
        source.sendFeedback(
                () -> Text.literal("Sent toast to " + players.size() + " player(s)."),
                true);
        return players.size();
    }
}
