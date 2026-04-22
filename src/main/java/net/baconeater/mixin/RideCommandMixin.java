package net.baconeater.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.RideCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RideCommand.class)
public abstract class RideCommandMixin {
    @Shadow
    @Final
    private static Dynamic2CommandExceptionType ALREADY_RIDING_EXCEPTION;

    @Shadow
    @Final
    private static Dynamic2CommandExceptionType GENERIC_FAILURE_EXCEPTION;

    @Shadow
    @Final
    private static SimpleCommandExceptionType RIDE_LOOP_EXCEPTION;

    @Shadow
    @Final
    private static SimpleCommandExceptionType WRONG_DIMENSION_EXCEPTION;

    @Inject(method = "executeMount", at = @At("HEAD"), cancellable = true)
    private static void allowPlayersAsVehicles(
            ServerCommandSource source,
            Entity rider,
            Entity vehicle,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        Entity currentVehicle = rider.getVehicle();
        if (currentVehicle != null) {
            throw ALREADY_RIDING_EXCEPTION.create(rider.getDisplayName(), currentVehicle.getDisplayName());
        }

        if (rider.streamSelfAndPassengers().anyMatch(passenger -> passenger == vehicle)) {
            throw RIDE_LOOP_EXCEPTION.create();
        }

        if (rider.getEntityWorld() != vehicle.getEntityWorld()) {
            throw WRONG_DIMENSION_EXCEPTION.create();
        }

        if (!rider.startRiding(vehicle, true, true)) {
            throw GENERIC_FAILURE_EXCEPTION.create(rider.getDisplayName(), vehicle.getDisplayName());
        }

        source.sendFeedback(() -> Text.translatable("commands.ride.mount.success", rider.getDisplayName(), vehicle.getDisplayName()), true);
        cir.setReturnValue(1);
    }
}
