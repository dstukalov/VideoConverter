package com.dstukalov.videoconverterdemo;

import android.app.Application;
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
        mLoadFilesRunnableFuture = mExecutor.submit(() -> mFiles.postValue(loadFiles()));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.i(TAG, "cancel");
        mLoadFilesRunnableFuture.cancel(true);
    }

    public @NonNull LiveData<List<File>> getFiles() {
        return mFiles;
    }

    public void deleteFile(@NonNull File file) {
        mExecutor.submit(() -> {
            if (!file.delete()) {
                Log.w(TAG, "failed to delete " + file.getAbsolutePath());
            }
            mFiles.postValue(loadFiles());
        });
    }

    @WorkerThread
    private @Nullable List<File> loadFiles() {
        final File [] filesArray = Objects.requireNonNull(getApplication().getExternalFilesDir(null)).listFiles(pathname -> pathname.getName().startsWith(Converter.CONVERTED_VIDEO_PREFIX));
        if (filesArray == null) {
            return null;
        }
        final List<File> filesList = Arrays.asList(filesArray);
        Collections.sort(filesList, (file1, file2) -> {
            final long t1 = file1.lastModified();
            final long t2 = file2.lastModified();
            return Long.compare(t2, t1);
        });
        return filesList;
    }
}
