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

    private static final String TAG = "USER_LoadUriViewModel";

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

    @WorkerThread
    private @NonNull LoadUriResult loadUriInBackground(@NonNull Uri uri) throws InterruptedException {
        final LoadUriResult result = new LoadUriResult();
        result.originalUri = uri;
        result.originalFileName = getFileNameFromUri(getApplication(), uri); // Retained
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
                    result.file = null; // Nullify if not valid
                } else {
                    Log.i(TAG, "Successfully prepared direct file access for file://: " + result.file.getAbsolutePath());
                    // originalFileName is already set, but if it was null for some reason:
                    if (result.originalFileName == null) result.originalFileName = result.file.getName();
                }
            } else {
                Log.e(TAG, "File URI error: Path is null.");
                result.error = "File scheme: Invalid URI (null path).";
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            Log.i(TAG, "Processing content:// URI. Attempting to get direct path.");
            result.file = null; // Initialize to null

            // Attempt to get a direct path using the existing helper method
            String directPath = getPathFromContentUri(getApplication(), uri);

            if (directPath != null) {
                Log.i(TAG, "Attempting to use direct path from content URI: " + directPath);
                File potentialFile = new File(directPath);
                if (potentialFile.exists() && potentialFile.canRead()) {
                    result.file = potentialFile;
                    Log.i(TAG, "Successfully obtained readable direct file path: " + result.file.getAbsolutePath());
                    // originalFileName is already set, but if it was null for some reason:
                    if (result.originalFileName == null) result.originalFileName = result.file.getName();
                } else {
                    Log.w(TAG, "Direct path obtained (" + directPath + ") but file doesn't exist or not readable. Will fall back to URI.");
                    // result.file remains null, will use URI for metadata
                }
            } else {
                Log.w(TAG, "Could not obtain a direct path for content URI. Will use URI directly for metadata.");
                // result.file remains null, will use URI for metadata
            }

            if (result.file == null && result.originalUri == null) { // Should not happen with originalUri
                result.error = "Content scheme: Original URI is null and no direct path obtained.";
                Log.e(TAG, result.error + " (URI: " + uri + ")");
            } else if (result.file == null) {
                Log.i(TAG, "Content URI will be used directly for metadata (no valid direct path): " + result.originalUri.toString());
            }

        } else {
            Log.e(TAG, "Unsupported URI scheme: " + scheme);
            result.error = "Unsupported URI scheme: " + scheme;
        }

        // --- Metadata Extraction ---
        // This part now intelligently chooses between result.file (if available)
        // and result.originalUri (if result.file is null, especially for content URIs)

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        android.os.ParcelFileDescriptor pfd = null; // Declare pfd here to close in finally

        try {
            if (result.file != null && result.file.exists() && result.file.canRead()) {
                // This path is for FILE URIs OR if we successfully got a *readable direct path* from a CONTENT URI
                Log.i(TAG, "Extracting metadata using file path: " + result.file.getAbsolutePath());
                result.fileLength = result.file.length();
                retriever.setDataSource(result.file.getAbsolutePath());
            } else if (result.originalUri != null && ContentResolver.SCHEME_CONTENT.equals(result.originalUri.getScheme())) {
                // Fallback for CONTENT URIs if direct path wasn't available or readable
                Log.i(TAG, "Extracting metadata using content URI and FileDescriptor: " + result.originalUri.toString());
                pfd = getApplication().getContentResolver().openFileDescriptor(result.originalUri, "r");
                if (pfd != null) {
                    retriever.setDataSource(pfd.getFileDescriptor());
                    Cursor cursor = getApplication().getContentResolver().query(result.originalUri, new String[]{MediaStore.MediaColumns.SIZE}, null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                int sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                                    result.fileLength = cursor.getLong(sizeIndex);
                                } else {
                                    Log.w(TAG, "Size column not found or is null for content URI: " + result.originalUri);
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                } else {
                    throw new IOException("Failed to open ParcelFileDescriptor for URI: " + result.originalUri);
                }
            } else if (result.originalUri != null && ContentResolver.SCHEME_FILE.equals(result.originalUri.getScheme()) && result.file == null) {
                Log.e(TAG, "Metadata extraction skipped: File URI was invalid and result.file is null.");
                if (result.error == null) result.error = "File URI was invalid for metadata extraction.";
                throw new IOException(result.error);
            } else { // No valid source for retriever
                if (result.error == null) result.error = "No valid source (file or content URI) for metadata extraction.";
                throw new IOException(result.error != null ? result.error : "Unknown error before metadata extraction.");
            }

            // Common metadata extraction logic
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
                Log.i(TAG, "Metadata: " + result.width + "x" + result.height + ", " + result.duration + "ms, Rotation: " + rotation + ", Length: " + result.fileLength);
                // If we got here, metadata extraction was successful.
                // Clear any "soft" error that might have been set if we fell back from direct path to URI.
                if (result.error != null && (result.error.contains("Direct path obtained") || result.error.contains("Could not obtain a direct path"))) {
                    result.error = null;
                }
            } else {
                String metaError = "Failed to extract some video metadata components.";
                result.error = (result.error == null ? metaError : result.error + ". " + metaError);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract metadata or access URI", e);
            String extractionError = "Corrupted or unreadable video data/metadata";
            if (e instanceof SecurityException) {
                extractionError = "Permission denied accessing video content: " + e.getMessage();
            } else if (e.getMessage() != null && e.getMessage().startsWith("Failed to open ParcelFileDescriptor")) {
                extractionError = "Could not open video content: " + e.getMessage();
            } else if (e.getMessage() != null) {
                extractionError += ": " + e.getMessage();
            }
            result.error = (result.error == null ? extractionError : result.error + ". " + extractionError);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) { // As of API 30, release() can throw IOException
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e);
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing ParcelFileDescriptor", e);
                }
            }
        }

        if (result.error != null) {
            Log.e(TAG, "Final check: LoadUriInBackground completed with error: " + result.error);
            // If an error occurred and we had successfully assigned a direct file path to result.file,
            // but that file then failed metadata extraction, result.file still points to it.
            // The calling code MUST check result.error first.
        } else if (result.file == null && result.originalUri == null) {
            // This case should ideally be caught earlier and result.error set.
            // It indicates an issue with URI processing that wasn't properly flagged.
            result.error = "Unknown error: No valid file or URI after processing.";
            Log.e(TAG, result.error);
        }


        return result;
    }

    // Ensure getPathFromContentUri is present in your MainViewModel.java
// It was already in your provided code, but for completeness:
    @Nullable
    public static String getPathFromContentUri(@NonNull Context context, @NonNull Uri contentUri) {
        if (ContentResolver.SCHEME_FILE.equals(contentUri.getScheme())) {
            return contentUri.getPath();
        }

        // This is the part that is unreliable and often returns null or an unusable path
        // for many content URIs, especially on newer Android versions or from SAF.
        Cursor cursor = null;
        try {
            // Try common columns for file path
            String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Video.Media.DATA, MediaStore.Audio.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                // Try MediaStore.Video.Media.DATA first as it's a video app
                int dataColumnIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                if (dataColumnIndex != -1 && !cursor.isNull(dataColumnIndex)) {
                    return cursor.getString(dataColumnIndex);
                }
                // Try MediaStore.Images.Media.DATA (sometimes used for general media)
                dataColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                if (dataColumnIndex != -1 && !cursor.isNull(dataColumnIndex)) {
                    return cursor.getString(dataColumnIndex);
                }
                // Try MediaStore.Audio.Media.DATA
                dataColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                if (dataColumnIndex != -1 && !cursor.isNull(dataColumnIndex)) {
                    return cursor.getString(dataColumnIndex);
                }
                // If specific columns are not found, iterate through all columns to find "_data"
                // This is a more desperate attempt.
                for (String columnName : cursor.getColumnNames()) {
                    if ("_data".equalsIgnoreCase(columnName)) {
                        int genericDataColumnIndex = cursor.getColumnIndex(columnName);
                        if (genericDataColumnIndex != -1 && !cursor.isNull(genericDataColumnIndex)) {
                            String path = cursor.getString(genericDataColumnIndex);
                            if (path != null && !path.isEmpty()) {
                                Log.w(TAG, "Found path using generic '_data' column: " + path);
                                return path;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error trying to get path from content URI: " + contentUri, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Log.w(TAG, "getPathFromContentUri: Direct path resolution failed for content:// URI: " + contentUri + ". This is common. Prefer using the URI directly with ContentResolver methods.");
        return null; // Default to null, indicating direct path not found/reliable.
    }
}