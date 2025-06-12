package com.dstukalov.videoconverterdemo;

// ... other necessary imports from your original Converter.java and my previous suggestions ...
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context; // Keep if Application context is used
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.dstukalov.videoconverter.BadMediaException;
import com.dstukalov.videoconverter.MediaConversionException;
import com.dstukalov.videoconverter.MediaConverter;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Converter {

    private static final String TAG_CONVERTER = "Converter";

    public static final String CONVERTED_VIDEO_PREFIX = "-1";

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

    private Converter(@NonNull Application application) { // Make constructor private for Singleton
        this.application = application;
    }

    @MainThread
    public void convert(@NonNull File inputFile,
                        @NonNull String targetOutputFileName,
                        long timeFrom, long timeTo, int videoResolution,
                        @NonNull @MediaConverter.VideoCodec String videoCodec,
                        int videoBitrate, int audioBitrate) {
        result.setValue(null);
        progress.postValue(new Progress(0, 0));

        convertRunnableFuture = executor.submit(() -> {
            Log.d(TAG_CONVERTER, "Conversion started for input: " + inputFile.getAbsolutePath());
            Log.d(TAG_CONVERTER, "Target output filename (received): " + targetOutputFileName);

            final long taskStartTime = System.currentTimeMillis();
            File finalOutputFileForNotification = null; // For the Result object if pre-Q
            Uri outputUriForNotification = null;        // For the Result object if Q+

            try {
                // The MediaConverter library likely needs a File object for output.
                // For Android 10+, we write to a temporary file first, then copy to MediaStore.
                // For pre-Android 10, we can write directly to the target location.

                File actualOutputFileForConverter; // The file path the MediaConverter library will write to.

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // On Q+, converter writes to a temp cache file, which is then copied to MediaStore.
                    // The 'targetOutputFileName' is used for the MediaStore display name.
                    actualOutputFileForConverter = new File(application.getCacheDir(), "temp_conversion_" + System.currentTimeMillis() + ".mp4");
                } else {
                    // Pre-Q, converter writes directly to the final public directory.
                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    String subDirName = application.getString(R.string.output_subdirectory_name);
                    File targetDir = new File(moviesDir, subDirName); // Your target subfolder
                    if (!targetDir.exists()) {
                        if (!targetDir.mkdirs()) {
                            Log.e(TAG_CONVERTER, "Failed to create directory: " + targetDir.getAbsolutePath());
                            result.postValue(new Result(new IOException("Failed to create output directory: " + targetDir.getAbsolutePath())));
                            return; // Exit runnable
                        }
                    }
                    actualOutputFileForConverter = new File(targetDir, targetOutputFileName);
                    finalOutputFileForNotification = actualOutputFileForConverter; // This will be the final file
                }

                Log.d(TAG_CONVERTER, "MediaConverter will write to: " + actualOutputFileForConverter.getAbsolutePath());

                final MediaConverter converter = new MediaConverter();
                converter.setInput(inputFile);
                converter.setOutput(actualOutputFileForConverter); // MediaConverter writes here
                converter.setTimeRange(timeFrom, timeTo);
                converter.setVideoResolution(videoResolution);
                converter.setVideoCodec(videoCodec);
                converter.setVideoBitrate(videoBitrate);
                converter.setAudioBitrate(audioBitrate);

                final long conversionLogicStartTime = System.currentTimeMillis();
                converter.setListener(percent -> {
                    progress.postValue(new Progress(percent, System.currentTimeMillis() - conversionLogicStartTime));
                    return convertRunnableFuture != null && convertRunnableFuture.isCancelled();
                });

                converter.convert(); // This is the actual conversion call

                if (convertRunnableFuture != null && convertRunnableFuture.isCancelled()) {
                    result.postValue(new Result()); // Cancelled, no error, but no file
                    if (actualOutputFileForConverter.exists()) {
                        actualOutputFileForConverter.delete(); // Clean up temp file if cancelled
                    }
                    return; // Exit runnable
                }

                // If conversion successful:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Copy the temporary converted file to MediaStore
                    ContentResolver resolver = application.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, targetOutputFileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                    String subDirName = application.getString(R.string.output_subdirectory_name);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + File.separator + subDirName);                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                    Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    Uri itemUri = null;
                    OutputStream os = null;

                    try {
                        itemUri = resolver.insert(collection, values);
                        if (itemUri == null) {
                            throw new IOException("Failed to create new MediaStore entry for " + targetOutputFileName);
                        }
                        os = resolver.openOutputStream(itemUri);
                        if (os == null) {
                            throw new IOException("Failed to get output stream for MediaStore URI: " + itemUri);
                        }
                        copyFileToOutputStream(actualOutputFileForConverter, os); // Helper method
                        os.flush();

                        values.clear();
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        resolver.update(itemUri, values, null, null);
                        outputUriForNotification = itemUri;
                        Log.i(TAG_CONVERTER, "Video copied to MediaStore: " + itemUri.toString());
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {
                                Log.e(TAG_CONVERTER, "Error closing MediaStore output stream", e);
                            }
                        }
                        if (actualOutputFileForConverter.exists()) {
                            actualOutputFileForConverter.delete(); // Delete temp file after copy
                        }
                    }
                }
                // For pre-Q, actualOutputFileForConverter is already the final file (finalOutputFileForNotification)

                long elapsedTime = System.currentTimeMillis() - taskStartTime;
                if (outputUriForNotification != null) { // Q+
                    result.postValue(new Result(outputUriForNotification, targetOutputFileName, elapsedTime, application));
                } else if (finalOutputFileForNotification != null) { // Pre-Q
                    result.postValue(new Result(finalOutputFileForNotification, elapsedTime));
                } else {
                    // Should not happen if conversion was successful and not cancelled
                    Log.e(TAG_CONVERTER, "Conversion reported success, but no output file/URI was set.");
                    result.postValue(new Result(new IOException()));
                }

            } catch (BadMediaException | IOException | MediaConversionException e) {
                Log.e(TAG_CONVERTER, "Failed to convert video", e);
                result.postValue(new Result(e));
            } catch (Exception e) { // Catch any other unexpected exceptions
                Log.e(TAG_CONVERTER, "Unexpected error during conversion task", e);
                result.postValue(new Result(e));
            }
            finally {
                progress.postValue(null); // Clear progress
                // convertRunnableFuture is set to null by the caller or when it finishes naturally
            }
        });
    }

    // Helper method to copy file content (ensure this is in the class or accessible)
    private void copyFileToOutputStream(File inputFile, OutputStream outputStream) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(inputFile);
            byte[] buffer = new byte[8 * 1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    Log.e(TAG_CONVERTER, "Error closing input stream during copy", e);
                }
            }
            // Do not close outputStream here; it's managed by the MediaStore block
        }
    }


    @MainThread
    public void reset() {
        cancel(); // Good practice to cancel any ongoing conversion on reset
        result.setValue(null);
        progress.setValue(null); // Also clear progress
    }

    public boolean isConverted() {
        Result currentResult = result.getValue();
        return currentResult != null && (currentResult.file != null || currentResult.uri != null) && currentResult.exception == null;
    }

    @Nullable
    public File getConvertedFile() { // This might need adjustment if primarily using URI post Q
        Result currentResult = result.getValue();
        if (currentResult != null && currentResult.file != null) {
            return currentResult.file;
        }
        return null;
    }

    @Nullable
    public Uri getConvertedUri() { // Add this for Q+
        Result currentResult = result.getValue();
        if (currentResult != null && currentResult.uri != null) {
            return currentResult.uri;
        }
        return null;
    }


    public boolean isConverting() {
        return progress.getValue() != null;
    }

    public void cancel() {
        if (convertRunnableFuture != null && !convertRunnableFuture.isDone()) {
            Log.d(TAG_CONVERTER, "Attempting to cancel conversion.");
            convertRunnableFuture.cancel(true); // Attempt to interrupt if listener checks isCancelled
        }
    }

    // --- Progress Class (remains the same) ---
    public static class Progress {
        // ... (as before)
        final int percent;
        final long elapsedTime;

        public Progress(int percent, long elapsedTime) {
            this.percent = percent;
            this.elapsedTime = elapsedTime;
        }
    }

    // --- Result Class (needs slight modification to handle Uri for Q+) ---
    public static class Result {
        @Nullable public File file; // For pre-Q or if you want to expose the temp file path before copy on Q+
        @Nullable public Uri uri;    // For Q+ MediaStore URI
        @Nullable public String displayName; // The actual filename used in MediaStore or file system

        public int width;
        public int height;
        public long duration;
        public long fileLength; // Length of the final file
        public long elapsedTime;
        @Nullable public Exception exception;

        // Constructor for cancellation or general empty result
        Result() {
        }

        // Constructor for pre-Q success (File output)
        Result(@NonNull File file, long elapsedTime) {
            this.file = file;
            this.displayName = file.getName();
            this.elapsedTime = elapsedTime;
            this.fileLength = file.length();
            extractMetadata(file.getAbsolutePath());
        }

        // Constructor for Q+ success (Uri output)
        Result(@NonNull Uri uri, @NonNull String displayName, long elapsedTime, @NonNull Context context) {
            this.uri = uri;
            this.displayName = displayName;
            this.elapsedTime = elapsedTime;

            try (Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                    if (sizeIndex != -1) {
                        this.fileLength = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG_CONVERTER, "Unable to get file size from URI: " + uri, e);
            }
            extractMetadataFromUri(uri, context);
        }


        Result(@NonNull Exception exception) {
            this.exception = exception;
        }

        private void extractMetadata(String path) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(path);
                populateMetadata(mmr);
            } catch (Exception e) {
                Log.w(TAG_CONVERTER, "Unable to get media meta from path: " + path, e);
            } finally {
                try {
                    mmr.release();
                } catch (IOException e) {
                    Log.e(TAG_CONVERTER, "Error releasing MediaMetadataRetriever", e);
                }
            }
        }

        private void extractMetadataFromUri(Uri mediaUri, Context context) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(context, mediaUri);
                populateMetadata(mmr);
            } catch (Exception e) {
                Log.w(TAG_CONVERTER, "Unable to get media meta from URI: " + mediaUri, e);
            } finally {
                try {
                    mmr.release();
                } catch (IOException e) {
                    Log.e(TAG_CONVERTER, "Error releasing MediaMetadataRetriever", e);
                }
            }
        }

        private void populateMetadata(MediaMetadataRetriever mmr) {
            width = Integer.parseInt(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
            height = Integer.parseInt(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
            duration = Long.parseLong(Objects.requireNonNull(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
            String rotationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotationStr != null) {
                int rotation = Integer.parseInt(rotationStr);
                if (rotation % 180 == 90) { // Check for 90 or 270
                    int tmp = width;
                    //noinspection SuspiciousNameCombination
                    width = height;
                    height = tmp;
                }
            }
        }
    }
}