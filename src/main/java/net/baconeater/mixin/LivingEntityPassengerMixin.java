package net.baconeater.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPassengerMixin {
    @Inject(method = "getPassengerRidingPos", at = @At("HEAD"), cancellable = true)
    private void usePlayerOriginAsPassengerAttachment(Entity passenger, CallbackInfoReturnable<Vec3d> cir) {
        if ((Object) this instanceof PlayerEntity) {
            cir.setReturnValue(((Entity) (Object) this).getEntityPos());
        }
    }
}
