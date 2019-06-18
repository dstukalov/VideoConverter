package com.dstukalov.videoconverter;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Muxer {

    void start();

    void stop() throws IOException;

    int addTrack(@NonNull MediaFormat format) throws IOException;

    void writeSampleData(int trackIndex, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException;

    void release();
}
