package net.baconeater.features.keybinds.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** action: 0=toggle(R), 1=move1(Z), 2=move2(X), 3=move3(C), 4=move4(V) */
public record C2SPayload(int action) implements CustomPayload {
    public static final Id<C2SPayload> ID =
            new Id<>(Identifier.of("keybinds", "ability_key"));

    // Encoder is (value, buf); decoder is (buf) -> value
    public static final PacketCodec<RegistryByteBuf, C2SPayload> CODEC =
            PacketCodec.of(
                    (C2SPayload payload, RegistryByteBuf buf) -> buf.writeVarInt(payload.action()),
                    (RegistryByteBuf buf) -> new C2SPayload(buf.readVarInt())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
