package net.baconeater.mixin;

import net.baconeater.features.commands.attack.AttackState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {
    @Inject(method = "sendEntityStatus", at = @At("HEAD"), cancellable = true)
    private void server$skipDisabledHurtTint(Entity entity, byte status, CallbackInfo ci) {
        if (status == 2 && AttackState.isHurtTintDisabled(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendEntityDamage", at = @At("HEAD"), cancellable = true)
    private void server$skipDisabledDamageTint(Entity entity, DamageSource damageSource, CallbackInfo ci) {
        if (AttackState.isHurtTintDisabled(entity)) {
            ci.cancel();
        }
    }
}
