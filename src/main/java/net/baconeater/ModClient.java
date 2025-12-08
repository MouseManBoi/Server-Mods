package net.baconeater;

import net.baconeater.features.commands.shader.network.ToggleShaderPayload;
import net.baconeater.features.commands.visibility.client.ClientVisibilityManager;
import net.baconeater.features.commands.visibility.network.VisibilityTogglePayload;
import net.baconeater.features.keybinds.payload.KeybindC2S;
import net.baconeater.features.commands.perspective.PerspectiveState;
import net.baconeater.features.commands.perspective.network.PerspectiveRequestPayload;
import net.baconeater.mixin.GameRendererInvoker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;


public class ModClient implements ClientModInitializer {
    private static final Identifier CREEPER_CHAIN_NEW  = Identifier.of("minecraft", "creeper");
    private static final Identifier CREEPER_CHAIN_OLD  = Identifier.of("minecraft", "shaders/post/creeper.json");
    private Identifier activeShader = null;
    private static KeyBinding customAbilityToggle, customAbilityMove1, customAbilityMove2, customAbilityMove3, customAbilityMove4;
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("keybinds", "abilities"));

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(ToggleShaderPayload.ID, ToggleShaderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VisibilityTogglePayload.ID, VisibilityTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerspectiveRequestPayload.ID, PerspectiveRequestPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ToggleShaderPayload.ID, (payload, context) ->
                context.client().execute(() -> handlePayload(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(VisibilityTogglePayload.ID, (payload, context) ->
                context.client().execute(() -> ClientVisibilityManager.handlePayload(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PerspectiveRequestPayload.ID, (payload, context) ->
                context.client().execute(() -> handlePerspectiveRequest(context.client(), payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientVisibilityManager.clear());

        // === Keybinds you already had ===
        customAbilityToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.keybinds.abilities.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        customAbilityMove1  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.keybinds.abilities.move1",  InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));
        customAbilityMove2  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.keybinds.abilities.move2",  InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY));
        customAbilityMove3  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.keybinds.abilities.move3",  InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        customAbilityMove4  = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.keybinds.abilities.move4",  InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (customAbilityToggle.wasPressed()) ClientPlayNetworking.send(new KeybindC2S(0));
            while (customAbilityMove1.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(1));
            while (customAbilityMove2.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(2));
            while (customAbilityMove3.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(3));
            while (customAbilityMove4.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(4));

            ClientVisibilityManager.tick(client);
        });
    }

    private void handlePayload(MinecraftClient client, ToggleShaderPayload payload) {
        if (client == null) {
            return;
        }
        boolean enable;
        Identifier shaderId = payload.shaderId();
        switch (payload.action()) {
            case ENABLE -> enable = true;
            case DISABLE -> enable = false;
            case TOGGLE -> enable = !shaderId.equals(activeShader);
            default -> throw new IllegalStateException("Unhandled shader action: " + payload.action());
        }

        boolean success;
        if (enable) {
            success = enableShader(client, shaderId);
        } else {
            success = disableShader(client);
        }
        if (success) {
            activeShader = enable ? shaderId : null;
        }
    }

    private boolean enableShader(MinecraftClient client, Identifier shaderId) {
        if (client == null || client.gameRenderer == null) {
            return false;
        }
        try {
            ((GameRendererInvoker) client.gameRenderer).invokeSetPostProcessor(shaderId);
            return true;
        } catch (Throwable original) {
            // Fallback for creeper shader to older resource path
            if (shaderId.equals(CREEPER_CHAIN_NEW)) {
                try {
                    ((GameRendererInvoker) client.gameRenderer).invokeSetPostProcessor(CREEPER_CHAIN_OLD);
                    return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }


    private boolean disableShader(MinecraftClient client) {
        if (client == null || client.gameRenderer == null) {
            return false;
        }
        client.gameRenderer.clearPostProcessor(); // hard OFF
        return true;
    }

    private void handlePerspectiveRequest(MinecraftClient client, PerspectiveRequestPayload payload) {
        if (client == null || client.options == null) {
            return;
        }
        client.options.setPerspective(payload.state().toClientPerspective());
    }
}
