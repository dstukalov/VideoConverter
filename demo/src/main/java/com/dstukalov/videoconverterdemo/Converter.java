package com.dstukalov.videoconverterdemo;

import android.app.Application;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.dstukalov.videoconverter.BadMediaException;
import com.dstukalov.videoconverter.MediaConversionException;
import com.dstukalov.videoconverter.MediaConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Converter {

    private static final String TAG = "Converter";

    private static volatile Converter INSTANCE;

    private final Application application;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> convertRunnableFuture;

    public final MutableLiveData<Progress> progress = new MutableLiveData<>();
    public final MutableLiveData<Result> result = new MutableLiveData<>();

    public static Converter getInstance(@NonNull Application application) {
        if (INSTANCE == null) {
            synchronized (Converter.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Converter(application);
                }
            }
        }
        return INSTANCE;
    }

    private Converter(@NonNull Application application) {
        this.application = application;
    }

    @MainThread
    public void convert(@NonNull File input, @NonNull String outputFileName, long timeFrom, long timeTo, int videoResolution,
                        @NonNull @MediaConverter.VideoCodec String videoCodec, int videoBitrate, int audioBitrate) {
        result.setValue(null);
        progress.postValue(new Progress(0, 0));
        convertRunnableFuture = executor.submit(() -> {
            try {
                final File output = new File(application.getExternalFilesDir(null), outputFileName);
                final MediaConverter converter = new MediaConverter();
                converter.setInput(input);
                converter.setOutput(output);
                converter.setTimeRange(timeFrom, timeTo);
                converter.setVideoResolution(videoResolution);
                try {
                    converter.setVideoCodec(videoCodec);
                } catch (FileNotFoundException e) {
                    result.postValue(new Result(e));
                    return;
                }
                converter.setVideoBitrate(videoBitrate);
                converter.setAudioBitrate(audioBitrate);

                final long startTime = System.currentTimeMillis();
                converter.setListener(percent -> {
                    progress.postValue(new Progress(percent, System.currentTimeMillis() - startTime));
                    return convertRunnableFuture.isCancelled();
                });

                try {
                    converter.convert();
                    if (convertRunnableFuture.isCancelled()) {
                        result.postValue(new Result());
                    } else {
                        result.postValue(new Result(output, System.currentTimeMillis() - startTime));
                    }
                } catch (BadMediaException | IOException | MediaConversionException e) {
                    Log.e(TAG, "failed to convert", e);
                    result.postValue(new Result(e));
                }
            } finally {
                progress.postValue(null);
                convertRunnableFuture = null;
            }
        });
    }

    @MainThread
    public void reset() {
        result.setValue(null);
    }

    public boolean isConverted() {
        return result.getValue() != null && result.getValue().file != null;
    }

    public File getConvertFile() {
        return Objects.requireNonNull(result.getValue()).file;
    }

    public boolean isConverting() {
        return progress.getValue() != null;
    }

    public void cancel() {
        if (convertRunnableFuture != null) {
            convertRunnableFuture.cancel(false);
        }
    }

    public static class Progress {
        final int percent;
        final long elapsedTime;

        public Progress(int percent, long elapsedTime) {
            this.percent = percent;
            this.elapsedTime = elapsedTime;
        }
    }

    public static class Result {
        @Nullable File file;
        int width;
        int height;
        long duration;
        long fileLength;
        public long elapsedTime;
        @Nullable Exception exception;

        Result() {
        }

        Result(@NonNull File file, long elapsedTime) {
            this.file = file;
            this.elapsedTime = elapsedTime;
            this.fileLength = file.length();
            final MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(file.getAbsolutePath());
                width = Integer.parseInt(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
                height = Integer.parseInt(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
                duration = Long.parseLong(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
                int rotation = Integer.parseInt(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)));
                if (rotation % 180 == 90) {
                    int tmp = width;
                    //noinspection SuspiciousNameCombination
                    width = height;
                    height = tmp;
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable get media meta", e);
            }
            try {
                mmr.release();
            } catch (IOException e) {
                Log.w(TAG, "Unable release MediaMetadataRetriever", e);
            }
        }

        Result(@NonNull Exception exception) {
            this.exception = exception;
        }
    }
}
