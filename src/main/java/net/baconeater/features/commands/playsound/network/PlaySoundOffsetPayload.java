package net.baconeater.features.commands.playsound.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record PlaySoundOffsetPayload(
        Identifier soundId,
        SoundCategory category,
        Vec3d pos,
        float volume,
        float pitch,
        long seed,
        int seconds) implements CustomPayload {
    public static final CustomPayload.Id<PlaySoundOffsetPayload> ID =
            new CustomPayload.Id<>(Identifier.of("server", "playsound_offset"));

    public static final PacketCodec<RegistryByteBuf, PlaySoundOffsetPayload> CODEC = new PacketCodec<>() {
        @Override
        public PlaySoundOffsetPayload decode(RegistryByteBuf buf) {
            Identifier soundId = buf.readIdentifier();
            SoundCategory category = buf.readEnumConstant(SoundCategory.class);
            Vec3d pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float volume = buf.readFloat();
            float pitch = buf.readFloat();
            long seed = buf.readLong();
            int seconds = buf.readVarInt();
            return new PlaySoundOffsetPayload(soundId, category, pos, volume, pitch, seed, seconds);
        }

        @Override
        public void encode(RegistryByteBuf buf, PlaySoundOffsetPayload value) {
            buf.writeIdentifier(value.soundId());
            buf.writeEnumConstant(value.category());
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
    public Id<PlaySoundOffsetPayload> getId() {
        return ID;
    }
}
