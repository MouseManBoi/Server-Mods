package net.baconeater.features.commands.playsound.client;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SkippingAudioStream implements AudioStream {
    private final AudioStream delegate;
    private long bytesToSkip;

    public SkippingAudioStream(AudioStream delegate, float seconds) {
        this.delegate = delegate;
        AudioFormat format = delegate.getFormat();
        int frameSize = Math.max(1, format.getFrameSize());
        long rawBytes = (long) (seconds * format.getFrameRate() * frameSize);
        this.bytesToSkip = rawBytes - rawBytes % frameSize;
    }

    @Override
    public AudioFormat getFormat() {
        return delegate.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        skipPendingBytes(size);
        return delegate.read(size);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private void skipPendingBytes(int chunkSize) throws IOException {
        while (bytesToSkip > 0) {
            int nextSize = (int) Math.min(bytesToSkip, Math.max(1, chunkSize));
            ByteBuffer skipped = delegate.read(nextSize);
            if (skipped == null) {
                bytesToSkip = 0;
                return;
            }
            bytesToSkip -= skipped.remaining();
        }
    }
}
