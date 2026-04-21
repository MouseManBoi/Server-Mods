package net.baconeater.features.commands.toast.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

public record ToastPayload(ItemStack icon, Text title, Text subtitle) implements CustomPayload {
    public static final CustomPayload.Id<ToastPayload> ID =
            new CustomPayload.Id<>(Identifier.of("server", "toast"));

    public static final PacketCodec<RegistryByteBuf, ToastPayload> CODEC = new PacketCodec<>() {
        @Override
        public ToastPayload decode(RegistryByteBuf buf) {
            ItemStack icon = ItemStack.PACKET_CODEC.decode(buf);
            Text title = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            Text subtitle = TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.decode(buf);
            return new ToastPayload(icon, title, subtitle);
        }

        @Override
        public void encode(RegistryByteBuf buf, ToastPayload value) {
            ItemStack.PACKET_CODEC.encode(buf, value.icon());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, value.title());
            TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, value.subtitle());
        }
    };

    @Override
    public Id<ToastPayload> getId() {
        return ID;
    }
}
