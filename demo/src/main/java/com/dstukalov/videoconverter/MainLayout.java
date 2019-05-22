package com.dstukalov.videoconverter;

import android.annotation.TargetApi;
import android.content.Context;
import androidx.annotation.Nullable;

import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class MainLayout extends LinearLayout {
    public MainLayout(Context context) {
        super(context);
    }

    public MainLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MainLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MainLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final View topChild = getChildAt(0);
        final View bottomChild = getChildAt(1);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        bottomChild.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST));
        final int bottomHeight = bottomChild.getMeasuredHeight();
        topChild.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(heightSize - bottomHeight, MeasureSpec.AT_MOST));
        setMeasuredDimension(bottomChild.getMeasuredWidth(), bottomChild.getMeasuredHeight() + topChild.getMeasuredHeight());
    }
}
