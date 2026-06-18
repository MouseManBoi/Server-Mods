package net.baconeater.features.commands.perspective.network;

import net.baconeater.features.commands.perspective.PerspectiveState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PerspectiveRequestPayload(PerspectiveState state) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PerspectiveRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("perspective", "request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PerspectiveRequestPayload> CODEC = new StreamCodec<>() {
        @Override
        public PerspectiveRequestPayload decode(RegistryFriendlyByteBuf buf) {
            PerspectiveState state = buf.readEnum(PerspectiveState.class);
            return new PerspectiveRequestPayload(state);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PerspectiveRequestPayload value) {
            buf.writeEnum(value.state());
        }
    };

    public static PerspectiveRequestPayload set(PerspectiveState state) {
        return new PerspectiveRequestPayload(state);
    }

    @Override
    public Type<PerspectiveRequestPayload> type() {
        return TYPE;
    }
}
