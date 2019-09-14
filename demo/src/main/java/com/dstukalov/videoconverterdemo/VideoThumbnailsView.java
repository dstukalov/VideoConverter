package com.dstukalov.videoconverterdemo;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VideoThumbnailsView extends View {

    private static final String TAG = "video-thumbnails";

    private String mVideoFilePath;
    private ArrayList<Bitmap> mThumbnails;
    private AsyncTask<Void, Bitmap, Void> mThumbnailsTask;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTmpRect = new RectF();
    private final Rect mDrawRect = new Rect();
    private final Rect mTmpDrawRect = new Rect();

    public VideoThumbnailsView(final Context context) {
        super(context);
    }

    public VideoThumbnailsView(final Context context, final @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoThumbnailsView(final Context context, final @Nullable AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public VideoThumbnailsView(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setVideoFile(final @Nullable String videoFilePath) {
        mVideoFilePath = videoFilePath;
        mThumbnails = null;
        if (mThumbnailsTask != null) {
            mThumbnailsTask.cancel(true);
            mThumbnailsTask = null;
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mThumbnails = null;
        if (mThumbnailsTask != null) {
            mThumbnailsTask.cancel(true);
            mThumbnailsTask = null;
        }
   }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        if (mVideoFilePath == null) {
            return;
        }

        mTmpDrawRect.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getBottom() - getPaddingBottom());

        if (!mDrawRect.equals(mTmpDrawRect)) {
            mDrawRect.set(mTmpDrawRect);
            mThumbnails = null;
            if (mThumbnailsTask != null) {
                mThumbnailsTask.cancel(true);
                mThumbnailsTask = null;
            }
        }

        if (mThumbnails == null) {
            if (mThumbnailsTask == null) {
                final int thumbnailCount = mDrawRect.width() / mDrawRect.height();
                final float thumbnailWidth = (float) mDrawRect.width() / thumbnailCount;
                final float thumbnailHeight = mDrawRect.height();
                mThumbnails = new ArrayList<>(thumbnailCount);
                mThumbnailsTask = new ThumbnailsTask(this, mVideoFilePath, thumbnailWidth, thumbnailHeight, thumbnailCount);
                mThumbnailsTask.execute();
            }
        } else {
            final int thumbnailCount = mDrawRect.width() / mDrawRect.height();
            final float thumbnailWidth = (float) mDrawRect.width() / thumbnailCount;
            final float thumbnailHeight = mDrawRect.height();
            mTmpRect.top = mDrawRect.top;
            mTmpRect.bottom = mDrawRect.bottom;
            for (int i = 0; i < mThumbnails.size(); i++) {
                mTmpRect.left = mDrawRect.left + i * thumbnailWidth;
                mTmpRect.right = mTmpRect.left + thumbnailWidth;
                final Bitmap thumbnailBitmap = mThumbnails.get(i);
                if (thumbnailBitmap != null) {
                    canvas.save();
                    canvas.rotate(180, mTmpRect.centerX(), mTmpRect.centerY());
                    mTmpDrawRect.set(0, 0, thumbnailBitmap.getWidth(), thumbnailBitmap.getHeight());
                    if (mTmpDrawRect.width() * thumbnailHeight > mTmpDrawRect.height() * thumbnailWidth) {
                        float w = mTmpDrawRect.height() * thumbnailWidth / thumbnailHeight;
                        mTmpDrawRect.left = mTmpDrawRect.centerX() - (int)(w / 2);
                        mTmpDrawRect.right = mTmpDrawRect.left + (int)w;
                    } else {
                        float h = mTmpDrawRect.width() * thumbnailHeight / thumbnailWidth;
                        mTmpDrawRect.top = mTmpDrawRect.centerY() - (int)(h / 2);
                        mTmpDrawRect.bottom = mTmpDrawRect.top + (int)h;
                    }
                    canvas.drawBitmap(thumbnailBitmap, mTmpDrawRect, mTmpRect, mPaint);
                    canvas.restore();
                }
            }
        }
    }

    private static class ThumbnailsTask extends AsyncTask<Void, Bitmap, Void> {

        final WeakReference<VideoThumbnailsView> mViewReference;
        final String mVideoFilePath;
        final float mThumbnailWidth;
        final float mThumbnailHeight;
        final int mThumbnailCount;

        ThumbnailsTask(final @NonNull VideoThumbnailsView view, final @NonNull String videoFilePath, final float thumbnailWidth, final float thumbnailHeight, final int thumbnailCount) {
            mViewReference = new WeakReference<>(view);
            mVideoFilePath = videoFilePath;
            mThumbnailWidth = thumbnailWidth;
            mThumbnailHeight = thumbnailHeight;
            mThumbnailCount = thumbnailCount;

        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.i(TAG, "generate " + mThumbnailCount + " thumbnails " + mThumbnailWidth + "x" + mThumbnailHeight);
            VideoThumbnailsExtractor.extractThumbnails(mVideoFilePath, mThumbnailCount, (int) mThumbnailHeight, (index, thumbnail) -> {
                ThumbnailsTask.this.publishProgress(thumbnail);
                return !isCancelled();
            });
            return null;
        }

        @Override
        protected void onProgressUpdate(Bitmap... values) {
            final VideoThumbnailsView view = mViewReference.get();
            if (view != null) {
                view.mThumbnails.addAll(Arrays.asList(values));
                view.invalidate();
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            final VideoThumbnailsView view = mViewReference.get();
            if (view != null) {
                view.invalidate();
                Log.i(TAG, "onPostExecute, we have " + view.mThumbnails.size() + " thumbs");
            }
        }
    }
}
