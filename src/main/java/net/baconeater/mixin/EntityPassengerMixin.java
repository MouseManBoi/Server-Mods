package net.baconeater.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.event.GameEvent;
import net.minecraft.advancement.criterion.Criteria;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPassengerMixin {
    @Shadow
    public abstract Entity getVehicle();

    @Shadow
    public abstract boolean hasVehicle();

    @Shadow
    public abstract void stopRiding();

    @Shadow
    public abstract void setPose(EntityPose pose);

    @Shadow
    public abstract net.minecraft.world.World getEntityWorld();

    @Shadow
    public abstract java.util.stream.Stream<Entity> streamSelfAndPassengers();

    @Shadow
    public abstract net.minecraft.util.math.Vec3d getEntityPos();

    @Inject(method = "couldAcceptPassenger", at = @At("HEAD"), cancellable = true)
    private void allowPlayersToSupportPassengers(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void allowAnythingToRidePlayers(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "startRiding(Lnet/minecraft/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void allowMountingPlayers(Entity vehicle, boolean force, boolean emitEvent, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!force || self.getEntityWorld().isClient() || !(vehicle instanceof PlayerEntity)) {
            return;
        }

        if (vehicle == this.getVehicle()) {
            cir.setReturnValue(false);
            return;
        }

        for (Entity current = vehicle; current.getVehicle() != null; current = current.getVehicle()) {
            if (current.getVehicle() == self) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (this.hasVehicle()) {
            this.stopRiding();
        }

        this.setPose(EntityPose.STANDING);
        entityPassengerMixin$setVehicle(self, vehicle);
        ((EntityInvoker) vehicle).servermods$addPassenger(self);
        vehicle.updatePassengerPosition(self);
        ((ServerWorld) self.getEntityWorld()).getChunkManager().sendToNearbyPlayers(vehicle, new EntityPassengersSetS2CPacket(vehicle));
        if (emitEvent) {
            this.getEntityWorld().emitGameEvent(self, GameEvent.ENTITY_MOUNT, vehicle.getEntityPos());
            vehicle.streamSelfAndPassengers()
                    .filter(passenger -> passenger instanceof ServerPlayerEntity)
                    .forEach(passenger -> Criteria.STARTED_RIDING.trigger((ServerPlayerEntity) passenger));
        }

        cir.setReturnValue(true);
    }

    @Unique
    private static void entityPassengerMixin$setVehicle(Entity rider, Entity vehicle) {
        ((EntityAccessor) rider).servermods$setVehicle(vehicle);
    }
}
