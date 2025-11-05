package net.baconeater.client;

//import net.minecraft.client.MinecraftClient;
//import net.minecraft.util.Identifier;
//
///** Minimal controller to force the vanilla creeper post-effect on/off. */
//public final class ShaderController {
//    private ShaderController() {}
//
//    // Vanilla creeper post shader
//    private static final Identifier CREEPER_POST = Identifier.of("minecraft", "shaders/post/creeper.json");
//
//    // Desired state set by the server; we’ll enforce it every few ticks
//    private static volatile boolean wantCreeper = false;
//    private static int retryCooldown = 0; // ticks
//
//    public static void setDesired(boolean enabled) {
//        wantCreeper = enabled;
//        retryCooldown = 0; // apply immediately next tick
//    }
//
//    /** Called each client tick from your KeybindsClient. */
//    public static void tick(MinecraftClient client) {
//        if (client == null || client.gameRenderer == null) return;
//
//        if (wantCreeper) {
//            // Re-assert occasionally in case another overlay closes it
//            if (retryCooldown-- <= 0) {
//                try {
//                    client.gameRenderer.loadPostProcessor(CREEPER_POST);
//                } catch (Exception ignored) {
//                    // If resource was missing (unlikely), just ignore.
//                }
//                retryCooldown = 10; // try again every ~0.5s
//            }
//        } else {
//            // Ensure post-effect is closed when disabled
//            try {
//                client.gameRenderer.closePostProcessor();
//            } catch (Exception ignored) {}
//        }
//    }
//}
