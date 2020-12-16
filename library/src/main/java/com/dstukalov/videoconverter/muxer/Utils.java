package com.dstukalov.videoconverter.muxer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mp4parser.muxer.MemoryDataSourceImpl;
import org.mp4parser.muxer.tracks.AbstractH26XTrack;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

class Utils {

    static @NonNull byte[] toArray(final @NonNull ByteBuffer buf) {
        final ByteBuffer newBuf = buf.duplicate();
        byte[] bytes = new byte[newBuf.remaining()];
        newBuf.get(bytes, 0, bytes.length);
        return bytes;
    }

    public static @NonNull ByteBuffer clone(final @NonNull ByteBuffer original) {
        final ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
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

    static @NonNull List<ByteBuffer> getNals(final @NonNull ByteBuffer buffer) {
        final List<ByteBuffer> nals = new ArrayList<>();
        ByteBuffer nal;
        while ((nal = nextNALUnit(buffer)) != null) {
            nals.add(nal);
        }
        return nals;
    }

    public static @Nullable ByteBuffer nextNALUnit(final @NonNull ByteBuffer buf) {
        skipToNALUnit(buf);
        return gotoNALUnit(buf);
    }

    public static void skipToNALUnit(final @NonNull ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            return;
        }
        int val = 0xffffffff;
        while (buf.hasRemaining()) {
            val <<= 8;
            val |= (buf.get() & 0xff);
            if ((val & 0xffffff) == 1) {
                buf.position(buf.position());
                break;
            }
        }
    }

    /**
     * Finds next Nth H.264 bitstream NAL unit (0x00000001) and returns the data
     * that preceeds it as a ByteBuffer slice
     *
     * Segment byte order is always little endian
     *
     * TODO: emulation prevention
     *
     * @param buf
     * @return
     */
    public static @Nullable ByteBuffer gotoNALUnit(final @NonNull ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            return null;
        }
        int from = buf.position();
        ByteBuffer result = buf.slice();
        result.order(ByteOrder.BIG_ENDIAN);

        int val = 0xffffffff;
        while (buf.hasRemaining()) {
            val <<= 8;
            val |= (buf.get() & 0xff);
            if ((val & 0xffffff) == 1) {
                buf.position(buf.position() - (val == 1 ? 4 : 3));
                result.limit(buf.position() - from);
                break;
            }
        }
        return result;
    }
}
