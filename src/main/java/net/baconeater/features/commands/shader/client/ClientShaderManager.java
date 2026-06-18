package net.baconeater.features.commands.shader.client;

import net.baconeater.features.commands.shader.ShaderState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientShaderManager {
    private static final FileToIdConverter POST_EFFECT_FINDER = FileToIdConverter.json("post_effect");
    private static final Identifier CREEPER_CHAIN_NEW = Identifier.fromNamespaceAndPath("minecraft", "creeper");
    private static final Identifier CREEPER_CHAIN_OLD = Identifier.fromNamespaceAndPath("minecraft", "shaders/post/creeper.json");
    private static final Map<Identifier, ActiveShader> ACTIVE_SHADERS = new LinkedHashMap<>();

    private ClientShaderManager() {
    }

    public static synchronized boolean isActive(Identifier shaderId) {
        return ACTIVE_SHADERS.containsKey(shaderId);
    }

    public static synchronized boolean enableShader(
            Minecraft client,
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

    public static void renderActiveShaders(
            Minecraft client,
            RenderTarget mainRenderTarget,
            GraphicsResourceAllocator graphicsResourceAllocator) {
        List<ActiveShader> shaders;
        synchronized (ClientShaderManager.class) {
            shaders = new ArrayList<>(ACTIVE_SHADERS.values());
        }

        DomainDisplaySkinAtlasManager.renderRuntimeAnimation(client);

        List<Identifier> failedShaders = new ArrayList<>();
        for (ActiveShader activeShader : shaders) {
            try {
                PostChain postChain = client.getShaderManager()
                        .getPostChain(activeShader.renderId(), LevelTargetBundle.MAIN_TARGETS);
                if (postChain == null) {
                    failedShaders.add(activeShader.shaderId());
                    continue;
                }

                postChain.process(mainRenderTarget, graphicsResourceAllocator);
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

    private static Identifier getRenderableShaderId(Minecraft client, Identifier shaderId) {
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

    private static boolean hasShaderResource(Minecraft client, Identifier shaderId) {
        try {
            return client.getResourceManager().getResource(POST_EFFECT_FINDER.idToFile(shaderId)).isPresent()
                    || hasRawResource(client, shaderId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasRawResource(Minecraft client, Identifier resourceId) {
        try {
            return client.getResourceManager().getResource(resourceId).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record ActiveShader(Identifier shaderId, Identifier renderId) {
    }
}
