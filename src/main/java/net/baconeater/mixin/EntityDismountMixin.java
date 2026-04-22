package net.baconeater.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDismountMixin {
    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void syncPlayerPassengerRemoval(Entity passenger, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof PlayerEntity) || self.getEntityWorld().isClient()) {
            return;
        }

        ((ServerWorld) self.getEntityWorld()).getChunkManager().sendToNearbyPlayers(self, new EntityPassengersSetS2CPacket(self));
    }
}
