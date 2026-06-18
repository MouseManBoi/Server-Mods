package net.baconeater;

import net.baconeater.features.commands.shader.ShaderState;
import net.baconeater.features.commands.playsound.client.OffsetPositionedSoundInstance;
import net.baconeater.features.commands.playsound.network.PlaySoundOffsetPayload;
import net.baconeater.features.commands.shader.client.ClientShaderManager;
import net.baconeater.features.commands.shader.client.DomainDisplaySkinAtlasManager;
import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.baconeater.features.commands.toast.client.ClientToast;
import net.baconeater.features.commands.toast.network.ToastPayload;
import net.baconeater.features.commands.visibility.client.ClientVisibilityManager;
import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.baconeater.features.keybinds.payload.KeybindC2S;
import net.baconeater.features.commands.perspective.network.PerspectiveRequestPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;


public class ModClient implements ClientModInitializer {
    private static KeyMapping customAbilityToggle, customAbilityMove1, customAbilityMove2, customAbilityMove3, customAbilityMove4;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("keybinds", "abilities"));

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(ToggleShaderPayload.TYPE, ToggleShaderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VisibilityTogglePayload.TYPE, VisibilityTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerspectiveRequestPayload.TYPE, PerspectiveRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ToastPayload.TYPE, ToastPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ToggleShaderPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handlePayload(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(PlaySoundOffsetPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handlePlaySoundOffset(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(VisibilityTogglePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientVisibilityManager.handlePayload(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PerspectiveRequestPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handlePerspectiveRequest(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(ToastPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().getToastManager()
                        .add(new ClientToast(payload.icon(), payload.title(), payload.subtitle()))));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientVisibilityManager.clear();
            ClientShaderManager.clear();
            DomainDisplaySkinAtlasManager.clear();
        });

        // === Keybinds you already had ===
        customAbilityToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybinds.abilities.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        customAbilityMove1  = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybinds.abilities.move1",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));
        customAbilityMove2  = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybinds.abilities.move2",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY));
        customAbilityMove3  = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybinds.abilities.move3",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        customAbilityMove4  = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keybinds.abilities.move4",  InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (customAbilityToggle.consumeClick()) ClientPlayNetworking.send(new KeybindC2S(0));
            while (customAbilityMove1.consumeClick())  ClientPlayNetworking.send(new KeybindC2S(1));
            while (customAbilityMove2.consumeClick())  ClientPlayNetworking.send(new KeybindC2S(2));
            while (customAbilityMove3.consumeClick())  ClientPlayNetworking.send(new KeybindC2S(3));
            while (customAbilityMove4.consumeClick())  ClientPlayNetworking.send(new KeybindC2S(4));

            DomainDisplaySkinAtlasManager.preloadCurrentPlayer(client);
            ClientVisibilityManager.tick(client);
            ClientShaderManager.tick();
        });
    }

    private void handlePayload(Minecraft client, ToggleShaderPayload payload) {
        try {
            if (client == null) {
                return;
            }
            Identifier shaderId = payload.shaderId();
            ShaderState shaderState = payload.state();
            boolean renderOnTop = payload.renderOnTop();
            switch (payload.action()) {
                case ENABLE -> {
                    enableShader(client, shaderId, shaderState, renderOnTop);
                }
                case DISABLE -> disableShader(shaderId, shaderState);
                case TOGGLE -> toggleShader(client, shaderId, shaderState, renderOnTop);
            }
        } catch (Throwable ignored) {
            ClientShaderManager.clear();
        }
    }

    private void handlePlaySoundOffset(Minecraft client, PlaySoundOffsetPayload payload) {
        if (client == null || client.getSoundManager() == null) {
            return;
        }

        client.getSoundManager().play(new OffsetPositionedSoundInstance(
                payload.soundId(),
                payload.category(),
                payload.volume(),
                payload.pitch(),
                RandomSource.create(payload.seed()),
                SoundInstance.Attenuation.LINEAR,
                payload.pos().x,
                payload.pos().y,
                payload.pos().z,
                payload.seconds()
        ));
    }

    private void toggleShader(
            Minecraft client,
            Identifier shaderId,
            ShaderState shaderState,
            boolean renderOnTop) {
        if (!ClientShaderManager.isActive(shaderId)) {
            enableShader(client, shaderId, shaderState, renderOnTop);
            return;
        }

        disableShader(shaderId, shaderState);
    }

    private void enableShader(
            Minecraft client,
            Identifier shaderId,
            ShaderState shaderState,
            boolean renderOnTop) {
        DomainDisplaySkinAtlasManager.prepareThenRun(
                client,
                shaderId,
                () -> ClientShaderManager.enableShader(client, shaderId, shaderState, renderOnTop));
    }

    private void disableShader(Identifier shaderId, ShaderState shaderState) {
        if (shaderState == ShaderState.OUT && ClientShaderManager.startShaderOut(shaderId)) {
            return;
        }
        ClientShaderManager.disableShader(shaderId);
    }

    private void handlePerspectiveRequest(Minecraft client, PerspectiveRequestPayload payload) {
        if (client == null || client.options == null) {
            return;
        }
        CameraType newPerspective = switch (payload.state()) {
            case FIRST -> CameraType.FIRST_PERSON;
            case SECOND -> CameraType.THIRD_PERSON_BACK;
            case THIRD -> CameraType.THIRD_PERSON_FRONT;
        };

        client.options.setCameraType(newPerspective);
    }
}
