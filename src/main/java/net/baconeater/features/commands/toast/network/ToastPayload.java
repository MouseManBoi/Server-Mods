package net.baconeater.features.commands.toast.network;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

public record ToastPayload(ItemStack icon, Component title, Component subtitle) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToastPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("server", "toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToastPayload> CODEC = new StreamCodec<>() {
        @Override
        public ToastPayload decode(RegistryFriendlyByteBuf buf) {
            ItemStack icon = ItemStack.STREAM_CODEC.decode(buf);
            Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
            Component subtitle = ComponentSerialization.STREAM_CODEC.decode(buf);
            return new ToastPayload(icon, title, subtitle);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ToastPayload value) {
            ItemStack.STREAM_CODEC.encode(buf, value.icon());
            ComponentSerialization.STREAM_CODEC.encode(buf, value.title());
            ComponentSerialization.STREAM_CODEC.encode(buf, value.subtitle());
        }
    };

    @Override
    public Type<ToastPayload> type() {
        return TYPE;
    }
}
