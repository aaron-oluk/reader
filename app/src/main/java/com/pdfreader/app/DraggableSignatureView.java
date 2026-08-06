package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

/**
 * Draggable / resizable signature overlay.
 *
 * While editing (not yet accepted), small signatures are magnified for easier
 * grabbing and handle use. Accept commits the logical (un-magnified) rect.
 */
public class DraggableSignatureView extends View {

    private Bitmap signatureBitmap;
    /** Logical position/size saved to the PDF. */
    private final RectF signatureRect = new RectF();
    /** Scratch rect for magnified display / hit-testing while editing. */
    private final RectF displayRect = new RectF();

    private Paint borderPaint;
    private Paint handlePaint;
    private Paint deletePaint;
    private Paint deleteCrossPaint;
    private Paint acceptPaint;
    private Paint acceptCheckPaint;
    private Paint hintPaint;
    private Paint hitHaloPaint;

    private boolean isAccepted = false;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private int activeHandle = -1;

    private float lastTouchX, lastTouchY;
    private float downTouchX, downTouchY;
    private boolean gestureMoved = false;
    private float minWidthPx;
    private float minHeightPx;
    private float handleRadius;
    private float touchTolerance;
    private float bodyPad;
    private float editMagnification = 1f;
    private float dragFeedback = 1f;

    // Handle indices: 0=TL, 1=TR delete, 2=BL, 3=BR, 4=BC accept
    private OnSignatureChangedListener listener;
    private OnSignatureDeletedListener deleteListener;
    private OnSignatureAcceptedListener acceptListener;

    public interface OnSignatureChangedListener {
        void onSignatureMoved(float x, float y, float width, float height);
    }

    public interface OnSignatureDeletedListener {
        void onSignatureDeleted();
    }

    public interface OnSignatureAcceptedListener {
        void onSignatureAccepted();
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
        float d = getResources().getDisplayMetrics().density;
        minWidthPx = 80f * d;
        minHeightPx = 40f * d;
        handleRadius = 14f * d;
        touchTolerance = 28f * d;
        bodyPad = 16f * d;

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#4C45D6"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f * d);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{8 * d, 6 * d}, 0));

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.parseColor("#4C45D6"));
        handlePaint.setStyle(Paint.Style.FILL);

        deletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deletePaint.setColor(Color.parseColor("#BA1A1A"));
        deletePaint.setStyle(Paint.Style.FILL);

        deleteCrossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deleteCrossPaint.setColor(Color.WHITE);
        deleteCrossPaint.setStyle(Paint.Style.STROKE);
        deleteCrossPaint.setStrokeWidth(3f * d);
        deleteCrossPaint.setStrokeCap(Paint.Cap.ROUND);

        acceptPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        acceptPaint.setColor(Color.parseColor("#10B981"));
        acceptPaint.setStyle(Paint.Style.FILL);

        acceptCheckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        acceptCheckPaint.setColor(Color.WHITE);
        acceptCheckPaint.setStyle(Paint.Style.STROKE);
        acceptCheckPaint.setStrokeWidth(3f * d);
        acceptCheckPaint.setStrokeCap(Paint.Cap.ROUND);
        acceptCheckPaint.setStrokeJoin(Paint.Join.ROUND);

        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.parseColor("#5D5D6F"));
        hintPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11f, getResources().getDisplayMetrics()));
        hintPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        hitHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hitHaloPaint.setColor(0x184C45D6);
        hitHaloPaint.setStyle(Paint.Style.FILL);
    }

    public void setOnSignatureAcceptedListener(OnSignatureAcceptedListener listener) {
        this.acceptListener = listener;
    }

    public boolean isAccepted() {
        return isAccepted;
    }

    public void resetAccepted() {
        isAccepted = false;
        refreshEditMagnification();
        invalidate();
    }

    public void setSignature(Bitmap bitmap, float initialX, float initialY, float width, float height) {
        this.signatureBitmap = bitmap;
        signatureRect.set(initialX, initialY, initialX + width, initialY + height);
        isAccepted = false;
        refreshEditMagnification();
        // Keep magnified display on-screen
        clampLogicalToParent();
        invalidate();
    }

    public void setSignature(Bitmap bitmap) {
        this.signatureBitmap = bitmap;
        if (bitmap != null && getWidth() > 0) {
            float width = getWidth() * 0.3f;
            float aspectRatio = (float) bitmap.getHeight() / Math.max(1, bitmap.getWidth());
            float height = width * aspectRatio;
            float x = (getWidth() - width) / 2f;
            float y = getHeight() - height - 100;
            signatureRect.set(x, y, x + width, y + height);
            isAccepted = false;
            refreshEditMagnification();
        }
        invalidate();
    }

    public void clearSignature() {
        this.signatureBitmap = null;
        isAccepted = false;
        editMagnification = 1f;
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

    public void setOnSignatureDeletedListener(OnSignatureDeletedListener listener) {
        this.deleteListener = listener;
    }

    /** Magnify only while dragging so placement is easier; idle edit stays true size. */
    private void refreshEditMagnification() {
        editMagnification = 1f;
    }

    private float activeMagnification() {
        if (isAccepted) return 1f;
        if (isDragging) {
            float d = getResources().getDisplayMetrics().density;
            float comfortW = 160f * d;
            float w = Math.max(1f, signatureRect.width());
            float scale = comfortW / w;
            return Math.max(1.12f, Math.min(2.25f, scale)) * dragFeedback;
        }
        return dragFeedback > 1f ? dragFeedback : 1f;
    }

    private void updateDisplayRect() {
        float mag = activeMagnification();
        if (mag <= 1.01f) {
            displayRect.set(signatureRect);
            return;
        }
        float cx = signatureRect.centerX();
        float cy = signatureRect.centerY();
        float hw = signatureRect.width() * mag / 2f;
        float hh = signatureRect.height() * mag / 2f;
        displayRect.set(cx - hw, cy - hh, cx + hw, cy + hh);
    }

    private void clampLogicalToParent() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        updateDisplayRect();
        float dx = 0, dy = 0;
        if (displayRect.left < 0) dx = -displayRect.left;
        if (displayRect.right > getWidth()) dx = getWidth() - displayRect.right;
        if (displayRect.top < bodyPad + hintPaint.getTextSize()) {
            dy = bodyPad + hintPaint.getTextSize() - displayRect.top;
        }
        if (displayRect.bottom > getHeight()) dy = getHeight() - displayRect.bottom;
        if (dx != 0 || dy != 0) {
            signatureRect.offset(dx, dy);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (signatureBitmap == null || signatureBitmap.isRecycled()) return;

        updateDisplayRect();
        canvas.drawBitmap(signatureBitmap, null, displayRect, null);

        if (isAccepted) {
            Paint solidBorder = new Paint(borderPaint);
            solidBorder.setPathEffect(null);
            solidBorder.setColor(Color.parseColor("#10B981"));
            canvas.drawRect(displayRect, solidBorder);
            return;
        }

        // Soft halo makes the grab area obvious
        RectF halo = new RectF(displayRect);
        halo.inset(-bodyPad * 0.35f, -bodyPad * 0.35f);
        canvas.drawRoundRect(halo, 8, 8, hitHaloPaint);

        canvas.drawRect(displayRect, borderPaint);

        drawResizeHandle(canvas, displayRect.left, displayRect.top);
        drawResizeHandle(canvas, displayRect.left, displayRect.bottom);
        drawResizeHandle(canvas, displayRect.right, displayRect.bottom);
        drawDeleteHandle(canvas, displayRect.right, displayRect.top);

        float acceptX = displayRect.centerX();
        float acceptY = displayRect.bottom;
        drawAcceptHandle(canvas, acceptX, acceptY);

        if (isDragging && activeMagnification() > 1.05f) {
            String hint = "Magnified while dragging";
            float textW = hintPaint.measureText(hint);
            float tx = Math.max(8, Math.min(displayRect.centerX() - textW / 2f, getWidth() - textW - 8));
            float ty = Math.max(hintPaint.getTextSize() + 4, displayRect.top - 10);
            canvas.drawText(hint, tx, ty, hintPaint);
        }
    }

    private void drawResizeHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, handleRadius, handlePaint);
    }

    private void drawDeleteHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, handleRadius, deletePaint);
        float arm = handleRadius * 0.42f;
        canvas.drawLine(x - arm, y - arm, x + arm, y + arm, deleteCrossPaint);
        canvas.drawLine(x + arm, y - arm, x - arm, y + arm, deleteCrossPaint);
    }

    private void drawAcceptHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, handleRadius, acceptPaint);
        float arm = handleRadius * 0.42f;
        canvas.drawLine(x - arm, y, x - arm * 0.2f, y + arm, acceptCheckPaint);
        canvas.drawLine(x - arm * 0.2f, y + arm, x + arm, y - arm * 0.6f, acceptCheckPaint);
    }

    private void disallowParentIntercept() {
        ViewParent p = getParent();
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(true);
            p = p.getParent();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (signatureBitmap == null) return false;

        float x = event.getX();
        float y = event.getY();
        updateDisplayRect();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (isAccepted) {
                    // Expanded hit area to unlock accepted signatures
                    if (hitBody(x, y)) {
                        isAccepted = false;
                        refreshEditMagnification();
                        clampLogicalToParent();
                        invalidate();
                        disallowParentIntercept();
                        return true;
                    }
                    return false;
                }

                activeHandle = getActiveHandle(x, y);
                downTouchX = lastTouchX = x;
                downTouchY = lastTouchY = y;
                gestureMoved = false;

                if (activeHandle == 1) {
                    if (deleteListener != null) deleteListener.onSignatureDeleted();
                    activeHandle = -1;
                    return true;
                }
                if (activeHandle == 4) {
                    // Accept: drop magnification and commit logical rect
                    isAccepted = true;
                    editMagnification = 1f;
                    dragFeedback = 1f;
                    invalidate();
                    if (acceptListener != null) acceptListener.onSignatureAccepted();
                    activeHandle = -1;
                    return true;
                }
                if (activeHandle != -1) {
                    isResizing = true;
                    isDragging = false;
                } else if (hitBody(x, y)) {
                    isDragging = true;
                    isResizing = false;
                    dragFeedback = 1f;
                    invalidate();
                } else {
                    return false;
                }
                disallowParentIntercept();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop() / 2f;
                if (!gestureMoved
                        && (Math.abs(x - downTouchX) > slop || Math.abs(y - downTouchY) > slop)) {
                    gestureMoved = true;
                }

                if (isDragging) {
                    disallowParentIntercept();
                    float clampedDx = dx;
                    float clampedDy = dy;
                    updateDisplayRect();
                    if (displayRect.left + clampedDx < 0) clampedDx = -displayRect.left;
                    if (displayRect.right + clampedDx > getWidth()) {
                        clampedDx = getWidth() - displayRect.right;
                    }
                    if (displayRect.top + clampedDy < 0) clampedDy = -displayRect.top;
                    if (displayRect.bottom + clampedDy > getHeight()) {
                        clampedDy = getHeight() - displayRect.bottom;
                    }
                    signatureRect.offset(clampedDx, clampedDy);
                    invalidate();
                    notifyListener();
                } else if (isResizing) {
                    disallowParentIntercept();
                    resizeWithHandle(activeHandle, dx, dy);
                    clampLogicalToParent();
                    invalidate();
                    notifyListener();
                }

                lastTouchX = x;
                lastTouchY = y;
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                isResizing = false;
                activeHandle = -1;
                dragFeedback = 1f;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private boolean hitBody(float x, float y) {
        RectF padded = new RectF(displayRect);
        padded.inset(-bodyPad, -bodyPad);
        return padded.contains(x, y);
    }

    private int getActiveHandle(float x, float y) {
        if (isNearPoint(x, y, displayRect.left, displayRect.top)) return 0;
        if (isNearPoint(x, y, displayRect.right, displayRect.top)) return 1;
        if (isNearPoint(x, y, displayRect.left, displayRect.bottom)) return 2;
        if (isNearPoint(x, y, displayRect.right, displayRect.bottom)) return 3;
        if (isNearPoint(x, y, displayRect.centerX(), displayRect.bottom)) return 4;
        return -1;
    }

    private boolean isNearPoint(float x, float y, float px, float py) {
        float dx = x - px;
        float dy = y - py;
        return dx * dx + dy * dy <= touchTolerance * touchTolerance;
    }

    private void resizeWithHandle(int handle, float dx, float dy) {
        float newLeft = signatureRect.left;
        float newTop = signatureRect.top;
        float newRight = signatureRect.right;
        float newBottom = signatureRect.bottom;

        switch (handle) {
            case 0:
                newLeft += dx;
                newTop += dy;
                break;
            case 2:
                newLeft += dx;
                newBottom += dy;
                break;
            case 3:
                newRight += dx;
                newBottom += dy;
                break;
            default:
                return;
        }

        if (newRight - newLeft >= minWidthPx && newBottom - newTop >= minHeightPx
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
