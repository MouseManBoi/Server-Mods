package net.baconeater.features.commands.perspective.network;

import net.baconeater.features.commands.perspective.PerspectiveState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record PerspectiveRequestPayload(PerspectiveAction action, PerspectiveState state, UUID requestId)
        implements CustomPayload {
    public static final CustomPayload.Id<PerspectiveRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("perspective", "request"));

    public static final PacketCodec<RegistryByteBuf, PerspectiveRequestPayload> CODEC = new PacketCodec<>() {
        @Override
        public PerspectiveRequestPayload decode(RegistryByteBuf buf) {
            PerspectiveAction action = buf.readEnumConstant(PerspectiveAction.class);
            PerspectiveState state = null;
            UUID requestId = null;
            switch (action) {
                case SET -> state = buf.readEnumConstant(PerspectiveState.class);
                case QUERY -> requestId = buf.readUuid();
            }
            return new PerspectiveRequestPayload(action, state, requestId);
        }

        @Override
        public void encode(RegistryByteBuf buf, PerspectiveRequestPayload value) {
            buf.writeEnumConstant(value.action());
            switch (value.action()) {
                case SET -> buf.writeEnumConstant(value.state());
                case QUERY -> buf.writeUuid(value.requestId());
            }
        }
    };

    public static PerspectiveRequestPayload set(PerspectiveState state) {
        return new PerspectiveRequestPayload(PerspectiveAction.SET, state, null);
    }

    public static PerspectiveRequestPayload query(UUID requestId) {
        return new PerspectiveRequestPayload(PerspectiveAction.QUERY, null, requestId);
    }

    @Override
    public Id<PerspectiveRequestPayload> getId() {
        return ID;
    }

    public enum PerspectiveAction {
        SET,
        QUERY
    }
}