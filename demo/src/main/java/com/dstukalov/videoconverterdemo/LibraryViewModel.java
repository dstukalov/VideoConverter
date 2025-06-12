package com.dstukalov.videoconverterdemo;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LibraryViewModel extends AndroidViewModel {

    private static final String TAG = "LibraryViewModel";

    private final MutableLiveData<List<File>> mFiles = new MutableLiveData<>();
    private final Future<?> mLoadFilesRunnableFuture;
    private final ExecutorService mExecutor;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        mExecutor = Executors.newSingleThreadExecutor();
        // Load files when ViewModel is created
        mLoadFilesRunnableFuture = mExecutor.submit(() -> mFiles.postValue(loadFiles()));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.i(TAG, "cancel");
        mLoadFilesRunnableFuture.cancel(true);
        mExecutor.shutdownNow();
    }

    public @NonNull LiveData<List<File>> getFiles() {
        return mFiles;
    }

    // Call this method to refresh the list, for example, after a new conversion is done.
    public void refreshFiles() {
        mExecutor.submit(() -> mFiles.postValue(loadFiles()));
    }

    public void deleteFile(@NonNull File file) {
        mExecutor.submit(() -> {
            if (file.exists()) { // Check if file exists before attempting delete
                if (!file.delete()) {
                    Log.w(TAG, "failed to delete " + file.getAbsolutePath());
                } else {
                    Log.i(TAG, "Successfully deleted file: " + file.getAbsolutePath());
                    // Refresh the list after deletion
                    mFiles.postValue(loadFiles());
                }
            } else {
                Log.w(TAG, "Attempted to delete a non-existent file: " + file.getAbsolutePath());
                // Optionally refresh the list anyway, in case the UI state was somehow stale
                mFiles.postValue(loadFiles());
            }
        });
    }

    @WorkerThread
    private @Nullable List<File> loadFiles() {
        // Get the application context to access string resources
        Application application = getApplication();
        String subDirName;
        try {
            subDirName = application.getString(R.string.output_subdirectory_name);
        } catch (Exception e) {
            Log.e(TAG, "Could not load string resource for output subdirectory name. Falling back.", e);
            // Fallback in case string resource is missing, though it shouldn't be.
            subDirName = "Compressed-Videos"; // Or handle more gracefully
        }

        // Get the public Movies directory
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (moviesDir == null) {
            Log.e(TAG, "External Movies directory is not available.");
            return Collections.emptyList(); // Or null
        }

        // Construct the path to your specific output subdirectory
        File outputDir = new File(moviesDir, subDirName);

        Log.i(TAG, "Loading files from: " + outputDir.getAbsolutePath());

        if (!outputDir.exists() || !outputDir.isDirectory()) {
            Log.w(TAG, "Output directory does not exist or is not a directory: " + outputDir.getAbsolutePath());
            // If the directory doesn't exist, it means no converted files are there (yet).
            return Collections.emptyList(); // Return empty list if directory doesn't exist
        }

        final File[] filesArray = outputDir.listFiles(pathname ->
                pathname.isFile() && pathname.getName().contains(Converter.CONVERTED_VIDEO_PREFIX)
        );

        if (filesArray == null) {
            Log.e(TAG, "listFiles returned null for directory: " + outputDir.getAbsolutePath() +
                    ". This might indicate an I/O error or permission issue.");
            return Collections.emptyList(); // Or null, depending on how you want to handle this error
        }

        if (filesArray.length == 0) {
            Log.i(TAG, "No files found in " + outputDir.getAbsolutePath() + " matching the prefix.");
            return Collections.emptyList();
        }

        final List<File> filesList = Arrays.asList(filesArray);
        // Sort by last modified time, newest first
        Collections.sort(filesList, (file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
        Log.i(TAG, "Loaded " + filesList.size() + " files.");
        return filesList;
    }
}