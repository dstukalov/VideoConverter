package com.dstukalov.videoconverter;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.Nullable;

public class VideoThumbnailsView extends View {

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

    public VideoThumbnailsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoThumbnailsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VideoThumbnailsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
                mThumbnailsTask = new ThumbnailsTask(mVideoFilePath, thumbnailWidth, thumbnailHeight, thumbnailCount);
                mThumbnailsTask.execute();
            }
        } else {
            final int thumbnailCount = mDrawRect.width() / mDrawRect.height();
            final float thumbnailWidth = (float) mDrawRect.width() / thumbnailCount;
            mTmpRect.top = mDrawRect.top;
            mTmpRect.bottom = mDrawRect.bottom;
            for (int i = 0; i < mThumbnails.size(); i++) {
                mTmpRect.left = mDrawRect.left + i * thumbnailWidth;
                mTmpRect.right = mTmpRect.left + thumbnailWidth;
                final Bitmap thumbnailBitmap = mThumbnails.get(i);
                if (thumbnailBitmap != null) {
                    canvas.drawBitmap(thumbnailBitmap, null, mTmpRect, mPaint);
                }
            }
        }
    }

    private class ThumbnailsTask extends AsyncTask<Void, Bitmap, Void> {

        final String mVideoFilePath;
        final float mThumbnailWidth;
        final float mThumbnailHeight;
        final int mThumbnailCount;

        long mInvalidateTime;

        ThumbnailsTask(final String videoFilePath, final float thumbnailWidth, final float thumbnailHeight, final int thumbnailCount) {
            mVideoFilePath = videoFilePath;
            mThumbnailWidth = thumbnailWidth;
            mThumbnailHeight = thumbnailHeight;
            mThumbnailCount = thumbnailCount;
        }

        @Override
        protected Void doInBackground(Void... params) {
            final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(mVideoFilePath);
            long duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            mInvalidateTime = System.currentTimeMillis();
            final RectF dstThumbnailRect = new RectF(0, 0, mThumbnailWidth, mThumbnailHeight);
            final Rect srcThumbnailRect = new Rect();
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int i = 0; i < mThumbnailCount; i++) {
                if (isCancelled()) {
                    return null;
                }

                final long frameTimestamp = duration * 1000 * i / mThumbnailCount;
                final Bitmap srcThumbnailBitmap = mediaMetadataRetriever.getFrameAtTime(frameTimestamp);
                Bitmap dstThumbnailBitmap = srcThumbnailBitmap;
                if (srcThumbnailBitmap != null) {
                    final int srcThumbnailWidth = srcThumbnailBitmap.getWidth();
                    final int srcThumbnailHeight = srcThumbnailBitmap.getHeight();
                    if (srcThumbnailWidth > mThumbnailWidth && srcThumbnailHeight > mThumbnailHeight) {
                        dstThumbnailBitmap = Bitmap.createBitmap((int) mThumbnailWidth, (int) mThumbnailHeight,
                                srcThumbnailBitmap.getConfig());
                        if (srcThumbnailWidth > srcThumbnailHeight) {
                            srcThumbnailRect.top = 0;
                            srcThumbnailRect.bottom = srcThumbnailHeight;
                            srcThumbnailRect.left = (srcThumbnailWidth - srcThumbnailHeight) / 2;
                            srcThumbnailRect.right = srcThumbnailRect.left + srcThumbnailHeight;
                        } else {
                            srcThumbnailRect.left = 0;
                            srcThumbnailRect.right = srcThumbnailWidth;
                            srcThumbnailRect.top = (srcThumbnailHeight - srcThumbnailWidth) / 2;
                            srcThumbnailRect.bottom = srcThumbnailRect.top + srcThumbnailWidth;
                        }
                        final Canvas canvas = new Canvas(dstThumbnailBitmap);
                        canvas.drawBitmap(srcThumbnailBitmap, srcThumbnailRect, dstThumbnailRect, paint);
                    }
                }
                if (dstThumbnailBitmap != srcThumbnailBitmap) {
                    srcThumbnailBitmap.recycle();
                }
                publishProgress(dstThumbnailBitmap);
            }
            mediaMetadataRetriever.release();
            return null;
        }

        @Override
        protected void onProgressUpdate(Bitmap... values) {
            mThumbnails.addAll(Arrays.asList(values));
            if (System.currentTimeMillis() > mInvalidateTime + 500) {
                mInvalidateTime = System.currentTimeMillis();
                invalidate();
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            invalidate();
        }
    }
}
