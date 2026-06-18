package net.baconeater.mixin;

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.baconeater.features.commands.playsound.network.PlaySoundOffsetPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.PlaySoundCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(PlaySoundCommand.class)
public class PlaySoundCommandMixin {
    private static final DynamicCommandExceptionType CANNOT_SEND_EXCEPTION =
            new DynamicCommandExceptionType(player -> Text.literal("Cannot play an offset sound for " + player + " because their client does not have this mod's playsound channel."));

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void server_mods$executeOffsetSound(
            ServerCommandSource source,
            Collection<ServerPlayer> targets,
            Identifier sound,
            SoundCategory category,
            Vec3d pos,
            float volume,
            float pitch,
            float seconds,
            CallbackInfoReturnable<Integer> cir) throws Exception {
        if (seconds <= 0.0F) {
            return;
        }

        PlaySoundOffsetPayload payload = new PlaySoundOffsetPayload(
                sound,
                category,
                pos,
                volume,
                pitch,
                source.getWorld().getRandom().nextLong(),
                Math.round(seconds)
        );

        for (ServerPlayer target : targets) {
            if (!ServerPlayNetworking.canSend(target, PlaySoundOffsetPayload.ID)) {
                throw CANNOT_SEND_EXCEPTION.create(target.getName().getString());
            }
            ServerPlayNetworking.send(target, payload);
        }

        if (targets.size() == 1) {
            ServerPlayer target = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.playsound.success.single", Text.literal(sound.toString()), target.getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.playsound.success.multiple", Text.literal(sound.toString()), targets.size()), true);
        }

        cir.setReturnValue(targets.size());
    }
}
