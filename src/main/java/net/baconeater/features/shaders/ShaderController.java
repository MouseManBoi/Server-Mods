package net.baconeater.features.shaders;

import net.baconeater.mixin.client.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class ShaderController {
    private ShaderController() {}

    private static Identifier desired = null;   // null = off
    private static boolean failedOnce = false;

    public static void setDesired(Identifier idOrNull) {
        desired = idOrNull;
        failedOnce = false;
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.gameRenderer == null) return;
        var gr = client.gameRenderer;

        // OFF state
        if (desired == null) {
            try { gr.clearPostProcessor(); } catch (Throwable ignored) {}
            return;
        }

        // Already active?
        var active = gr.getPostProcessorId(); // null if none
        if (desired.equals(active)) return;

        // Try to switch; on failure, turn off and stop retrying
        try { gr.clearPostProcessor(); } catch (Throwable ignored) {}
        try {
            ((GameRendererAccessor) gr).keybinds$setPostProcessor(desired);
        } catch (Throwable t) {
            if (!failedOnce) {
                System.out.println("[keybinds] Failed to load post shader: " + desired + " (turning off)");
                failedOnce = true;
            }
            desired = null; // stop spamming & turn off
            try { gr.clearPostProcessor(); } catch (Throwable ignored) {}
        }
    }
}
