package net.baconeater.features.commands.visibility.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VisibilityTogglePayload(int entityId, VisibilityAction action, boolean renderOutsideFirstPerson) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VisibilityTogglePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("visibility", "toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VisibilityTogglePayload> CODEC = new StreamCodec<>() {
        @Override
        public VisibilityTogglePayload decode(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            VisibilityAction action = buf.readEnum(VisibilityAction.class);
            boolean renderOutsideFirstPerson = buf.readBoolean();
            return new VisibilityTogglePayload(entityId, action, renderOutsideFirstPerson);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, VisibilityTogglePayload value) {
            buf.writeVarInt(value.entityId());
            buf.writeEnum(value.action());
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
    public Type<VisibilityTogglePayload> type() {
        return TYPE;
    }

    public enum VisibilityAction {
        DISABLE,
        ENABLE;

        public boolean hides() {
            return this == DISABLE;
        }
    }
}
