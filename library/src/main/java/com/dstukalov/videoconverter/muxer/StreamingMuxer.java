package com.dstukalov.videoconverter.muxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.dstukalov.videoconverter.Muxer;

import org.mp4parser.streaming.StreamingTrack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamingMuxer implements Muxer {

    private final OutputStream outputStream;
    private final Map<Integer, Track> tracks = new HashMap<>();
    private Mp4Writer mp4Writer;

    public StreamingMuxer(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void start() {
        final List<StreamingTrack> source = new ArrayList<>();
        for (Track track : tracks.values()) {
            source.add((StreamingTrack) track);
        }
        mp4Writer = new Mp4Writer(source, Channels.newChannel(outputStream));
    }

    @Override
    public void stop() throws IOException {
        for (Track track : tracks.values()) {
            track.close();
        }
        mp4Writer.close();
    }

    @Override
    public int addTrack(@NonNull MediaFormat format) throws IOException {

        final String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime.startsWith("video/")) {
            tracks.put(tracks.size(), new VideoTrack(format));
        } else if (mime.startsWith("audio/")) {
            tracks.put(tracks.size(), new AudioTrack(format));
        } else {
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

    interface Track {
        void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException;
        void close() throws IOException;
    }

    static class VideoTrack extends AvcTrack implements Track {

        VideoTrack(@NonNull MediaFormat format) throws IOException {
            final ByteBuffer cdsBuffer0 = format.getByteBuffer("csd-0");
            final byte [] cds0 = new byte [cdsBuffer0.capacity() - 4];
            cdsBuffer0.position(4);
            cdsBuffer0.get(cds0, 0, cds0.length);
            consumeNal(ByteBuffer.wrap(cds0), 0);

            final ByteBuffer cdsBuffer1 = format.getByteBuffer("csd-1");
            final byte [] cds1 = new byte [cdsBuffer1.capacity() - 4];
            cdsBuffer1.position(4);
            cdsBuffer1.get(cds1, 0, cds1.length);
            consumeNal(ByteBuffer.wrap(cds1), 0);

            setTimescale(90000);
            setFrametick(3000);
        }

        @Override
        public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
            final byte [] buffer = new byte[bufferInfo.size - 4];
            byteBuf.position(bufferInfo.offset + 4);
            byteBuf.get(buffer, 0, bufferInfo.size - 4);
            consumeNal(ByteBuffer.wrap(buffer), bufferInfo.presentationTimeUs);
        }

        @Override
        public void close() throws IOException {
            consumeLastNal();
        }
    }

    static class AudioTrack extends AacTrack implements Track {

        AudioTrack(@NonNull MediaFormat format) {
            super(format.getInteger(MediaFormat.KEY_BIT_RATE), format.getInteger(MediaFormat.KEY_BIT_RATE),
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    format.containsKey(MediaFormat.KEY_PROFILE) ? format.getInteger(MediaFormat.KEY_PROFILE) : MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        }

        @Override
        public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
            final byte [] buffer = new byte[bufferInfo.size];
            byteBuf.position(bufferInfo.offset);
            byteBuf.get(buffer, 0, bufferInfo.size);
            processSample(buffer);
        }

        @Override
        public void close() {
        }
    }
}
