package net.baconeater.features.commands.visibility.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VisibilityTogglePayload(int entityId, VisibilityAction action) implements CustomPayload {
    public static final CustomPayload.Id<VisibilityTogglePayload> ID =
            new CustomPayload.Id<>(Identifier.of("visibility", "toggle"));

    public static final PacketCodec<RegistryByteBuf, VisibilityTogglePayload> CODEC = new PacketCodec<>() {
        @Override
        public VisibilityTogglePayload decode(RegistryByteBuf buf) {
            int entityId = buf.readVarInt();
            VisibilityAction action = buf.readEnumConstant(VisibilityAction.class);
            return new VisibilityTogglePayload(entityId, action);
        }

        @Override
        public void encode(RegistryByteBuf buf, VisibilityTogglePayload value) {
            buf.writeVarInt(value.entityId());
            buf.writeEnumConstant(value.action());
        }
    };

    public static VisibilityTogglePayload disable(int entityId) {
        return new VisibilityTogglePayload(entityId, VisibilityAction.DISABLE);
    }

    public static VisibilityTogglePayload enable(int entityId) {
        return new VisibilityTogglePayload(entityId, VisibilityAction.ENABLE);
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