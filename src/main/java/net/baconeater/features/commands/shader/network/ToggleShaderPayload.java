package net.baconeater.features.commands.shader.network;

import net.baconeater.features.commands.shader.ShaderState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ToggleShaderPayload(Identifier shaderId, ShaderAction action, ShaderState state) implements CustomPayload {
    public static final CustomPayload.Id<ToggleShaderPayload> ID = new CustomPayload.Id<>(Identifier.of("creepershader", "toggle_shader"));

    public static final PacketCodec<RegistryByteBuf, ToggleShaderPayload> CODEC = new PacketCodec<>() {
        @Override
        public ToggleShaderPayload decode(RegistryByteBuf buf) {
            Identifier shader = buf.readIdentifier();
            ShaderAction shaderAction = buf.readEnumConstant(ShaderAction.class);
            ShaderState shaderState = buf.readEnumConstant(ShaderState.class);
            return new ToggleShaderPayload(shader, shaderAction, shaderState);
        }

        @Override
        public void encode(RegistryByteBuf buf, ToggleShaderPayload value) {
            buf.writeIdentifier(value.shaderId());
            buf.writeEnumConstant(value.action());
            buf.writeEnumConstant(value.state());
        }
    };

    public static ToggleShaderPayload toggle(Identifier shaderId, ShaderState state) {
        return new ToggleShaderPayload(shaderId, ShaderAction.TOGGLE, state);
    }

    public static ToggleShaderPayload enable(Identifier shaderId, ShaderState state) {
        return new ToggleShaderPayload(shaderId, ShaderAction.ENABLE, state);
    }

    public static ToggleShaderPayload disable(Identifier shaderId, ShaderState state) {
        return new ToggleShaderPayload(shaderId, ShaderAction.DISABLE, state);
    }

    @Override
    public Id<ToggleShaderPayload> getId() {
        return ID;
    }

    public enum ShaderAction {
        TOGGLE,
        ENABLE,
        DISABLE
    }
}
