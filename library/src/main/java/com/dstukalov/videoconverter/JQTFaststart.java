package com.dstukalov.videoconverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import androidx.annotation.NonNull;

/**
 * JQTFaststart
 * <p>
 * This utility classes implements a method to move the MOOV atom of a
 * Quicktime(TM) file to its front and adjusting its offsets accordingly. This
 * allows video players to start playing the video even if not the whole file is
 * yet available, e.g. while doing HTTP "streaming".
 * <p>
 * If the MOOV atom is found in the right place already, the original file is
 * simply copied.
 * <p>
 * This is a very simple Java port of Mike Melanson's
 * (http://multimedia.cx/eggs/) qt-faststart tool that comes with the ffmpeg
 * commandline tool (http://ffmpeg.mplayerhq.hu). This software has so far the
 * same limitations, such as not being able to handle files with compressed MOOV
 * atoms.
 * <p>
 * This class offers a static method for fast starting an QT style video file as
 * well as giving a simple command line interface that can be used as an example
 * on how to call the static method.
 * <p>
 * The original code by Mike Melanson (melanson@pcisys.net) was placed in the
 * public domain and so is this Java port of it.
 *
 * @author Axel Philipsenburg (axel@wired-space.de)
 */
public class JQTFaststart {

    // Atom FOURCC
    private static final String ATOM_FREE = "free";
    private static final String ATOM_JUNK = "junk";
    private static final String ATOM_MDAT = "mdat";
    private static final String ATOM_MOOV = "moov";
    private static final String ATOM_PNOT = "pnot";
    private static final String ATOM_SKIP = "skip";
    private static final String ATOM_WIDE = "wide";
    private static final String ATOM_PICT = "PICT";
    private static final String ATOM_FTYP = "ftyp";
    private static final String ATOM_CMOV = "cmov";
    private static final String ATOM_STCO = "stco";
    private static final String ATOM_CO64 = "co64";
    private static final String[] VALID_TOPLEVEL_ATOMS = {ATOM_FREE, ATOM_JUNK, ATOM_MDAT, ATOM_MOOV,
            ATOM_PNOT, ATOM_SKIP, ATOM_WIDE, ATOM_PICT,
            ATOM_FTYP};

    /**
     * This method takes an input file, parses its QT atom structure and creates
     * a copy of this file that will have its MOOV atom at the begin of the
     * file.
     *
     * @param inputFile  Input QT file.
     * @param outputFile Output QT file that will have the MOOV atom at the file's
     *                   front.
     * @throws IOException I/O errors or a malformed input file will cause an
     *                     IOException to be thrown.
     */
    public static void startFast(@NonNull File inputFile, @NonNull File outputFile) throws IOException {

        try (final RandomAccessFile input = new RandomAccessFile(inputFile, "r");
             final RandomAccessFile output = new RandomAccessFile(outputFile, "rw")) {

            Atom ah = null;
            Atom ftypAtom = null;

            boolean gotFtyp = false;
            boolean gotMdat = false;
            boolean inputIsStreamable = false;

            while (input.getFilePointer() < input.length()) {

                ah = new Atom(input);

                if (!isValidTopLevelAtom(ah)) {
                    throw new IOException("Non top level QT atom found. File invalid?");
                }

                if (gotFtyp && !gotMdat && ah.type.equalsIgnoreCase(ATOM_MOOV)) {
                    inputIsStreamable = true;
                    break;
                }

                // store ftyp atom to buffer
                if (ah.type.equalsIgnoreCase(ATOM_FTYP)) {
                    ftypAtom = ah;
                    ftypAtom.fillBuffer(input);
                    gotFtyp = true;
                } else if (ah.type.equalsIgnoreCase(ATOM_MDAT)) {
                    gotMdat = true;
                    input.skipBytes((int) ah.size);
                } else {
                    input.skipBytes((int) ah.size);
                }
            }

            if (ftypAtom == null) {
                throw new IOException("No n top level QT atom found. File invalid?");
            }

            if (inputIsStreamable) {
                copyFile(inputFile, outputFile);
                return;
            }

            if (!ah.type.equalsIgnoreCase(ATOM_MOOV)) {
                throw new IOException("Last QT atom was not the MOOV atom.");
            }

            input.seek(ah.offset);

            final Atom moovAtom = ah;
            moovAtom.fillBuffer(input);

            if (isCompressedMoovAtom(moovAtom)) {
                throw new IOException("Compressed MOOV qt atoms are not supported");
            }

            patchMoovAtom(moovAtom);

            output.write(ftypAtom.buffer);
            output.write(moovAtom.buffer);

            input.getChannel().transferTo(ftypAtom.offset + ftypAtom.size, moovAtom.offset - ftypAtom.offset - ftypAtom.size, output.getChannel());
        }
    }

    private static void copyFile(@NonNull File inputFile, @NonNull File outputFile) throws IOException {

        try (FileInputStream input = new FileInputStream(inputFile); FileOutputStream output = new FileOutputStream(outputFile)) {
            final FileChannel inputChannel = input.getChannel();
            final FileChannel outputChannel = output.getChannel();
            outputChannel.transferFrom(input.getChannel(), 0, inputChannel.size());
        }
    }

    private static boolean isCompressedMoovAtom(@NonNull Atom moovAtom) {

        return new String(Arrays.copyOfRange(moovAtom.buffer, 12, 15)).equalsIgnoreCase(ATOM_CMOV);
    }

    private static boolean isValidTopLevelAtom(@NonNull Atom ah) {

        for (String validAtom : VALID_TOPLEVEL_ATOMS) {
            if (validAtom.equalsIgnoreCase(ah.type)) {
                return true;
            }
        }
        return false;

    }

    private static void patchMoovAtom(@NonNull Atom moovAtom) {

        for (int idx = 4; idx < moovAtom.size - 4; idx++) {
            byte[] buffer = Arrays.copyOfRange(moovAtom.buffer, idx, idx + 4);
            if (new String(buffer).equalsIgnoreCase(ATOM_STCO)) {
                int stcoSize = patchStcoAtom(moovAtom, idx);
                idx += stcoSize - 4;
            } else if (new String(buffer).equalsIgnoreCase(ATOM_CO64)) {
                int co64Size = patchCo64Atom(moovAtom, idx);
                idx += co64Size - 4;
            }
        }

    }

    private static int patchStcoAtom(@NonNull Atom ah, int idx) {
        int stcoSize = (int) bytesToLong(Arrays.copyOfRange(ah.buffer, idx - 4, idx));

        int offsetCount = (int) bytesToLong(Arrays.copyOfRange(ah.buffer, idx + 8, idx + 12));
        for (int j = 0; j < offsetCount; j++) {
            int currentOffset = (int) bytesToLong(Arrays.copyOfRange(ah.buffer, idx + 12 + j * 4, (idx + 12 + j * 4) + 4));
            currentOffset += ah.size;
            int offsetIdx = idx + 12 + j * 4;
            ah.buffer[offsetIdx + 0] = (byte) ((currentOffset >> 24) & 0xFF);
            ah.buffer[offsetIdx + 1] = (byte) ((currentOffset >> 16) & 0xFF);
            ah.buffer[offsetIdx + 2] = (byte) ((currentOffset >> 8) & 0xFF);
            ah.buffer[offsetIdx + 3] = (byte) ((currentOffset >> 0) & 0xFF);
        }

        return stcoSize;
    }

    private static int patchCo64Atom(Atom ah, int idx) {
        int co64Size = (int) bytesToLong(Arrays.copyOfRange(ah.buffer, idx - 4, idx));

        int offsetCount = (int) bytesToLong(Arrays.copyOfRange(ah.buffer, idx + 8, idx + 12));
        for (int j = 0; j < offsetCount; j++) {
            long currentOffset = bytesToLong(Arrays.copyOfRange(ah.buffer, idx + 12 + j * 8, (idx + 12 + j * 8) + 8));
            currentOffset += ah.size;
            int offsetIdx = idx + 12 + j * 8;
            ah.buffer[offsetIdx + 0] = (byte) ((currentOffset >> 56) & 0xFF);
            ah.buffer[offsetIdx + 1] = (byte) ((currentOffset >> 48) & 0xFF);
            ah.buffer[offsetIdx + 2] = (byte) ((currentOffset >> 40) & 0xFF);
            ah.buffer[offsetIdx + 3] = (byte) ((currentOffset >> 32) & 0xFF);
            ah.buffer[offsetIdx + 4] = (byte) ((currentOffset >> 24) & 0xFF);
            ah.buffer[offsetIdx + 5] = (byte) ((currentOffset >> 16) & 0xFF);
            ah.buffer[offsetIdx + 6] = (byte) ((currentOffset >> 8) & 0xFF);
            ah.buffer[offsetIdx + 7] = (byte) ((currentOffset >> 0) & 0xFF);
        }

        return co64Size;
    }

    private static long bytesToLong(byte[] buffer) {

        long retVal = 0;

        for (int i = 0; i < buffer.length; i++) {
            retVal += ((buffer[i] & 0x00000000000000FF) << 8 * (buffer.length - i - 1));
        }

        return retVal;

    }

    static class Atom {

        public long offset;
        public long size;
        public String type;
        byte[] buffer = null;

        Atom(@NonNull RandomAccessFile input) throws IOException {
            offset = input.getFilePointer();
            // get atom size
            size = input.readInt();
            // get atom type
            byte[] atomTypeFCC = new byte[4];
            input.readFully(atomTypeFCC);
            type = new String(atomTypeFCC);
            if (size == 1) {
                // 64 bit size. Read new size from body and store it
                size = input.readLong();
            }
            // skip back to atom start
            input.seek(offset);
        }

        void fillBuffer(@NonNull RandomAccessFile input) throws IOException {
            buffer = new byte[(int) size];
            input.readFully(buffer);
        }
    }
}
