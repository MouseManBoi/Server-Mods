package net.baconeater.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.baconeater.features.commands.shader.client.ShaderTimeUniforms;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.Handle;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(PostEffectPass.class)
public abstract class PostEffectPassMixin {
    @Shadow
    @Final
    private String id;

    @Shadow
    @Final
    private Map<String, GpuBuffer> uniformBuffers;

    @Inject(method = "render", at = @At("HEAD"))
    private void server$updateTimeUniforms(
            FrameGraphBuilder builder,
            Map<Identifier, Handle<Framebuffer>> handles,
            GpuBufferSlice slice,
            CallbackInfo ci) {
        ShaderTimeUniforms.updateTimeUniforms(this.uniformBuffers, this.id);
    }
}
