package net.baconeater.features.commands.shader.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ShaderToggleS2C(String effect, boolean enabled) implements CustomPayload {
    public static final CustomPayload.Id<ShaderToggleS2C> ID =
            new CustomPayload.Id<>(Identifier.of("baconeater", "shader_toggle"));

    // Minimal boolean codec bound to RegistryByteBuf to satisfy the generic B=RegistryByteBuf
    public static final PacketCodec<RegistryByteBuf, Boolean> BOOL_CODEC = new PacketCodec<>() {
        @Override public Boolean decode(RegistryByteBuf buf) { return buf.readBoolean(); }
        @Override public void encode(RegistryByteBuf buf, Boolean value) { buf.writeBoolean(value); }
    };

    public static final PacketCodec<RegistryByteBuf, ShaderToggleS2C> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, ShaderToggleS2C::effect,
                    BOOL_CODEC,           ShaderToggleS2C::enabled,
                    ShaderToggleS2C::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
