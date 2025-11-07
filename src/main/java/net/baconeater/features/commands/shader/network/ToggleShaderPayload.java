package net.baconeater.features.commands.shader.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ToggleShaderPayload(Identifier shaderId, ShaderAction action) implements CustomPayload {
    public static final CustomPayload.Id<ToggleShaderPayload> ID = new CustomPayload.Id<>(Identifier.of("creepershader", "toggle_shader"));

    public static final PacketCodec<RegistryByteBuf, ToggleShaderPayload> CODEC = new PacketCodec<>() {
        @Override
        public ToggleShaderPayload decode(RegistryByteBuf buf) {
            Identifier shader = buf.readIdentifier();
            ShaderAction shaderAction = buf.readEnumConstant(ShaderAction.class);
            return new ToggleShaderPayload(shader, shaderAction);
        }

        @Override
        public void encode(RegistryByteBuf buf, ToggleShaderPayload value) {
            buf.writeIdentifier(value.shaderId());
            buf.writeEnumConstant(value.action());
        }
    };

    public static ToggleShaderPayload toggle(Identifier shaderId) {
        return new ToggleShaderPayload(shaderId, ShaderAction.TOGGLE);
    }

    public static ToggleShaderPayload enable(Identifier shaderId) {
        return new ToggleShaderPayload(shaderId, ShaderAction.ENABLE);
    }

    public static ToggleShaderPayload disable(Identifier shaderId) {
        return new ToggleShaderPayload(shaderId, ShaderAction.DISABLE);
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