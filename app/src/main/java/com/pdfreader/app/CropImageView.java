package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * Full-frame crop control. Drag a corner, an edge, or the middle of the frame.
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

    private static final int NONE = -1;
    private static final int CORNER_TL = 0;
    private static final int CORNER_TR = 1;
    private static final int CORNER_BL = 2;
    private static final int CORNER_BR = 3;
    private static final int MOVE = 4;
    private static final int EDGE_L = 5;
    private static final int EDGE_R = 6;
    private static final int EDGE_T = 7;
    private static final int EDGE_B = 8;

    private int activeDrag = NONE;
    private float lastTouchX;
    private float lastTouchY;
    private Paint paintGrid;
    private Paint paintBracket;

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
        paintRect = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintRect.setColor(Color.WHITE);
        paintRect.setStyle(Paint.Style.STROKE);
        paintRect.setStrokeWidth(dp(1.5f));

        paintCorner = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCorner.setColor(Color.WHITE);
        paintCorner.setStyle(Paint.Style.FILL);

        paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGrid.setColor(Color.WHITE);
        paintGrid.setAlpha(110);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(dp(1f));

        paintBracket = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBracket.setColor(Color.WHITE);
        paintBracket.setStyle(Paint.Style.STROKE);
        paintBracket.setStrokeWidth(dp(3.5f));
        paintBracket.setStrokeCap(Paint.Cap.ROUND);

        paintOverlay = new Paint();
        paintOverlay.setColor(Color.BLACK);
        paintOverlay.setAlpha(150);
        setClickable(true);
    }

    public void resetCrop() {
        userAdjustedCrop = false;
        layoutCrop();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
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

            float thirdW = cropRect.width() / 3f;
            float thirdH = cropRect.height() / 3f;
            for (int i = 1; i <= 2; i++) {
                float x = cropRect.left + thirdW * i;
                float y = cropRect.top + thirdH * i;
                canvas.drawLine(x, cropRect.top, x, cropRect.bottom, paintGrid);
                canvas.drawLine(cropRect.left, y, cropRect.right, y, paintGrid);
            }

            canvas.drawRect(cropRect, paintRect);
            drawBracket(canvas, cropRect.left, cropRect.top, 1f, 1f);
            drawBracket(canvas, cropRect.right, cropRect.top, -1f, 1f);
            drawBracket(canvas, cropRect.left, cropRect.bottom, 1f, -1f);
            drawBracket(canvas, cropRect.right, cropRect.bottom, -1f, -1f);
            drawEdgeHandle(canvas, cropRect.centerX(), cropRect.top, true);
            drawEdgeHandle(canvas, cropRect.centerX(), cropRect.bottom, true);
            drawEdgeHandle(canvas, cropRect.left, cropRect.centerY(), false);
            drawEdgeHandle(canvas, cropRect.right, cropRect.centerY(), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawBracket(Canvas canvas, float x, float y, float xDir, float yDir) {
        float len = dp(22f);
        canvas.drawLine(x, y, x + xDir * len, y, paintBracket);
        canvas.drawLine(x, y, x, y + yDir * len, paintBracket);
    }

    private void drawEdgeHandle(Canvas canvas, float x, float y, boolean horizontal) {
        float longSide = dp(16f);
        float thick = dp(3f);
        float left = horizontal ? x - longSide : x - thick;
        float top = horizontal ? y - thick : y - longSide;
        float right = horizontal ? x + longSide : x + thick;
        float bottom = horizontal ? y + thick : y + longSide;
        canvas.drawRoundRect(left, top, right, bottom, thick, thick, paintCorner);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cropRect == null || bitmap == null) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeDrag = hitTest(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return activeDrag != NONE;

            case MotionEvent.ACTION_MOVE:
                if (activeDrag == NONE) return false;
                userAdjustedCrop = true;
                if (activeDrag == MOVE) {
                    moveCrop(x - lastTouchX, y - lastTouchY);
                } else if (activeDrag >= EDGE_L) {
                    updateEdge(activeDrag, x, y);
                } else {
                    updateCropRect(activeDrag, x, y);
                }
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeDrag = NONE;
                break;
        }

        return super.onTouchEvent(event);
    }

    private int hitTest(float x, float y) {
        float slop = dp(28f);
        if (near(x, y, cropRect.left, cropRect.top, slop)) return CORNER_TL;
        if (near(x, y, cropRect.right, cropRect.top, slop)) return CORNER_TR;
        if (near(x, y, cropRect.left, cropRect.bottom, slop)) return CORNER_BL;
        if (near(x, y, cropRect.right, cropRect.bottom, slop)) return CORNER_BR;
        if (Math.abs(x - cropRect.left) <= slop && y >= cropRect.top - slop && y <= cropRect.bottom + slop) {
            return EDGE_L;
        }
        if (Math.abs(x - cropRect.right) <= slop && y >= cropRect.top - slop && y <= cropRect.bottom + slop) {
            return EDGE_R;
        }
        if (Math.abs(y - cropRect.top) <= slop && x >= cropRect.left - slop && x <= cropRect.right + slop) {
            return EDGE_T;
        }
        if (Math.abs(y - cropRect.bottom) <= slop && x >= cropRect.left - slop && x <= cropRect.right + slop) {
            return EDGE_B;
        }
        if (cropRect.contains(x, y)) return MOVE;
        return NONE;
    }

    private boolean near(float x, float y, float pointX, float pointY, float slop) {
        return Math.abs(x - pointX) <= slop && Math.abs(y - pointY) <= slop;
    }

    private void moveCrop(float dx, float dy) {
        float minX = imageTransX;
        float minY = imageTransY;
        float maxX = imageTransX + bitmap.getWidth() * imageScale;
        float maxY = imageTransY + bitmap.getHeight() * imageScale;
        float width = cropRect.width();
        float height = cropRect.height();
        float left = clampFloat(cropRect.left + dx, minX, maxX - width);
        float top = clampFloat(cropRect.top + dy, minY, maxY - height);
        cropRect.offsetTo(left, top);
    }

    private void updateEdge(int edge, float x, float y) {
        float minSize = dp(56f);
        float minX = imageTransX;
        float minY = imageTransY;
        float maxX = imageTransX + bitmap.getWidth() * imageScale;
        float maxY = imageTransY + bitmap.getHeight() * imageScale;
        x = clampFloat(x, minX, maxX);
        y = clampFloat(y, minY, maxY);
        switch (edge) {
            case EDGE_L:
                if (cropRect.right - x >= minSize) cropRect.left = x;
                break;
            case EDGE_R:
                if (x - cropRect.left >= minSize) cropRect.right = x;
                break;
            case EDGE_T:
                if (cropRect.bottom - y >= minSize) cropRect.top = y;
                break;
            case EDGE_B:
                if (y - cropRect.top >= minSize) cropRect.bottom = y;
                break;
            default:
                break;
        }
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateCropRect(int corner, float x, float y) {
        float minSize = dp(56f);
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
