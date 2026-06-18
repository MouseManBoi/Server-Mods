package net.baconeater.features.commands.shader.client;

import net.baconeater.features.commands.shader.ShaderState;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShaderContextManager {
    private static final float CINEMATIC_BARS_DURATION_SECONDS = 1.0F;

    private static final Map<Identifier, ShaderContext> ACTIVE_CONTEXTS = new HashMap<>();

    private ShaderContextManager() {
    }

    public static synchronized void onShaderEnabled(Identifier shaderId, ShaderState state) {
        ACTIVE_CONTEXTS.put(shaderId, new ShaderContext(state, System.nanoTime()));
    }

    public static synchronized void onShaderDisabled(Identifier shaderId) {
        ACTIVE_CONTEXTS.remove(shaderId);
    }

    public static synchronized void onShaderDisabled() {
        ACTIVE_CONTEXTS.clear();
    }

    public static synchronized List<Identifier> getShadersReadyToDisable() {
        List<Identifier> shadersToDisable = new ArrayList<>();
        for (Map.Entry<Identifier, ShaderContext> entry : ACTIVE_CONTEXTS.entrySet()) {
            Identifier shaderId = entry.getKey();
            ShaderContext context = entry.getValue();
            if (context.state() == ShaderState.OUT
                    && getElapsedSeconds(context) >= getAnimationDurationSeconds(shaderId)) {
                shadersToDisable.add(shaderId);
            }
        }
        return shadersToDisable;
    }

    public static synchronized float getDisplayElapsedSeconds(String passId, float defaultElapsedSeconds) {
        ShaderContext context = getContextForPass(passId);
        if (context == null) {
            return defaultElapsedSeconds;
        }

        ShaderState state = context.state();
        float elapsedSeconds = getElapsedSeconds(context);
        if (state == ShaderState.NONE) {
            return elapsedSeconds;
        }

        if (passId.contains("cinematic_bars")) {
            float duration = CINEMATIC_BARS_DURATION_SECONDS;
            return state == ShaderState.OUT
                    ? Math.max(0.0F, duration - Math.min(elapsedSeconds, duration))
                    : Math.min(elapsedSeconds, duration);
        }

        return elapsedSeconds;
    }

    private static ShaderContext getContextForPass(String passId) {
        for (Map.Entry<Identifier, ShaderContext> entry : ACTIVE_CONTEXTS.entrySet()) {
            String shaderId = entry.getKey().toString();
            if (passId.equals(shaderId) || passId.startsWith(shaderId + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static float getElapsedSeconds(ShaderContext context) {
        return (System.nanoTime() - context.stateStartNanos()) / 1_000_000_000.0F;
    }

    private static float getAnimationDurationSeconds(Identifier shaderId) {
        if (shaderId.getPath().contains("cinematic_bars")) {
            return CINEMATIC_BARS_DURATION_SECONDS;
        }
        return 0.0F;
    }

    private record ShaderContext(ShaderState state, long stateStartNanos) {
    }
}
