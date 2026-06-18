package net.baconeater.features.commands.playsound.client;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

public class OffsetPositionedSoundInstance extends SimpleSoundInstance implements SoundTimeOffset {
    private final float offsetSeconds;

    public OffsetPositionedSoundInstance(
            Identifier id,
            SoundSource category,
            float volume,
            float pitch,
            RandomSource random,
            SoundInstance.Attenuation attenuationType,
            double x,
            double y,
            double z,
            float offsetSeconds) {
        super(id, category, volume, pitch, random, false, 0, attenuationType, x, y, z, false);
        this.offsetSeconds = offsetSeconds;
    }

    @Override
    public float server_mods$getOffsetSeconds() {
        return offsetSeconds;
    }
}
