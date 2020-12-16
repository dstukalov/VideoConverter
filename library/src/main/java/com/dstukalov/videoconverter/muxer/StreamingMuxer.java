package com.dstukalov.videoconverter.muxer;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.dstukalov.videoconverter.Muxer;
import com.dstukalov.videoconverter.Preconditions;

import org.mp4parser.streaming.StreamingTrack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

public class StreamingMuxer implements Muxer {

    private final OutputStream outputStream;
    private final List<MediaCodecTrack> tracks = new ArrayList<>();
    private Mp4Writer mp4Writer;

    public StreamingMuxer(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void start() throws IOException {
        final List<StreamingTrack> source = new ArrayList<>();
        for (MediaCodecTrack track : tracks) {
            source.add((StreamingTrack) track);
        }
        mp4Writer = new Mp4Writer(source, Channels.newChannel(outputStream));
    }

    @Override
    public void stop() throws IOException {
        if (mp4Writer == null) {
            throw new IllegalStateException("calling stop prior to start");
        }
        for (MediaCodecTrack track : tracks) {
            track.finish();
        }
        mp4Writer.close();
        mp4Writer = null;
    }

    @Override
    public int addTrack(@NonNull MediaFormat format) throws IOException {
        final String mime = format.getString(MediaFormat.KEY_MIME);
        switch (Preconditions.checkNotNull(mime)) {
            case "video/avc":
                tracks.add(new MediaCodecAvcTrack(format));
                break;
            case "audio/mp4a-latm":
                tracks.add(new MediaCodecAacTrack(format));
                break;
            case "video/hevc":
                tracks.add(new MediaCodecHevcTrack(format));
                break;
            default:
                throw new IllegalArgumentException("unknown track format");
        }
        return tracks.size() - 1;
    }

    @Override
    public void writeSampleData(int trackIndex, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
        tracks.get(trackIndex).writeSampleData(byteBuf, bufferInfo);
    }

    @Override
    public void release() {
    }

    interface MediaCodecTrack {
        void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException;
        void finish() throws IOException;
    }

    static class MediaCodecAvcTrack extends AvcTrack implements MediaCodecTrack {

        MediaCodecAvcTrack(@NonNull MediaFormat format) {
            super(Utils.subBuffer(Preconditions.checkNotNull(format.getByteBuffer("csd-0")), 4),
                    Utils.subBuffer(Preconditions.checkNotNull(format.getByteBuffer("csd-1")), 4));
        }

        @Override
        public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
            final List<ByteBuffer> nals = Utils.getNals(byteBuf);
            for (ByteBuffer nal : nals) {
                consumeNal(Utils.clone(nal), bufferInfo.presentationTimeUs);
            }
        }

        @Override
        public void finish() throws IOException {
            consumeLastNal();
        }
    }

    static class MediaCodecHevcTrack extends HevcTrack implements MediaCodecTrack {

        MediaCodecHevcTrack(@NonNull MediaFormat format) throws IOException {
            super(Utils.getNals(Preconditions.checkNotNull(format.getByteBuffer("csd-0"))));
        }

        @Override
        public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
            final List<ByteBuffer> nals = Utils.getNals(byteBuf);
            for (ByteBuffer nal : nals) {
                consumeNal(Utils.clone(nal), bufferInfo.presentationTimeUs);
            }
        }

        @Override
        public void finish() throws IOException {
            consumeLastNal();
        }
    }

    static class MediaCodecAacTrack extends AacTrack implements MediaCodecTrack {

        MediaCodecAacTrack(@NonNull MediaFormat format) {
            super(format.getInteger(MediaFormat.KEY_BIT_RATE), format.getInteger(MediaFormat.KEY_BIT_RATE),
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    format.getInteger(MediaFormat.KEY_AAC_PROFILE));
        }

        @Override
        public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
            final byte [] buffer = new byte[bufferInfo.size];
            byteBuf.position(bufferInfo.offset);
            byteBuf.get(buffer, 0, bufferInfo.size);
            processSample(ByteBuffer.wrap(buffer));
        }

        @Override
        public void finish() {
        }
    }
}
