package net.baconeater.features.commands.shader.client;

import net.baconeater.features.commands.shader.ShaderState;
import net.minecraft.util.Identifier;

public final class ShaderContextManager {
    private static final float CINEMATIC_BARS_DURATION_SECONDS = 1.0F;

    private static volatile Identifier activeShaderId;
    private static volatile ShaderState activeState = ShaderState.NONE;
    private static volatile long stateStartNanos = System.nanoTime();

    private ShaderContextManager() {
    }

    public static void onShaderEnabled(Identifier shaderId, ShaderState state) {
        activeShaderId = shaderId;
        activeState = state;
        stateStartNanos = System.nanoTime();
    }

    public static void onShaderDisabled() {
        activeShaderId = null;
        activeState = ShaderState.NONE;
        stateStartNanos = System.nanoTime();
    }

    public static boolean shouldDisableAfterAnimation(Identifier shaderId) {
        return shaderId != null
                && shaderId.equals(activeShaderId)
                && activeState == ShaderState.OUT
                && getElapsedSeconds() >= getAnimationDurationSeconds(shaderId);
    }

    public static float getElapsedSeconds() {
        return (System.nanoTime() - stateStartNanos) / 1_000_000_000.0F;
    }

    public static float getDisplayElapsedSeconds(String passId, float defaultElapsedSeconds) {
        ShaderState state = activeState;
        if (state == ShaderState.NONE) {
            return defaultElapsedSeconds;
        }

        if (passId.contains("cinematic_bars")) {
            float duration = CINEMATIC_BARS_DURATION_SECONDS;
            return state == ShaderState.OUT
                    ? Math.max(0.0F, duration - Math.min(defaultElapsedSeconds, duration))
                    : Math.min(defaultElapsedSeconds, duration);
        }

        return defaultElapsedSeconds;
    }

    private static float getAnimationDurationSeconds(Identifier shaderId) {
        if (shaderId.getPath().contains("cinematic_bars")) {
            return CINEMATIC_BARS_DURATION_SECONDS;
        }
        return 0.0F;
    }
}
