package net.baconeater.features.commands.perspective.network;

import net.baconeater.features.commands.perspective.PerspectiveState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PerspectiveRequestPayload(PerspectiveState state) implements CustomPayload {
    public static final CustomPayload.Id<PerspectiveRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("perspective", "request"));

    public static final PacketCodec<RegistryByteBuf, PerspectiveRequestPayload> CODEC = new PacketCodec<>() {
        @Override
        public PerspectiveRequestPayload decode(RegistryByteBuf buf) {
            PerspectiveState state = buf.readEnumConstant(PerspectiveState.class);
            return new PerspectiveRequestPayload(state);
        }

        @Override
        public void encode(RegistryByteBuf buf, PerspectiveRequestPayload value) {
            buf.writeEnumConstant(value.state());
        }
    };

    public static PerspectiveRequestPayload set(PerspectiveState state) {
        return new PerspectiveRequestPayload(state);
    }

    @Override
    public Id<PerspectiveRequestPayload> getId() {
        return ID;
    }
}