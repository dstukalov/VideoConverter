// RoundedCornersTransformation.java
package com.dstukalov.videoconverterdemo;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.DisplayMetrics; // For DENSITY_DEFAULT

import com.squareup.picasso.Transformation;

public class RoundedCornersTransformation implements Transformation {

    private final float radiusDp;
    private final float marginDp;
    private final String key;

    public RoundedCornersTransformation(float radiusDp) {
        this(radiusDp, 0f);
    }

    public RoundedCornersTransformation(float radiusDp, float marginDp) {
        this.radiusDp = radiusDp;
        this.marginDp = marginDp;
        this.key = "rounded_corners(radius=" + radiusDp + ", margin=" + marginDp + ")";
    }

    @Override
    public Bitmap transform(Bitmap source) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // Calculate pixel values from dp
        // source.getDensity() gives DPI (e.g., 160, 240, 320). Density factor = DPI / DENSITY_DEFAULT.
        float densityFactor = (float) source.getDensity() / DisplayMetrics.DENSITY_DEFAULT;

        if (source.getDensity() == 0) {
            // If the source bitmap has no density information (density is 0 dpi),
            // this is a problem. We need a density to convert dp to px.
            // Using a default of 1.0f (mdpi) is a last resort and may not look correct on all devices.
            // A more robust solution would be to ensure bitmaps have density or pass
            // the display density from a Context into this transformation's constructor.
            densityFactor = 1.0f; // Fallback: assumes mdpi if density is unknown
            // This means 1dp = 1px, which is only correct for mdpi screens.
            // Log.w("RoundedCorners", "Source bitmap has no density. Falling back to 1.0f. Results may vary.");
        } else if (densityFactor <= 0) {
            // Should not happen if source.getDensity() is valid and > 0
            densityFactor = 1.0f;
            // Log.w("RoundedCorners", "Calculated invalid densityFactor. Falling back to 1.0f.");
        }


        float radiusPx = this.radiusDp * densityFactor;
        float marginPx = this.marginDp * densityFactor;

        RectF rect = new RectF(marginPx, marginPx, source.getWidth() - marginPx, source.getHeight() - marginPx);
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);

        if (source != output) {
            source.recycle();
        }

        return output;
    }

    @Override
    public String key() {
        return this.key;
    }
}