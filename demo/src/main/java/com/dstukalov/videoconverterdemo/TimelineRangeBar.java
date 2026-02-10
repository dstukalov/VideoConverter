package com.dstukalov.videoconverterdemo;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class TimelineRangeBar extends View {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {EDGE_NONE, EDGE_LEFT, EDGE_RIGHT, EDGE_MOVE})
    public @interface MotionEdge {}
    public static final int EDGE_NONE = 0;
    public static final int EDGE_LEFT = (1 << 1);
    public static final int EDGE_RIGHT = (1 << 2);
    public static final int EDGE_MOVE = (1 << 5);

    private long mDuration;
    private long mMaxRange = Long.MAX_VALUE;
    private long mMinRange = 1;

    private float mRangeFrom = 0f;
    private float mRangeTo = 1f; // 0 <= mRangeFrom <= mRangeTo <= 1
    private float mPosition;
    private final RectF mDrawRect = new RectF();
    private final RectF mTmpRect = new RectF();
    private float mTouchTarget;

    private final Paint mOutsidePaint = new Paint();
    private final Paint mHandlePaint = new Paint();
    private final Paint mHandleShadowPaint = new Paint();
    private final Paint mPositionPaint = new Paint();

    private int mMotionEdge = EDGE_NONE;
    private float mDownX;
    private float mLastX;

    private Callback mCallback;

    private final List<Rect> mExclusionRects = new ArrayList<>();

    public interface Callback {
        void onRangeChanged(long position, long timeFrom, long timeTo, @MotionEdge int motionEdge);
    }

    public TimelineRangeBar(Context context) {
        super(context);
        init(context);
    }

    public TimelineRangeBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TimelineRangeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public TimelineRangeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public void setDuration(long duration) {
        this.mDuration = duration;
    }

    public long getDuration() {
        return mDuration;
    }

    public void setMaxRange(long duration) {
        this.mMaxRange = duration;
    }

    public void setMinRange(long duration) {
        this.mMinRange = duration;
    }

    public void setRange(long timeFrom, long timeTo) {
        mRangeFrom = mDuration == 0 ? 0 : (1f * timeFrom / mDuration);
        mRangeTo = mDuration == 0 ? 0 : (1f * timeTo / mDuration);
        invalidate();
    }

    public long getTimeFrom() {
        return (long)(mDuration * mRangeFrom);
    }

    public long getTimeTo() {
        return (long)(mDuration * mRangeTo);
    }

    public void setPosition(long position) {
        final float p = mDuration == 0 ? 0 : (1f * position / mDuration);
        if (p != this.mPosition) {
            this.mPosition = p;
            invalidate();
        }
    }

    public long getPosition() {
        return (long)(mDuration * mPosition);
    }

    public boolean isFullSourceSelected() {
        return mRangeFrom == 0 && mRangeTo == 1f;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    private void init(Context context) {
        mOutsidePaint.setARGB(125, 50, 50, 50);

        final float density = context.getResources().getDisplayMetrics().density;

        mHandlePaint.setStyle(Paint.Style.STROKE);
        mHandlePaint.setColor(0xffffffff);
        mHandlePaint.setAntiAlias(true);
        mHandlePaint.setStrokeWidth(6f * density);
        mHandlePaint.setStrokeCap(Paint.Cap.ROUND);

        mHandleShadowPaint.setStyle(Paint.Style.STROKE);
        mHandleShadowPaint.setColor(0x40000000);
        mHandleShadowPaint.setAntiAlias(true);
        mHandleShadowPaint.setStrokeWidth(6f * density);
        mHandleShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        mHandleShadowPaint.setMaskFilter(new BlurMaskFilter(2.5f * density, BlurMaskFilter.Blur.NORMAL));

        mPositionPaint.setStyle(Paint.Style.STROKE);
        mPositionPaint.setColor(0x80ff0000);
        mPositionPaint.setAntiAlias(true);
        mPositionPaint.setStrokeWidth(2f * density);
        mPositionPaint.setStrokeCap(Paint.Cap.ROUND);

        mTouchTarget = 16f * density;

        mExclusionRects.add(new Rect(0, 0, 0, 0));
        mExclusionRects.add(new Rect(0, 0, 0, 0));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (Build.VERSION.SDK_INT >= 29) {
            final Rect leftExclusionRect = mExclusionRects.get(0);
            leftExclusionRect.bottom = getHeight();
            leftExclusionRect.right = getHeight();
            final Rect rightExclusionRect = mExclusionRects.get(1);
            rightExclusionRect.bottom = getHeight();
            rightExclusionRect.right = getWidth();
            rightExclusionRect.left = getWidth() - getHeight();
            ViewCompat.setSystemGestureExclusionRects(this, mExclusionRects);
        }

        final RectF rect = getDrawRect();

        mTmpRect.set(getPaddingLeft(), getPaddingTop(), rect.left, getHeight() - getPaddingBottom());
        canvas.drawRect(mTmpRect, mOutsidePaint);
        mTmpRect.set(rect.right, getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        canvas.drawRect(mTmpRect, mOutsidePaint);
        mTmpRect.set(rect.left, getPaddingTop(), rect.right, rect.top);
        canvas.drawRect(mTmpRect, mOutsidePaint);
        mTmpRect.set(rect.left, rect.bottom, rect.right, getHeight() - getPaddingBottom());
        canvas.drawRect(mTmpRect, mOutsidePaint);

        float vHandleSize = rect.height() * 5 / 12f;

        canvas.drawLine(rect.left, rect.centerY() - vHandleSize / 2, rect.left, rect.centerY() + vHandleSize / 2, mHandleShadowPaint);
        canvas.drawLine(rect.right, rect.centerY() - vHandleSize / 2, rect.right, rect.centerY() + vHandleSize / 2, mHandleShadowPaint);

        canvas.drawLine(rect.left, rect.centerY() - vHandleSize / 2, rect.left, rect.centerY() + vHandleSize / 2, mHandlePaint);
        canvas.drawLine(rect.right, rect.centerY() - vHandleSize / 2, rect.right, rect.centerY() + vHandleSize / 2, mHandlePaint);

        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        float positionX = getPaddingLeft() + width * mPosition;

        canvas.drawLine(positionX, getPaddingTop(), positionX, getHeight() - getPaddingBottom(), mPositionPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mMotionEdge = getHit(event.getX(), event.getY());
                mLastX = event.getX();
                mDownX = mLastX;
                break;
            case MotionEvent.ACTION_UP:
                if (mDownX == event.getX()) {
                    updatePosition(event.getX());
                }
                mMotionEdge = EDGE_NONE;
                reportChange();
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (mMotionEdge != EDGE_NONE) {
                    handleMotion(mMotionEdge,  event.getX() - mLastX);
                    mLastX = event.getX();
                    invalidate();
                    reportChange();
                }
                break;
        }

        return true;
    }

    private void reportChange() {
        if (mCallback != null) {
            mCallback.onRangeChanged((long)(mPosition * mDuration), (long)(mRangeFrom * mDuration), (long)(mRangeTo * mDuration), mMotionEdge);
        }
    }

    private RectF getDrawRect() {
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();
        mDrawRect.left = getPaddingLeft() + width * mRangeFrom;
        mDrawRect.right = getPaddingLeft() + width * mRangeTo;
        mDrawRect.top = getPaddingTop();
        mDrawRect.bottom = getPaddingTop() + height;
        return mDrawRect;
    }

    private void updatePosition(float x) {
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        mPosition = (x - getPaddingLeft()) / width;
        if (mPosition < mRangeFrom) {
            mPosition = mRangeFrom;
        }
        if (mPosition > mRangeTo) {
            mPosition = mRangeTo;
        }
    }

    float getRelativeMinRange() {
        return mDuration == 0 ? 0 : (1f * mMinRange / mDuration);
    }

    float getRelativeMaxRange() {
        return mDuration == 0 ? 0 : (mMaxRange == Long.MAX_VALUE ? 1 : 1f * mMaxRange / mDuration);
    }

    private void handleMotion(int motionEdge, float dx) {
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final float _dx = dx / width;
        if (motionEdge == EDGE_MOVE) {
            final float rangeWidth = mRangeTo - mRangeFrom;
            mRangeFrom += _dx;
            mRangeTo += _dx;
            if (mRangeFrom < 0) {
                mRangeFrom = 0;
                mRangeTo = rangeWidth;
            }
            if (mRangeTo > 1) {
                mRangeFrom = 1 - rangeWidth;
                mRangeTo = 1;
            }
        } else {
            if ((motionEdge & EDGE_LEFT) != 0) {
                mRangeFrom += _dx;
                if (mRangeTo - mRangeFrom < getRelativeMinRange()) {
                    mRangeFrom = mRangeTo -  getRelativeMinRange();
                }
                if (mRangeTo - mRangeFrom > getRelativeMaxRange()) {
                    mRangeTo = mRangeFrom +  getRelativeMaxRange();
                }
            }
            if ((motionEdge & EDGE_RIGHT) != 0) {
                mRangeTo += _dx;
                if (mRangeTo - mRangeFrom < getRelativeMinRange()) {
                    mRangeTo = mRangeFrom +  getRelativeMinRange();
                }
                if (mRangeTo - mRangeFrom > getRelativeMaxRange()) {
                    mRangeFrom = mRangeTo -  getRelativeMaxRange();
                }
            }
            if (mRangeFrom < 0) {
                mRangeFrom = 0;
            }
            if (mRangeTo > 1) {
                mRangeTo = 1;
            }
        }

        if ((motionEdge & EDGE_LEFT) != 0) {
            mPosition = mRangeFrom;
        } else if ((motionEdge & EDGE_RIGHT) != 0) {
            mPosition = mRangeTo;
        } else if (mPosition < mRangeFrom) {
            mPosition = mRangeFrom;
        } else if (mPosition > mRangeTo) {
            mPosition = mRangeTo;
        }
    }

    int getHit(float x, float y) {
        int edge = 0;
        final RectF rect = getDrawRect();
        if ((Math.abs(rect.left - x) < mTouchTarget)) {
            edge |= EDGE_LEFT;
        }
        if ((Math.abs(rect.right - x) < mTouchTarget)) {
            edge |= EDGE_RIGHT;
        }
        if ((edge & (EDGE_LEFT|EDGE_RIGHT)) == (EDGE_LEFT|EDGE_RIGHT)) {
            if (Math.abs(rect.left - x) < Math.abs(rect.right - x)) {
                edge = (edge & ~((EDGE_LEFT|EDGE_RIGHT))) | EDGE_LEFT;
            } else {
                edge = (edge & ~((EDGE_LEFT|EDGE_RIGHT))) | EDGE_RIGHT;
            }
        }
        if (edge == 0 && rect.contains(x, y)) {
            edge = EDGE_MOVE;
        }
        return edge;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, mDuration, mMaxRange, mMinRange, mPosition, mRangeFrom, mRangeTo);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        final SavedState savedState = (SavedState) state;
        mDuration = savedState.duration;
        mMaxRange = savedState.maxRange;
        mMinRange = savedState.minRange;
        mPosition = savedState.position;
        mRangeFrom = savedState.rangeFrom;
        mRangeTo = savedState.rangeTo;
        super.onRestoreInstanceState(savedState.getSuperState());
    }

    protected static class SavedState extends BaseSavedState {

        private final long duration;
        private final long maxRange;
        private final long minRange;
        private final float position;
        private final float rangeFrom;
        private final float rangeTo;

        private SavedState(Parcelable superState, long duration, long maxRange, long minRange, float position, float rangeFrom, float rangeTo) {
            super(superState);
            this.duration = duration;
            this.maxRange = maxRange;
            this.minRange = minRange;
            this.position = position;
            this.rangeFrom = rangeFrom;
            this.rangeTo = rangeTo;
        }

        private SavedState(Parcel in) {
            super(in);
            duration = in.readLong();
            maxRange = in.readLong();
            minRange = in.readLong();
            position = in.readFloat();
            rangeFrom = in.readFloat();
            rangeTo = in.readFloat();
        }

        @Override
        public void writeToParcel(Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeLong(duration);
            destination.writeLong(maxRange);
            destination.writeLong(minRange);
            destination.writeFloat(position);
            destination.writeFloat(rangeFrom);
            destination.writeFloat(rangeTo);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
