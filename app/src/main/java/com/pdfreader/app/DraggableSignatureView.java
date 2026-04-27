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
 * Custom view that allows dragging, resizing, and deleting a signature bitmap overlay.
 *
 * Handle layout:
 *   [TL resize]  ────────────  [TR delete ✕]
 *       │                            │
 *   [BL resize]  ────────────  [BR resize]
 */
public class DraggableSignatureView extends View {

    private Bitmap signatureBitmap;
    private RectF signatureRect;
    private Paint borderPaint;
    private Paint handlePaint;
    private Paint deletePaint;
    private Paint deleteCrossPaint;

    private boolean isDragging = false;
    private boolean isResizing = false;
    private int activeHandle = -1;

    private float lastTouchX, lastTouchY;
    private float minWidth = 80;
    private float minHeight = 40;

    // Handle indices: 0=TopLeft, 1=TopRight(DELETE), 2=BottomLeft, 3=BottomRight
    private static final int HANDLE_RADIUS = 22;
    private static final int TOUCH_TOLERANCE = 55;

    private OnSignatureChangedListener listener;
    private OnSignatureDeletedListener deleteListener;

    public interface OnSignatureChangedListener {
        void onSignatureMoved(float x, float y, float width, float height);
    }

    public interface OnSignatureDeletedListener {
        void onSignatureDeleted();
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
        borderPaint.setAntiAlias(true);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        handlePaint = new Paint();
        handlePaint.setColor(Color.parseColor("#2196F3"));
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);

        deletePaint = new Paint();
        deletePaint.setColor(Color.parseColor("#F44336"));
        deletePaint.setStyle(Paint.Style.FILL);
        deletePaint.setAntiAlias(true);

        deleteCrossPaint = new Paint();
        deleteCrossPaint.setColor(Color.WHITE);
        deleteCrossPaint.setStyle(Paint.Style.STROKE);
        deleteCrossPaint.setStrokeWidth(3.5f);
        deleteCrossPaint.setStrokeCap(Paint.Cap.ROUND);
        deleteCrossPaint.setAntiAlias(true);
    }

    public void setSignature(Bitmap bitmap, float initialX, float initialY, float width, float height) {
        this.signatureBitmap = bitmap;
        signatureRect.set(initialX, initialY, initialX + width, initialY + height);
        invalidate();
    }

    public void setSignature(Bitmap bitmap) {
        this.signatureBitmap = bitmap;
        if (bitmap != null) {
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

    public float getSignatureX() { return signatureRect.left; }
    public float getSignatureY() { return signatureRect.top; }
    public float getSignatureWidth() { return signatureRect.width(); }
    public float getSignatureHeight() { return signatureRect.height(); }

    public void setOnSignatureChangedListener(OnSignatureChangedListener listener) {
        this.listener = listener;
    }

    public void setOnSignatureDeletedListener(OnSignatureDeletedListener listener) {
        this.deleteListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (signatureBitmap == null || signatureBitmap.isRecycled()) return;

        canvas.drawBitmap(signatureBitmap, null, signatureRect, null);
        canvas.drawRect(signatureRect, borderPaint);

        // Resize handles: TL, BL, BR
        drawResizeHandle(canvas, signatureRect.left, signatureRect.top);
        drawResizeHandle(canvas, signatureRect.left, signatureRect.bottom);
        drawResizeHandle(canvas, signatureRect.right, signatureRect.bottom);

        // Delete handle: TR (red ✕)
        drawDeleteHandle(canvas, signatureRect.right, signatureRect.top);
    }

    private void drawResizeHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, HANDLE_RADIUS, handlePaint);
    }

    private void drawDeleteHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, HANDLE_RADIUS, deletePaint);
        float arm = HANDLE_RADIUS * 0.42f;
        canvas.drawLine(x - arm, y - arm, x + arm, y + arm, deleteCrossPaint);
        canvas.drawLine(x + arm, y - arm, x - arm, y + arm, deleteCrossPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (signatureBitmap == null) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = getActiveHandle(x, y);
                if (activeHandle == 1) {
                    // Delete handle tapped
                    if (deleteListener != null) deleteListener.onSignatureDeleted();
                    activeHandle = -1;
                    return true;
                } else if (activeHandle != -1) {
                    isResizing = true;
                    isDragging = false;
                } else if (signatureRect.contains(x, y)) {
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
                    float newLeft = signatureRect.left + dx;
                    float newTop = signatureRect.top + dy;
                    float newRight = signatureRect.right + dx;
                    float newBottom = signatureRect.bottom + dy;
                    if (newLeft >= 0 && newRight <= getWidth() && newTop >= 0 && newBottom <= getHeight()) {
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
        if (isNearPoint(x, y, signatureRect.left, signatureRect.top)) return 0;   // TL resize
        if (isNearPoint(x, y, signatureRect.right, signatureRect.top)) return 1;  // TR delete
        if (isNearPoint(x, y, signatureRect.left, signatureRect.bottom)) return 2; // BL resize
        if (isNearPoint(x, y, signatureRect.right, signatureRect.bottom)) return 3; // BR resize
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

        switch (handle) {
            case 0: // Top Left: drag moves left edge and top edge
                newLeft += dx;
                newTop += dy;
                break;
            case 2: // Bottom Left: drag moves left edge and bottom edge
                newLeft += dx;
                newBottom += dy;
                break;
            case 3: // Bottom Right: drag moves right edge and bottom edge
                newRight += dx;
                newBottom += dy;
                break;
        }

        if (newRight - newLeft >= minWidth && newBottom - newTop >= minHeight
                && newLeft >= 0 && newRight <= getWidth()
                && newTop >= 0 && newBottom <= getHeight()) {
            signatureRect.set(newLeft, newTop, newRight, newBottom);
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onSignatureMoved(
                    signatureRect.left, signatureRect.top,
                    signatureRect.width(), signatureRect.height());
        }
    }
}
