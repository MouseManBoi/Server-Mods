package net.baconeater.mixin;

import net.baconeater.features.commands.shader.client.DomainDisplaySkinAtlasManager;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Mixin(targets = "net.minecraft.client.gl.ShaderLoader$Cache")
public abstract class ShaderLoaderCacheMixin {
    @Shadow
    @Final
    private Map<Identifier, Optional<PostChain>> postEffectProcessors;

    @Inject(method = "getOrLoadProcessor", at = @At("HEAD"))
    private void server$invalidateDomainPostEffect(
            Identifier id,
            Set<Identifier> availableExternalTargets,
            CallbackInfoReturnable<PostChain> cir) {
        if (!DomainDisplaySkinAtlasManager.shouldInvalidateCachedPostEffect(id)) {
            return;
        }

        Optional<PostChain> removed = postEffectProcessors.remove(id);
        removed.ifPresent(PostChain::close);
    }
}
