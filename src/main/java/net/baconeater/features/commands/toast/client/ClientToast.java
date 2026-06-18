package net.baconeater.features.commands.toast.client;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ClientToast extends SystemToast {
    public ClientToast(ItemStack icon, Component title, Component subtitle) {
        super(new SystemToast.SystemToastId(), title, subtitle);
    }
}
