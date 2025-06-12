package com.dstukalov.videoconverterdemo;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "LoadUriViewModel";

    private final SavedStateHandle savedStateHandle;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<LoadUriResult> loadUriResultLiveData = new MutableLiveData<>();
    private Future<?> loadUriRunnableFuture;

    public static class LoadUriResult implements Parcelable { // Implement Parcelable if needed for SavedStateHandle
        @Nullable
        public File file; // The accessible File object (direct or cached copy)
        @Nullable
        public Uri originalUri;
        @Nullable
        public String originalFileName;
        public int width;
        public int height;
        public long duration;
        public long fileLength;
        @Nullable
        public String error;

        public static final Creator<LoadUriResult> CREATOR = new Creator<LoadUriResult>() {
            @Override
            public LoadUriResult createFromParcel(Parcel in) {
                return new LoadUriResult(in);
            }

            @Override
            public LoadUriResult[] newArray(int size) {
                return new LoadUriResult[size];
            }
        };

        public boolean isOk() {
            return file != null && error == null && width > 0 && height > 0 && duration > 0;
        }

        public LoadUriResult() {
        } // Default constructor

        // Parcelable implementation
        protected LoadUriResult(Parcel in) {
            file = (File) in.readSerializable();
            originalUri = in.readParcelable(Uri.class.getClassLoader());
            originalFileName = in.readString();
            width = in.readInt();
            height = in.readInt();
            duration = in.readLong();
            fileLength = in.readLong();
            error = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeSerializable(file);
            dest.writeParcelable(originalUri, flags);
            dest.writeString(originalFileName);
            dest.writeInt(width);
            dest.writeInt(height);
            dest.writeLong(duration);
            dest.writeLong(fileLength);
            dest.writeString(error);
        }

        @Override
        public int describeContents() {
            return 0;
        }

    }

    public MainViewModel(@NonNull Application application, SavedStateHandle savedStateHandle) {
        super(application);
        this.savedStateHandle = savedStateHandle;
        LoadUriResult result = savedStateHandle.get("result");
        if (result != null) {
            Log.i(TAG, "restore from saved state");
            loadUriResultLiveData.setValue(result);
        }
    }

    public void loadUri(@NonNull Uri uri) {
        Log.i(TAG, "start");
        if (loadUriRunnableFuture != null) {
            loadUriRunnableFuture.cancel(true);
        }
        loadUriRunnableFuture = executor.submit(() -> {
            LoadUriResult result;
            try {
                result = loadUriInBackground(uri);
            } catch (InterruptedException e) {
                Log.i(TAG, "interrupted");
                result = new LoadUriResult();
            } catch (Throwable e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    throw e;
                });
                result = new LoadUriResult();
            }
            loadUriResultLiveData.postValue(result);
            savedStateHandle.set("result", result);
        });
    }

    public boolean isUriLoaded() {
        return loadUriResultLiveData.getValue() != null && loadUriResultLiveData.getValue().isOk();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.i(TAG, "cancel");
        if (loadUriRunnableFuture != null) {
            loadUriRunnableFuture.cancel(true);
        }
    }

    public @NonNull LiveData<LoadUriResult> getLoadUriResultLiveData() {
        return loadUriResultLiveData;
    }

    public @Nullable File getLoadedFile() {
        final LoadUriResult result = loadUriResultLiveData.getValue();
        return result == null ? null : result.file;
    }

    @Nullable
    public static String getFileNameFromUri(@NonNull Context context, @NonNull Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            fileName = uri.getLastPathSegment();
        } else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error getting display name for content URI: " + uri, e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        // Fallback if still null from content resolver, or for other schemes.
        if (fileName == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    fileName = path.substring(cut + 1);
                } else {
                    fileName = path; // If no slash, path itself might be the name
                }
            }
        }
        // If still null, generate a fallback. A null name is problematic.
        if (fileName == null || fileName.isEmpty()) {
            fileName = "unknown_video_" + System.currentTimeMillis(); // Or handle as an error upstream
            Log.w(TAG, "Could not determine original file name for URI: " + uri + ", using fallback: " + fileName);
        }
        return fileName;
    }

    @Nullable
    private static File copyContentUriToMoviesTemp(@NonNull Context context, @NonNull Uri uri, @NonNull String targetFileNameInMovies) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        File outputFile = null;

        // Get the subdirectory name from strings.xml
        // Assuming R.string.output_subdirectory_name is "Compressed-Videos"
        String subDirNameFromResource = context.getString(R.string.output_subdirectory_name);

        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for content URI: " + uri);
                return null;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

                // **Step 1: Query and Delete Existing File with the Same Name in the Target Directory**
                String selection = MediaStore.Video.Media.DISPLAY_NAME + "=? AND " +
                        MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
                // Note: RELATIVE_PATH needs trailing slash for exact directory match with LIKE
                String targetRelativePathForQuery = Environment.DIRECTORY_MOVIES + File.separator + subDirNameFromResource + File.separator;
                String[] selectionArgs = { targetFileNameInMovies, targetRelativePathForQuery };

                Cursor cursor = resolver.query(collection, new String[]{MediaStore.Video.Media._ID}, selection, selectionArgs, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        try {
                            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                            Uri existingItemUri = Uri.withAppendedPath(collection, String.valueOf(id));
                            int deletedRows = resolver.delete(existingItemUri, null, null);
                            if (deletedRows > 0) {
                                Log.i(TAG, "Deleted existing MediaStore entry: " + existingItemUri + " for overwrite.");
                            } else {
                                Log.w(TAG, "Failed to delete existing MediaStore entry or it was already gone: " + existingItemUri);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error deleting existing MediaStore item URI during overwrite attempt: " + targetFileNameInMovies, e);
                            // Optional: Decide if you want to abort or continue if deletion fails.
                            // If deletion fails, MediaStore might still create "tmp (1).mp4".
                        }
                    }
                    cursor.close();
                } else {
                    Log.w(TAG, "MediaStore query for existing file returned null cursor: " + targetFileNameInMovies);
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileNameInMovies);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                // For insert, RELATIVE_PATH does not need a trailing slash
                String targetRelativePathForInsert = Environment.DIRECTORY_MOVIES + File.separator + subDirNameFromResource;
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePathForInsert);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri itemUri = resolver.insert(collection, values);

                if (itemUri == null) {
                    Log.e(TAG, "Failed to create new MediaStore entry for: " + targetFileNameInMovies + " after delete attempt.");
                    return null;
                }

                outputStream = resolver.openOutputStream(itemUri);
                if (outputStream == null) {
                    Log.e(TAG, "Failed to get output stream for MediaStore URI: " + itemUri);
                    resolver.delete(itemUri, null, null); // Clean up the newly created pending entry
                    return null;
                }

                byte[] buffer = new byte[8 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(itemUri, values, null, null);
                Log.i(TAG, "Successfully copied (with overwrite logic) to MediaStore: " + itemUri.toString());

                String path = getPathFromMediaStoreUri(context, itemUri);
                if (path != null) {
                    outputFile = new File(path);
                    Log.i(TAG, "MediaStore path (Q+) after overwrite: " + path);
                } else {
                    Log.w(TAG, "Could not get a direct path for MediaStore URI (Q+) after overwrite: " + itemUri + ". The File object might not be directly usable by path.");
                }

            } else { // Pre-Android Q (FileOutputStream inherently overwrites)
                File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                File targetSubDir = new File(moviesDir, subDirNameFromResource);
                if (!targetSubDir.exists()) {
                    if (!targetSubDir.mkdirs()) {
                        Log.e(TAG, "Failed to create directory: " + targetSubDir.getAbsolutePath());
                        return null;
                    }
                }
                outputFile = new File(targetSubDir, targetFileNameInMovies);
                // FileOutputStream by default truncates and overwrites if the file exists.
                outputStream = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                Log.i(TAG, "Successfully copied (with overwrite) to (pre-Q): " + outputFile.getAbsolutePath());
            }
            return outputFile;

        } catch (IOException e) {
            Log.e(TAG, "Error copying content URI to Movies folder: " + uri + " as " + targetFileNameInMovies, e);
            if (outputFile != null && outputFile.exists() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams after copying to Movies folder", e);
            }
        }
    }

    // Helper to attempt to get a path from a MediaStore URI (use with caution)
    @Nullable
    private static String getPathFromMediaStoreUri(@NonNull Context context, @NonNull Uri uri) {
        Cursor cursor = null;
        try {
            // This projection is common for MediaStore URIs.
            String[] projection = {MediaStore.MediaColumns.DATA};
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                String path = cursor.getString(columnIndex);
                if (path != null) {
                    return path;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting path from MediaStore URI: " + uri, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Nullable
    public static String getPathFromContentUri(@NonNull Context context, @NonNull Uri contentUri) {
        if (ContentResolver.SCHEME_FILE.equals(contentUri.getScheme())) { // Already a file URI
            return contentUri.getPath();
        }

        Log.w(TAG, "getPathFromContentUri: Direct path resolution for content:// URIs is unreliable and often not possible. Prefer copying for content URIs.");

        return null; // Default to null, indicating direct path not found/reliable.
    }


    @WorkerThread
    private @NonNull LoadUriResult loadUriInBackground(@NonNull Uri uri) throws InterruptedException {
        final LoadUriResult result = new LoadUriResult();
        result.originalUri = uri;
        result.originalFileName = getFileNameFromUri(getApplication(), uri); // Preserves original name
        Log.i(TAG, "ViewModel loading URI: " + uri.toString() + ". Original Filename: " + result.originalFileName);

        String scheme = uri.getScheme();

        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            Log.i(TAG, "Processing file:// URI directly.");
            String path = uri.getPath();
            if (path != null) {
                result.file = new File(path);
                if (!result.file.exists() || !result.file.canRead()) {
                    Log.e(TAG, "File URI error: File does not exist or cannot be read: " + path);
                    result.error = "File scheme: File not found or not readable.";
                    result.file = null;
                } else {
                    Log.i(TAG, "Successfully prepared direct file access for file://: " + result.file.getAbsolutePath());
                    if (result.originalFileName == null && result.file != null) {
                        result.originalFileName = result.file.getName();
                    }
                }
            } else {
                Log.e(TAG, "File URI error: Path is null.");
                result.error = "File scheme: Invalid URI (null path).";
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            Log.i(TAG, "Processing content:// URI. Will copy to Movies/Compressed-Videos/tmp.mp4");

            // The old getPathFromContentUri is unreliable and not needed if we always copy for content URIs.
            // We will directly use our new copying method.
            // String directPath = getPathFromContentUri(getApplication(), uri); // Less reliable
            // if (directPath != null && new File(directPath).canRead()) { ... }

            // Always copy content URIs to the designated public location for this use case.
            // Use a fixed name "tmp.mp4" as requested for the file in the public directory.
            String targetFileNameInMovies = "tmp.mp4";
            result.file = copyContentUriToMoviesTemp(getApplication(), uri, targetFileNameInMovies);

            if (result.file == null || !result.file.exists()) { // Check if copy failed or file is not accessible
                result.error = "Content scheme: Failed to copy video to " + Environment.DIRECTORY_MOVIES + "/Compressed-Videos/" + targetFileNameInMovies;
                Log.e(TAG, result.error + " (URI: " + uri + ")");
                result.file = null; // Ensure file is null on error
            } else {
                Log.i(TAG, "Successfully copied content:// to public file: " + result.file.getAbsolutePath());
                // result.originalFileName is already set from getFileNameFromUri earlier.
                // If the copied file name ("tmp.mp4") needs to be stored distinctly from originalFileName,
                // LoadUriResult would need another field. For now, result.file points to "tmp.mp4".
            }

        } else {
            Log.e(TAG, "Unsupported URI scheme: " + scheme);
            result.error = "Unsupported URI scheme: " + scheme;
        }

        // Metadata Extraction (only if result.file is valid and accessible)
        if (result.file != null && result.file.exists() && result.file.canRead()) { // Added canRead() check
            result.fileLength = result.file.length();
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                // For Android Q+ and MediaStore URIs, using a FileDescriptor is safer if available.
                // If result.file.getAbsolutePath() doesn't work for MediaStore URIs (common on Q+ if it's not a real path),
                // you'd need to open a FileDescriptor from the original content URI or the new MediaStore URI.
                // This example assumes getPathFromMediaStoreUri gave a usable path or it's pre-Q.
                retriever.setDataSource(result.file.getAbsolutePath());
                // ... (rest of metadata extraction remains the same) ...
                String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

                if (widthStr != null && heightStr != null && durationStr != null) {
                    result.width = Integer.parseInt(widthStr);
                    result.height = Integer.parseInt(heightStr);
                    result.duration = Long.parseLong(durationStr);
                    int rotation = 0;
                    if (rotationStr != null) {
                        try {
                            rotation = Integer.parseInt(rotationStr);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid rotation value: " + rotationStr);
                        }
                    }
                    if (rotation == 90 || rotation == 270) {
                        int temp = result.width;
                        result.width = result.height;
                        result.height = temp;
                    }
                    Log.i(TAG, "Metadata: " + result.width + "x" + result.height + ", " + result.duration + "ms, Rotation: " + rotation);
                } else {
                    result.error = (result.error == null ? "" : result.error + " ") + "Failed to extract some video metadata components.";
                    result.file = null;
                }
            } catch (Exception e) { // Catch broader exceptions for retriever
                Log.e(TAG, "Failed to extract metadata", e);
                result.error = (result.error == null ? "" : result.error + " ") + "Corrupted or unreadable video metadata.";
                result.file = null;
            } finally {
                try {
                    retriever.release();
                } catch (IOException e) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
                }
            }
        } else {
            if (result.file != null && (!result.file.exists() || !result.file.canRead())) {
                Log.e(TAG, "File object exists but file system entry does not exist or cannot be read: " + result.file.getAbsolutePath());
            }
            if (result.error == null) { // If no error was set before, but file is not usable
                result.error = "Video file could not be accessed or prepared for metadata extraction.";
            }
            Log.e(TAG, "Final check: File is null, does not exist, or not readable. Error: " + result.error);
            result.file = null; // Ensure file is null if metadata extraction failed or file is bad
        }
        return result;
    }
}