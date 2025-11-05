package net.baconeater.features.keybinds.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** action: 0=toggle(R), 1=move1(Z), 2=move2(X), 3=move3(C), 4=move4(V) */
public record KeybindC2S(int action) implements CustomPayload {
    public static final Id<KeybindC2S> ID =
            new Id<>(Identifier.of("keybinds", "ability_key"));

    // Encoder is (value, buf); decoder is (buf) -> value
    public static final PacketCodec<RegistryByteBuf, KeybindC2S> CODEC =
            PacketCodec.of(
                    (KeybindC2S payload, RegistryByteBuf buf) -> buf.writeVarInt(payload.action()),
                    (RegistryByteBuf buf) -> new KeybindC2S(buf.readVarInt())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
