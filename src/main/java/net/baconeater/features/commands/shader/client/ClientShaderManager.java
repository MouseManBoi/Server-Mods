package net.baconeater.features.commands.shader.client;

import net.baconeater.features.commands.shader.ShaderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.Pool;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientShaderManager {
    private static final ResourceFinder POST_EFFECT_FINDER = ResourceFinder.json("post_effect");
    private static final Identifier CREEPER_CHAIN_NEW = Identifier.of("minecraft", "creeper");
    private static final Identifier CREEPER_CHAIN_OLD = Identifier.of("minecraft", "shaders/post/creeper.json");
    private static final Map<Identifier, ActiveShader> ACTIVE_SHADERS = new LinkedHashMap<>();

    private ClientShaderManager() {
    }

    public static synchronized boolean isActive(Identifier shaderId) {
        return ACTIVE_SHADERS.containsKey(shaderId);
    }

    public static synchronized boolean enableShader(
            MinecraftClient client,
            Identifier shaderId,
            ShaderState state,
            boolean renderOnTop) {
        Identifier renderId = getRenderableShaderId(client, shaderId);
        if (renderId == null) {
            return false;
        }

        addActiveShader(new ActiveShader(shaderId, renderId), renderOnTop);
        ShaderTimeUniforms.onShaderEnabled();
        ShaderContextManager.onShaderEnabled(shaderId, state);
        return true;
    }

    public static synchronized boolean startShaderOut(Identifier shaderId) {
        ActiveShader activeShader = ACTIVE_SHADERS.get(shaderId);
        if (activeShader == null) {
            return false;
        }

        ShaderContextManager.onShaderEnabled(shaderId, ShaderState.OUT);
        return true;
    }

    public static synchronized boolean disableShader(Identifier shaderId) {
        if (ACTIVE_SHADERS.remove(shaderId) == null) {
            return false;
        }

        ShaderContextManager.onShaderDisabled(shaderId);
        if (ACTIVE_SHADERS.isEmpty()) {
            ShaderTimeUniforms.onShaderDisabled();
        }
        return true;
    }

    public static synchronized void clear() {
        ACTIVE_SHADERS.clear();
        ShaderTimeUniforms.onShaderDisabled();
        ShaderContextManager.onShaderDisabled();
    }

    public static void tick() {
        List<Identifier> shadersToDisable = ShaderContextManager.getShadersReadyToDisable();
        for (Identifier shaderId : shadersToDisable) {
            disableShader(shaderId);
        }
    }

    public static void renderActiveShaders(MinecraftClient client, Pool pool) {
        List<ActiveShader> shaders;
        synchronized (ClientShaderManager.class) {
            shaders = new ArrayList<>(ACTIVE_SHADERS.values());
        }

        List<Identifier> failedShaders = new ArrayList<>();
        for (ActiveShader activeShader : shaders) {
            try {
                PostEffectProcessor postEffectProcessor = client.getShaderLoader()
                        .loadPostEffect(activeShader.renderId(), DefaultFramebufferSet.MAIN_ONLY);
                if (postEffectProcessor == null) {
                    failedShaders.add(activeShader.shaderId());
                    continue;
                }

                postEffectProcessor.render(client.getFramebuffer(), pool);
            } catch (Throwable ignored) {
                failedShaders.add(activeShader.shaderId());
            }
        }

        for (Identifier shaderId : failedShaders) {
            disableShader(shaderId);
        }
    }

    private static void addActiveShader(ActiveShader activeShader, boolean renderOnTop) {
        ACTIVE_SHADERS.remove(activeShader.shaderId());
        if (renderOnTop) {
            ACTIVE_SHADERS.put(activeShader.shaderId(), activeShader);
            return;
        }

        Map<Identifier, ActiveShader> reorderedShaders = new LinkedHashMap<>();
        reorderedShaders.put(activeShader.shaderId(), activeShader);
        reorderedShaders.putAll(ACTIVE_SHADERS);
        ACTIVE_SHADERS.clear();
        ACTIVE_SHADERS.putAll(reorderedShaders);
    }

    private static Identifier getRenderableShaderId(MinecraftClient client, Identifier shaderId) {
        if (client == null || client.getResourceManager() == null || shaderId == null) {
            return null;
        }

        if (hasShaderResource(client, shaderId)) {
            return shaderId;
        }
        if (shaderId.equals(CREEPER_CHAIN_NEW) && hasRawResource(client, CREEPER_CHAIN_OLD)) {
            return CREEPER_CHAIN_OLD;
        }
        return null;
    }

    private static boolean hasShaderResource(MinecraftClient client, Identifier shaderId) {
        try {
            return client.getResourceManager().getResource(POST_EFFECT_FINDER.toResourcePath(shaderId)).isPresent()
                    || hasRawResource(client, shaderId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasRawResource(MinecraftClient client, Identifier resourceId) {
        try {
            return client.getResourceManager().getResource(resourceId).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record ActiveShader(Identifier shaderId, Identifier renderId) {
    }
}
