package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * Custom ImageView for cropping with draggable corners
 */
public class CropImageView extends androidx.appcompat.widget.AppCompatImageView {

    private Bitmap bitmap;
    private Paint paintRect;
    private Paint paintCorner;
    private Paint paintOverlay;

    private RectF cropRect;
    private float imageScale = 1f;
    private float imageTransX = 0f;
    private float imageTransY = 0f;

    private static final int CORNER_SIZE = 60;
    private static final int TOUCH_TOLERANCE = 80;

    private int activeTouchCorner = -1; // -1: none, 0: TL, 1: TR, 2: BL, 3: BR
    private static final int CORNER_TL = 0;
    private static final int CORNER_TR = 1;
    private static final int CORNER_BL = 2;
    private static final int CORNER_BR = 3;

    // Optional initial crop region, in bitmap pixel coordinates, set before setImageBitmap().
    private RectF suggestedBitmapRegion;
    private boolean userAdjustedCrop;

    public void setSuggestedCropRegion(RectF bitmapRegion) {
        this.suggestedBitmapRegion = bitmapRegion;
    }

    public CropImageView(android.content.Context context) {
        super(context);
        init();
    }

    public CropImageView(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CropImageView(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintRect = new Paint();
        paintRect.setColor(Color.WHITE);
        paintRect.setStyle(Paint.Style.STROKE);
        paintRect.setStrokeWidth(4f);

        paintCorner = new Paint();
        paintCorner.setColor(Color.WHITE);
        paintCorner.setStyle(Paint.Style.FILL);

        paintOverlay = new Paint();
        paintOverlay.setColor(Color.BLACK);
        paintOverlay.setAlpha(128);
    }

    public void setImageBitmap(Bitmap bm) {
        this.bitmap = bm;
        this.userAdjustedCrop = false;
        this.cropRect = null;
        if (bm != null && getWidth() > 0 && getHeight() > 0) {
            layoutCrop();
        } else {
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bitmap != null && w > 0 && h > 0 && !userAdjustedCrop) {
            layoutCrop();
        }
    }

    private void layoutCrop() {
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        if (bitmap == null || viewWidth <= 0 || viewHeight <= 0) return;

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        float scaleX = viewWidth / bitmapWidth;
        float scaleY = viewHeight / bitmapHeight;
        imageScale = Math.min(scaleX, scaleY);
        if (imageScale <= 0f) return;

        float scaledWidth = bitmapWidth * imageScale;
        float scaledHeight = bitmapHeight * imageScale;
        imageTransX = (viewWidth - scaledWidth) / 2f;
        imageTransY = (viewHeight - scaledHeight) / 2f;

        if (suggestedBitmapRegion != null) {
            cropRect = new RectF(
                    imageTransX + suggestedBitmapRegion.left * imageScale,
                    imageTransY + suggestedBitmapRegion.top * imageScale,
                    imageTransX + suggestedBitmapRegion.right * imageScale,
                    imageTransY + suggestedBitmapRegion.bottom * imageScale
            );
            cropRect.left = Math.max(cropRect.left, imageTransX);
            cropRect.top = Math.max(cropRect.top, imageTransY);
            cropRect.right = Math.min(cropRect.right, imageTransX + scaledWidth);
            cropRect.bottom = Math.min(cropRect.bottom, imageTransY + scaledHeight);
        } else {
            float padX = Math.min(40f, scaledWidth * 0.06f);
            float padY = Math.min(40f, scaledHeight * 0.06f);
            cropRect = new RectF(
                    imageTransX + padX,
                    imageTransY + padY,
                    imageTransX + scaledWidth - padX,
                    imageTransY + scaledHeight - padY
            );
        }
        if (cropRect.width() < 2f || cropRect.height() < 2f) {
            cropRect = new RectF(imageTransX, imageTransY,
                    imageTransX + scaledWidth, imageTransY + scaledHeight);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null || bitmap.isRecycled() || cropRect == null) return;

        try {
            // Draw bitmap
            canvas.save();
            canvas.translate(imageTransX, imageTransY);
            canvas.scale(imageScale, imageScale);
            canvas.drawBitmap(bitmap, 0, 0, null);
            canvas.restore();

            // Draw dark overlay outside crop area
            canvas.drawRect(0, 0, getWidth(), cropRect.top, paintOverlay);
            canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), paintOverlay);
            canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, paintOverlay);
            canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, paintOverlay);

            // Draw crop rectangle
            canvas.drawRect(cropRect, paintRect);

            // Draw corner handles
            drawCorner(canvas, cropRect.left, cropRect.top);
            drawCorner(canvas, cropRect.right, cropRect.top);
            drawCorner(canvas, cropRect.left, cropRect.bottom);
            drawCorner(canvas, cropRect.right, cropRect.bottom);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawCorner(Canvas canvas, float x, float y) {
        float halfSize = CORNER_SIZE / 2f;
        canvas.drawRect(x - halfSize, y - halfSize, x + halfSize, y + halfSize, paintCorner);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cropRect == null) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeTouchCorner = getTouchedCorner(x, y);
                return activeTouchCorner >= 0;

            case MotionEvent.ACTION_MOVE:
                if (activeTouchCorner >= 0) {
                    userAdjustedCrop = true;
                    updateCropRect(activeTouchCorner, x, y);
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                activeTouchCorner = -1;
                break;
        }

        return super.onTouchEvent(event);
    }

    private int getTouchedCorner(float x, float y) {
        if (isNearPoint(x, y, cropRect.left, cropRect.top)) return CORNER_TL;
        if (isNearPoint(x, y, cropRect.right, cropRect.top)) return CORNER_TR;
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom)) return CORNER_BL;
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom)) return CORNER_BR;
        return -1;
    }

    private boolean isNearPoint(float x, float y, float pointX, float pointY) {
        return Math.abs(x - pointX) < TOUCH_TOLERANCE && Math.abs(y - pointY) < TOUCH_TOLERANCE;
    }

    private void updateCropRect(int corner, float x, float y) {
        float minSize = 100f;
        float maxX = imageTransX + bitmap.getWidth() * imageScale;
        float maxY = imageTransY + bitmap.getHeight() * imageScale;

        x = Math.max(imageTransX, Math.min(maxX, x));
        y = Math.max(imageTransY, Math.min(maxY, y));

        switch (corner) {
            case CORNER_TL:
                if (cropRect.right - x >= minSize) cropRect.left = x;
                if (cropRect.bottom - y >= minSize) cropRect.top = y;
                break;
            case CORNER_TR:
                if (x - cropRect.left >= minSize) cropRect.right = x;
                if (cropRect.bottom - y >= minSize) cropRect.top = y;
                break;
            case CORNER_BL:
                if (cropRect.right - x >= minSize) cropRect.left = x;
                if (y - cropRect.top >= minSize) cropRect.bottom = y;
                break;
            case CORNER_BR:
                if (x - cropRect.left >= minSize) cropRect.right = x;
                if (y - cropRect.top >= minSize) cropRect.bottom = y;
                break;
        }
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null || bitmap.isRecycled()) return null;
        if (cropRect == null) layoutCrop();
        if (cropRect == null || imageScale <= 0f) return null;

        try {
            float left = (cropRect.left - imageTransX) / imageScale;
            float top = (cropRect.top - imageTransY) / imageScale;
            float right = (cropRect.right - imageTransX) / imageScale;
            float bottom = (cropRect.bottom - imageTransY) / imageScale;

            int x = Math.max(0, Math.round(left));
            int y = Math.max(0, Math.round(top));
            int r = Math.min(bitmap.getWidth(), Math.round(right));
            int b = Math.min(bitmap.getHeight(), Math.round(bottom));
            int width = r - x;
            int height = b - y;

            if (width <= 0 || height <= 0) {
                android.util.Log.e("CropImageView", "Invalid crop dimensions: " + width + "x" + height);
                return null;
            }

            return Bitmap.createBitmap(bitmap, x, y, width, height);
        } catch (Exception e) {
            android.util.Log.e("CropImageView", "Error cropping bitmap", e);
            e.printStackTrace();
            return null;
        }
    }
}
