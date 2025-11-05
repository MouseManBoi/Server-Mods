package net.baconeater;

import net.baconeater.features.keybinds.payload.KeybindC2S;
import net.baconeater.features.shaders.ShaderController;
import net.baconeater.features.shaders.payload.ShaderSelectS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModClient implements ClientModInitializer {
    private static KeyBinding customAbilityToggle, customAbilityMove1, customAbilityMove2, customAbilityMove3, customAbilityMove4;

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("keybinds", "abilities"));

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ShaderSelectS2C.ID,
                (payload, ctx) -> ShaderController.setDesired(payload.shaderIdOrNull()));

        // Default keys: R, Z, X, C, V
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

        // Send a tiny action code; NO chat messages are sent anywhere
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (customAbilityToggle.wasPressed()) ClientPlayNetworking.send(new KeybindC2S(0));
            while (customAbilityMove1.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(1));
            while (customAbilityMove2.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(2));
            while (customAbilityMove3.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(3));
            while (customAbilityMove4.wasPressed())  ClientPlayNetworking.send(new KeybindC2S(4));
        });
    }
}
