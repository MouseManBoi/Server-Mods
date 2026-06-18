package net.baconeater.features.keybinds.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** action: 0=toggle(R), 1=move1(Z), 2=move2(X), 3=move3(C), 4=move4(V) */
public record KeybindC2S(int action) implements CustomPacketPayload {
    public static final Type<KeybindC2S> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("keybinds", "ability_key"));

    // Encoder is (value, buf); decoder is (buf) -> value
    public static final StreamCodec<RegistryFriendlyByteBuf, KeybindC2S> CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buf, KeybindC2S payload) -> buf.writeVarInt(payload.action()),
                    (RegistryFriendlyByteBuf buf) -> new KeybindC2S(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
