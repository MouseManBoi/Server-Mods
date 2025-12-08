package net.baconeater.features.commands.perspective.network;

import net.baconeater.features.commands.perspective.PerspectiveState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record PerspectiveReportPayload(UUID requestId, PerspectiveState state) implements CustomPayload {
    public static final CustomPayload.Id<PerspectiveReportPayload> ID =
            new CustomPayload.Id<>(Identifier.of("perspective", "report"));

    public static final PacketCodec<RegistryByteBuf, PerspectiveReportPayload> CODEC = new PacketCodec<>() {
        @Override
        public PerspectiveReportPayload decode(RegistryByteBuf buf) {
            UUID requestId = buf.readUuid();
            PerspectiveState state = buf.readEnumConstant(PerspectiveState.class);
            return new PerspectiveReportPayload(requestId, state);
        }

        @Override
        public void encode(RegistryByteBuf buf, PerspectiveReportPayload value) {
            buf.writeUuid(value.requestId());
            buf.writeEnumConstant(value.state());
        }
    };

    @Override
    public Id<PerspectiveReportPayload> getId() {
        return ID;
    }
}