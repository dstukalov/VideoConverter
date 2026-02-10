package com.dstukalov.videoconverterdemo;

import android.app.Application;
import android.content.ContentResolver;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

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

    static class LoadUriResult implements Parcelable {

        @Nullable File file;
        int width;
        int height;
        long duration;
        long fileLength;
        String mime;

        public static final Parcelable.Creator<LoadUriResult> CREATOR = new Parcelable.Creator<LoadUriResult>() {
            public LoadUriResult createFromParcel(Parcel in) {
                return new LoadUriResult(in);
            }

            public LoadUriResult[] newArray(int size) {
                return new LoadUriResult[size];
            }
        };

        boolean isOk() {
            return file != null && width > 0 && height > 0 && duration > 0 && fileLength > 0 && mime != null;
        }

        LoadUriResult() {
        }

        private LoadUriResult(Parcel in) {
            file = (File)in.readSerializable();
            width = in.readInt();
            height = in.readInt();
            duration = in.readLong();
            fileLength = in.readLong();
            mime = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeSerializable(file);
            dest.writeInt(width);
            dest.writeInt(height);
            dest.writeLong(duration);
            dest.writeLong(fileLength);
            dest.writeString(mime);
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

    @WorkerThread
    private @NonNull LoadUriResult loadUriInBackground(@NonNull Uri uri) throws InterruptedException {
        final LoadUriResult result = new LoadUriResult();
        if ("file".equals(uri.getScheme())) {
            result.file = new File(Objects.requireNonNull(uri.getPath()));
        } else {
            result.file = new File(getApplication().getExternalFilesDir(null), "tmp.mp4");
            //noinspection ResultOfMethodCallIgnored
            result.file.delete();
            final ContentResolver cr = getApplication().getContentResolver();
            if (cr != null) {
                try (InputStream inputStream = new BufferedInputStream(cr.openInputStream(uri)); OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(result.file))) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = inputStream.read(buffer, 0, buffer.length)) >= 0) {
                        outputStream.write(buffer, 0, n);
                    }
                } catch (InterruptedIOException e) {
                    throw new InterruptedException();
                } catch (SecurityException | IOException e) {
                    Log.w(TAG, "Unable to open stream", e);
                    result.file = null;
                }
            }
        }
        if (result.file != null) {
            final MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(result.file.getAbsolutePath());
            } catch (IOException e) {
                Log.w(TAG, "Unable get media meta", e);
                result.file = null;
            }
            if (result.file != null) {
                MediaFormat mediaFormat = null;
                for (int index = 0; index < extractor.getTrackCount(); ++index) {
                    String mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        extractor.selectTrack(index);
                        mediaFormat = extractor.getTrackFormat(index);
                        break;
                    }
                }
                if (mediaFormat != null) {
                    final int rotation = mediaFormat.containsKey(MediaFormat.KEY_ROTATION) ? mediaFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
                    result.width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                    result.height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    result.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION) / 1000L;
                    result.mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    if (rotation % 180 == 90) {
                        int tmp = result.width;
                        //noinspection SuspiciousNameCombination
                        result.width = result.height;
                        result.height = tmp;
                    }
                    result.fileLength = result.file.length();
                }
            }
            extractor.release();
        }
        Log.i(TAG, "result " + result.file + " " + result.mime + " " + result.width + "x" + result.height + " " + result.duration + "ms");
        return result;
    }
}
