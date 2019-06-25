package com.dstukalov.videoconverter.muxer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mp4parser.muxer.MemoryDataSourceImpl;
import org.mp4parser.muxer.tracks.AbstractH26XTrack;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class Utils {

    static byte[] toArray(final @NonNull ByteBuffer buf) {
        final ByteBuffer newBuf = buf.duplicate();
        byte[] bytes = new byte[newBuf.remaining()];
        newBuf.get(bytes, 0, bytes.length);
        return bytes;
    }

    static @NonNull ByteBuffer subBuffer(final @NonNull ByteBuffer buf, final int start) {
        return subBuffer(buf, start, buf.remaining() - start);
    }

    static @NonNull ByteBuffer subBuffer(final @NonNull ByteBuffer buf, final int start, final int count) {
        final ByteBuffer newBuf = buf.duplicate();
        byte[] bytes = new byte[count];
        newBuf.position(start);
        newBuf.get(bytes, 0, bytes.length);
        return ByteBuffer.wrap(bytes);
    }

    static @NonNull List<ByteBuffer> getNals(ByteBuffer buffer) throws IOException {
        final MemoryDataSourceImpl dataSource = new MemoryDataSourceImpl(buffer);
        final AbstractH26XTrack.LookAhead lookAhead = new AbstractH26XTrack.LookAhead(dataSource);
        final List<ByteBuffer> nals = new ArrayList<>();
        ByteBuffer nal;
        while ((nal = findNextNal(lookAhead)) != null) {
            nals.add(nal);
        }
        return nals;
    }

    private static @Nullable ByteBuffer findNextNal(final @NonNull AbstractH26XTrack.LookAhead la) throws IOException {
        try {
            while (!la.nextThreeEquals001()) {
                la.discardByte();
            }
            la.discardNext3AndMarkStart();

            while (!la.nextThreeEquals000or001orEof(true)) {
                la.discardByte();
            }
            return la.getNal();
        } catch (EOFException e) {
            return null;
        }
    }

}
