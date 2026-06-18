package net.baconeater.features.commands.shader.network;

import net.baconeater.features.commands.shader.ShaderState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleShaderPayload(
        Identifier shaderId,
        ShaderAction action,
        ShaderState state,
        boolean renderOnTop) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToggleShaderPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("creepershader", "toggle_shader"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleShaderPayload> CODEC = new StreamCodec<>() {
        @Override
        public ToggleShaderPayload decode(RegistryFriendlyByteBuf buf) {
            Identifier shader = buf.readIdentifier();
            ShaderAction shaderAction = buf.readEnum(ShaderAction.class);
            ShaderState shaderState = buf.readEnum(ShaderState.class);
            boolean renderOnTop = buf.readBoolean();
            return new ToggleShaderPayload(shader, shaderAction, shaderState, renderOnTop);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ToggleShaderPayload value) {
            buf.writeIdentifier(value.shaderId());
            buf.writeEnum(value.action());
            buf.writeEnum(value.state());
            buf.writeBoolean(value.renderOnTop());
        }
    };

    public static ToggleShaderPayload toggle(
            Identifier shaderId,
            ShaderState state,
            boolean renderOnTop) {
        return new ToggleShaderPayload(shaderId, ShaderAction.TOGGLE, state, renderOnTop);
    }

    public static ToggleShaderPayload enable(
            Identifier shaderId,
            ShaderState state,
            boolean renderOnTop) {
        return new ToggleShaderPayload(shaderId, ShaderAction.ENABLE, state, renderOnTop);
    }

    public static ToggleShaderPayload disable(
            Identifier shaderId,
            ShaderState state,
            boolean renderOnTop) {
        return new ToggleShaderPayload(shaderId, ShaderAction.DISABLE, state, renderOnTop);
    }

    @Override
    public Type<ToggleShaderPayload> type() {
        return TYPE;
    }

    public enum ShaderAction {
        TOGGLE,
        ENABLE,
        DISABLE
    }
}
