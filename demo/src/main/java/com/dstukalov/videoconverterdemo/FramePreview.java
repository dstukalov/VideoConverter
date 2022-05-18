package com.dstukalov.videoconverterdemo;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class FramePreview extends Thread {

    private static final String TAG = "frame-preview";

    private final String mFilePath;
    private final ImageView mThumbView;
    private long mFrameTime = -1;
    private final Object mLock = new Object();
    private final AtomicBoolean mRunning = new AtomicBoolean(true);

    FramePreview(@NonNull String filePath, @NonNull ImageView thumbView) {
        mFilePath = filePath;
        mThumbView = thumbView;
        setPriority(Thread.NORM_PRIORITY - 1);
        start();
    }

    public void requestShowFrame(long frameTime) {
        synchronized (mLock) {
            if (mFrameTime != frameTime) {
                Log.d(TAG, "request frame at " + frameTime);
                this.mFrameTime = frameTime;
                mLock.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(mFilePath);
        } catch (Exception ex) {
            Log.e(TAG, "failed to initialize MediaMetadataRetriever for " + mFilePath, ex);
            return;
        }
        try {
            long lastFrameTime = -1;

            //noinspection InfiniteLoopStatement
            while (true) {

                synchronized (mLock) {
                    if (mFrameTime == lastFrameTime) {
                        mLock.wait();
                    }
                    lastFrameTime = mFrameTime;
                }

                long t1 = System.currentTimeMillis();
                final Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(lastFrameTime * 1000);
                if (bitmap != null) {
                    Log.d(TAG, "got frame at " + mFrameTime + ", took " + (System.currentTimeMillis() - t1) + "ms");
                    mThumbView.post(() -> {
                        if (mRunning.get()) {
                            mThumbView.setImageBitmap(bitmap);
                        }
                    });
                }
            }

        } catch (InterruptedException e) {
            //allow thread to exit
        }
        mRunning.set(false);
        mediaMetadataRetriever.release();
    }
}
