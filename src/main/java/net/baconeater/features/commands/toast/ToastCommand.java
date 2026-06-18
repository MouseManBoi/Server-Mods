package net.baconeater.features.commands.toast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.baconeater.features.commands.toast.network.ToastPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Collection;

public final class ToastCommand {
    private ToastCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(createToastCommand(registryAccess));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createToastCommand(CommandBuildContext registryAccess) {
        return Commands.literal("toast")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.players())
                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                .then(Commands.argument("title", ComponentArgument.textComponent(registryAccess))
                                        .then(Commands.argument("subtitle", ComponentArgument.textComponent(registryAccess))
                                                .executes(context -> sendToast(
                                                        EntityArgument.getPlayers(context, "player"),
                                                        ItemArgument.getItem(context, "item").createItemStack(1),
                                                        ComponentArgument.getRawComponent(context, "title"),
                                                        ComponentArgument.getRawComponent(context, "subtitle"),
                                                        context.getSource()))))));
    }

    private static int sendToast(
            Collection<ServerPlayer> players,
            ItemStack icon,
            Component title,
            Component subtitle,
            CommandSourceStack source) {
        ToastPayload payload = new ToastPayload(icon, title, subtitle);
        players.forEach(player -> ServerPlayNetworking.send(player, payload));
        source.sendSuccess(
                () -> Component.literal("Sent toast to " + players.size() + " player(s)."),
                true);
        return players.size();
    }
}
