package net.baconeater.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MinecraftClientSoundMixin {
    @Redirect(
            method = "setWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundManager;stopAll()V"
            )
    )
    private void server_mods$keepSoundsOnDimensionChange(SoundManager soundManager, ClientWorld world) {
        if (world == null) {
            soundManager.stopAll();
        }
    }
}
