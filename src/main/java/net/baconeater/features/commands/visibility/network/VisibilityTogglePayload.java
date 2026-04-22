package net.baconeater.features.commands.visibility.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VisibilityTogglePayload(int entityId, VisibilityAction action, boolean renderOutsideFirstPerson) implements CustomPayload {
    public static final CustomPayload.Id<VisibilityTogglePayload> ID =
            new CustomPayload.Id<>(Identifier.of("visibility", "toggle"));

    public static final PacketCodec<RegistryByteBuf, VisibilityTogglePayload> CODEC = new PacketCodec<>() {
        @Override
        public VisibilityTogglePayload decode(RegistryByteBuf buf) {
            int entityId = buf.readVarInt();
            VisibilityAction action = buf.readEnumConstant(VisibilityAction.class);
            boolean renderOutsideFirstPerson = buf.readBoolean();
            return new VisibilityTogglePayload(entityId, action, renderOutsideFirstPerson);
        }

        @Override
        public void encode(RegistryByteBuf buf, VisibilityTogglePayload value) {
            buf.writeVarInt(value.entityId());
            buf.writeEnumConstant(value.action());
            buf.writeBoolean(value.renderOutsideFirstPerson());
        }
    };

    public static VisibilityTogglePayload disable(int entityId, boolean renderOutsideFirstPerson) {
        return new VisibilityTogglePayload(entityId, VisibilityAction.DISABLE, renderOutsideFirstPerson);
    }

    public static VisibilityTogglePayload enable(int entityId) {
        return new VisibilityTogglePayload(entityId, VisibilityAction.ENABLE, false);
    }

    @Override
    public Id<VisibilityTogglePayload> getId() {
        return ID;
    }

    public enum VisibilityAction {
        DISABLE,
        ENABLE;

        public boolean hides() {
            return this == DISABLE;
        }
    }
}
