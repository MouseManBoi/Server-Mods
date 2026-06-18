package net.baconeater.features.commands.playsound.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record PlaySoundOffsetPayload(
        Identifier soundId,
        SoundSource category,
        Vec3 pos,
        float volume,
        float pitch,
        long seed,
        int seconds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlaySoundOffsetPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("server", "playsound_offset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaySoundOffsetPayload> CODEC = new StreamCodec<>() {
        @Override
        public PlaySoundOffsetPayload decode(RegistryFriendlyByteBuf buf) {
            Identifier soundId = buf.readIdentifier();
            SoundSource category = buf.readEnum(SoundSource.class);
            Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float volume = buf.readFloat();
            float pitch = buf.readFloat();
            long seed = buf.readLong();
            int seconds = buf.readVarInt();
            return new PlaySoundOffsetPayload(soundId, category, pos, volume, pitch, seed, seconds);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlaySoundOffsetPayload value) {
            buf.writeIdentifier(value.soundId());
            buf.writeEnum(value.category());
            buf.writeDouble(value.pos().x);
            buf.writeDouble(value.pos().y);
            buf.writeDouble(value.pos().z);
            buf.writeFloat(value.volume());
            buf.writeFloat(value.pitch());
            buf.writeLong(value.seed());
            buf.writeVarInt(value.seconds());
        }
    };

    @Override
    public Type<PlaySoundOffsetPayload> type() {
        return TYPE;
    }
}
