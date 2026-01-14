package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom view that allows dragging and resizing a signature bitmap overlay
 */
public class DraggableSignatureView extends View {

    private Bitmap signatureBitmap;
    private RectF signatureRect;
    private Paint borderPaint;
    private Paint handlePaint;

    private boolean isDragging = false;
    private boolean isResizing = false;
    private int activeHandle = -1;

    private float lastTouchX, lastTouchY;
    private float minWidth = 80;
    private float minHeight = 40;

    // Handle positions: 0=TopLeft, 1=TopRight, 2=BottomLeft, 3=BottomRight
    private static final int HANDLE_SIZE = 40;
    private static final int TOUCH_TOLERANCE = 50;

    private OnSignatureChangedListener listener;

    public interface OnSignatureChangedListener {
        void onSignatureMoved(float x, float y, float width, float height);
    }

    public DraggableSignatureView(Context context) {
        super(context);
        init();
    }

    public DraggableSignatureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableSignatureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        signatureRect = new RectF();

        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#2196F3"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#2196F3"));
        handlePaint.setStyle(Paint.Style.FILL);
    }

    public void setSignature(Bitmap bitmap, float initialX, float initialY, float width, float height) {
        this.signatureBitmap = bitmap;
        signatureRect.set(initialX, initialY, initialX + width, initialY + height);
        invalidate();
    }

    public void setSignature(Bitmap bitmap) {
        this.signatureBitmap = bitmap;
        if (bitmap != null) {
            // Default position: center bottom of the view
            float width = getWidth() * 0.3f;
            float aspectRatio = (float) bitmap.getHeight() / bitmap.getWidth();
            float height = width * aspectRatio;

            float x = (getWidth() - width) / 2;
            float y = getHeight() - height - 100;

            signatureRect.set(x, y, x + width, y + height);
        }
        invalidate();
    }

    public void clearSignature() {
        this.signatureBitmap = null;
        invalidate();
    }

    public boolean hasSignature() {
        return signatureBitmap != null;
    }

    public RectF getSignatureRect() {
        return new RectF(signatureRect);
    }

    public float getSignatureX() {
        return signatureRect.left;
    }

    public float getSignatureY() {
        return signatureRect.top;
    }

    public float getSignatureWidth() {
        return signatureRect.width();
    }

    public float getSignatureHeight() {
        return signatureRect.height();
    }

    public void setOnSignatureChangedListener(OnSignatureChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (signatureBitmap == null || signatureBitmap.isRecycled()) {
            return;
        }

        // Draw the signature
        canvas.drawBitmap(signatureBitmap, null, signatureRect, null);

        // Draw border and handles for interactive feedback
        canvas.drawRect(signatureRect, borderPaint);

        // Draw corner handles
        drawHandle(canvas, signatureRect.left, signatureRect.top);
        drawHandle(canvas, signatureRect.right, signatureRect.top);
        drawHandle(canvas, signatureRect.left, signatureRect.bottom);
        drawHandle(canvas, signatureRect.right, signatureRect.bottom);
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, HANDLE_SIZE / 2f, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (signatureBitmap == null) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check if touching a resize handle
                activeHandle = getActiveHandle(x, y);
                if (activeHandle != -1) {
                    isResizing = true;
                    isDragging = false;
                } else if (signatureRect.contains(x, y)) {
                    // Check if touching inside the signature
                    isDragging = true;
                    isResizing = false;
                } else {
                    return false;
                }
                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;

                if (isDragging) {
                    // Move the signature
                    float newLeft = signatureRect.left + dx;
                    float newTop = signatureRect.top + dy;
                    float newRight = signatureRect.right + dx;
                    float newBottom = signatureRect.bottom + dy;

                    // Keep within bounds
                    if (newLeft >= 0 && newRight <= getWidth() &&
                        newTop >= 0 && newBottom <= getHeight()) {
                        signatureRect.offset(dx, dy);
                        invalidate();
                        notifyListener();
                    }
                } else if (isResizing) {
                    resizeWithHandle(activeHandle, dx, dy);
                    invalidate();
                    notifyListener();
                }

                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                isResizing = false;
                activeHandle = -1;
                return true;
        }

        return super.onTouchEvent(event);
    }

    private int getActiveHandle(float x, float y) {
        // Top Left
        if (isNearPoint(x, y, signatureRect.left, signatureRect.top)) {
            return 0;
        }
        // Top Right
        if (isNearPoint(x, y, signatureRect.right, signatureRect.top)) {
            return 1;
        }
        // Bottom Left
        if (isNearPoint(x, y, signatureRect.left, signatureRect.bottom)) {
            return 2;
        }
        // Bottom Right
        if (isNearPoint(x, y, signatureRect.right, signatureRect.bottom)) {
            return 3;
        }
        return -1;
    }

    private boolean isNearPoint(float x, float y, float px, float py) {
        return Math.abs(x - px) < TOUCH_TOLERANCE && Math.abs(y - py) < TOUCH_TOLERANCE;
    }

    private void resizeWithHandle(int handle, float dx, float dy) {
        float newLeft = signatureRect.left;
        float newTop = signatureRect.top;
        float newRight = signatureRect.right;
        float newBottom = signatureRect.bottom;

        // Maintain aspect ratio while resizing
        float aspectRatio = signatureBitmap != null ?
                (float) signatureBitmap.getHeight() / signatureBitmap.getWidth() : 1f;

        switch (handle) {
            case 0: // Top Left - resize from top-left corner
                newLeft += dx;
                newTop = newBottom - (newRight - newLeft) * aspectRatio;
                break;
            case 1: // Top Right - resize from top-right corner
                newRight += dx;
                newTop = newBottom - (newRight - newLeft) * aspectRatio;
                break;
            case 2: // Bottom Left - resize from bottom-left corner
                newLeft += dx;
                newBottom = newTop + (newRight - newLeft) * aspectRatio;
                break;
            case 3: // Bottom Right - resize from bottom-right corner
                newRight += dx;
                newBottom = newTop + (newRight - newLeft) * aspectRatio;
                break;
        }

        // Check minimum size
        if (newRight - newLeft >= minWidth && newBottom - newTop >= minHeight) {
            // Check bounds
            if (newLeft >= 0 && newRight <= getWidth() &&
                newTop >= 0 && newBottom <= getHeight()) {
                signatureRect.set(newLeft, newTop, newRight, newBottom);
            }
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onSignatureMoved(
                    signatureRect.left,
                    signatureRect.top,
                    signatureRect.width(),
                    signatureRect.height()
            );
        }
    }
}
