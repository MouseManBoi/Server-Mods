package net.baconeater.features.commands.playsound.client;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public class OffsetPositionedSoundInstance extends PositionedSoundInstance implements SoundTimeOffset {
    private final float offsetSeconds;

    public OffsetPositionedSoundInstance(
            Identifier id,
            SoundCategory category,
            float volume,
            float pitch,
            Random random,
            SoundInstance.AttenuationType attenuationType,
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
