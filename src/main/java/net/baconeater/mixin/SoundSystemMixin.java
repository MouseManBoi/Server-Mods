package net.baconeater.mixin;

import net.baconeater.features.commands.playsound.client.SoundTimeOffset;
import net.baconeater.features.commands.playsound.client.SkippingAudioStream;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    @Redirect(
            method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;",
                    ordinal = 1
            )
    )
    private CompletableFuture<Void> server_mods$skipStreamStart(
            CompletableFuture<AudioStream> future,
            Consumer<? super AudioStream> consumer,
            SoundInstance sound) {
        if (sound instanceof SoundTimeOffset offsetSound && offsetSound.server_mods$getOffsetSeconds() > 0.0F) {
            return future.thenAccept(stream -> consumer.accept(new SkippingAudioStream(stream, offsetSound.server_mods$getOffsetSeconds())));
        }
        return future.thenAccept(consumer);
    }

    @Inject(method = "method_19754", at = @At("TAIL"))
    private void server_mods$applySoundOffset(SoundInstance sound, Channel.SourceManager sourceManager, CallbackInfo ci) {
        if (sound instanceof SoundTimeOffset offsetSound && offsetSound.server_mods$getOffsetSeconds() > 0.0F) {
            sourceManager.run(source -> AL10.alSourcef(
                    ((SourceAccessor) source).server_mods$getPointer(),
                    AL11.AL_SEC_OFFSET,
                    offsetSound.server_mods$getOffsetSeconds()));
        }
    }
}
