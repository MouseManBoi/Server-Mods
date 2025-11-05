package net.baconeater.features.shaders.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ShaderSelectS2C(Identifier shaderIdOrNull) implements CustomPayload {
    public static final Id<ShaderSelectS2C> ID =
            new Id<>(Identifier.of("keybinds", "shader_select"));

    public static final PacketCodec<RegistryByteBuf, ShaderSelectS2C> CODEC =
            PacketCodec.of(
                    (ShaderSelectS2C value, RegistryByteBuf buf) -> {
                        boolean present = value.shaderIdOrNull() != null;
                        buf.writeBoolean(present);
                        if (present) buf.writeString(value.shaderIdOrNull().toString());
                    },
                    (RegistryByteBuf buf) -> {
                        boolean present = buf.readBoolean();
                        return new ShaderSelectS2C(present ? Identifier.of(buf.readString()) : null);
                    }
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
