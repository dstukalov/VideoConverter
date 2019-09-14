package com.dstukalov.videoconverterdemo;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.util.Log;

import androidx.annotation.NonNull;

import com.dstukalov.videoconverter.OutputSurface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VideoThumbnailsExtractor {

    private static final String TAG = "video-thumbnails";

    interface Callback {
        boolean publishProgress(int index, Bitmap thumbnail);
    }

    public static void extractThumbnails(
            final @NonNull String videoFilePath,
            final int thumbnailCount,
            final int thumbnailResolution,
            final @NonNull Callback callback) {

        final MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        OutputSurface outputSurface = null;
        try {
            extractor.setDataSource(videoFilePath);
            MediaFormat mediaFormat = null;
            for (int index = 0; index < extractor.getTrackCount(); ++index) {
                if (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    extractor.selectTrack(index);
                    mediaFormat = extractor.getTrackFormat(index);
                    break;
                }
            }
            if (mediaFormat != null) {
                final String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                final int rotation = mediaFormat.containsKey(MediaFormat.KEY_ROTATION) ? mediaFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
                final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                final int outputWidth;
                final int outputHeight;
                if (width < height) {
                    outputWidth = thumbnailResolution;
                    outputHeight = height * outputWidth / width;
                } else {
                    outputHeight = thumbnailResolution;
                    outputWidth = width * outputHeight / height;
                }
                final int outputWidthRotated;
                final int outputHeightRotated;
                if ((rotation % 180 == 90)) {
                    //noinspection SuspiciousNameCombination
                    outputWidthRotated = outputHeight;
                    //noinspection SuspiciousNameCombination
                    outputHeightRotated = outputWidth;
                } else {
                    outputWidthRotated = outputWidth;
                    outputHeightRotated = outputHeight;
                }

                Log.i(TAG, "video: " + width + "x" + height + " " + rotation);
                Log.i(TAG, "output: " + outputWidthRotated + "x" + outputHeightRotated);

                outputSurface = new OutputSurface(outputWidthRotated, outputHeightRotated);

                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(mediaFormat, outputSurface.getSurface(), null, 0);
                decoder.start();
                doExtract(extractor, decoder, outputSurface, outputWidthRotated, outputHeightRotated, mediaFormat.getLong(MediaFormat.KEY_DURATION), thumbnailCount, callback);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            extractor.release();
        }
    }

    private static void doExtract(
            final @NonNull MediaExtractor extractor,
            final @NonNull MediaCodec decoder,
            final @NonNull OutputSurface outputSurface,
            final int outputWidth, int outputHeight, long duration, int thumbnailCount,
            final @NonNull Callback callback) {

        final int TIMEOUT_USEC = 10000;
        final ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int samplesExtracted = 0;
        int thumbnailsCreated = 0;

        Log.i(TAG, "doExtract started");
        final ByteBuffer pixelBuf = ByteBuffer.allocateDirect(outputWidth * outputHeight * 4);
        pixelBuf.order(ByteOrder.LITTLE_ENDIAN);

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    final ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    final int sampleSize = extractor.readSampleData(inputBuf, 0);
                    if (sampleSize < 0 || samplesExtracted >= thumbnailCount) {
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        Log.i(TAG, "input done");
                    } else {
                        final long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0 /*flags*/);
                        samplesExtracted++;
                        extractor.seekTo(duration * samplesExtracted / thumbnailCount, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        Log.i(TAG, "seek to " + duration * samplesExtracted / thumbnailCount + ", actual " + extractor.getSampleTime());
                    }
                }
            }

            int outputBufIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
            if (outputBufIndex >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }

                final boolean shouldRender = (info.size != 0) /*&& (info.presentationTimeUs >= duration * decodeCount / mThumbnailCount)*/;

                decoder.releaseOutputBuffer(outputBufIndex, shouldRender);
                if (shouldRender) {
                    outputSurface.awaitNewImage();
                    outputSurface.drawImage();

                    if (thumbnailsCreated < thumbnailCount) {
                        pixelBuf.rewind();
                        GLES20.glReadPixels(0, 0, outputWidth, outputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);

                        final Bitmap bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
                        pixelBuf.rewind();
                        bitmap.copyPixelsFromBuffer(pixelBuf);

                        if (!callback.publishProgress(thumbnailsCreated, bitmap)) {
                            break;
                        }
                        Log.i(TAG, "publishProgress for frame " + thumbnailsCreated + " at " + info.presentationTimeUs + " (target " + duration * thumbnailsCreated / thumbnailCount + ")" );
                    }
                    thumbnailsCreated++;
                }
            }
        }
        Log.i(TAG, "doExtract finished");
    }
}
