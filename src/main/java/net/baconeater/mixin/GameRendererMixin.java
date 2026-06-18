package net.baconeater.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.baconeater.features.commands.shader.client.ClientShaderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft client;

    @Shadow
    @Final
    private GraphicsResourceAllocator GraphicsResourceAllocator;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                    ordinal = 0
            )
    )
    private void server$renderActiveCommandShaders(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RenderSystem.resetTextureMatrix();
        ClientShaderManager.renderActiveShaders(this.client, this.GraphicsResourceAllocator);
    }
}
