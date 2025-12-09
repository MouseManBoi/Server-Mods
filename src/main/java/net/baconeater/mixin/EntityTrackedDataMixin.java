package net.baconeater.mixin;

import net.baconeater.features.commands.visibility.client.ClientVisibilityManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityTrackedDataMixin {
    @Inject(method = "onTrackedDataSet", at = @At("HEAD"))
    private void server$reapplyHiddenVisibility(TrackedData<?> trackedData, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (ClientVisibilityManager.isHidden(self.getId())) {
            ClientVisibilityManager.reapplyHide(self);
        }
    }
}