package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.core.content.ContextCompat;

/**
 * Draggable signature overlay.
 *
 * Editing: trash (top-left), scale (right), accept (✓) under the signature.
 * When not editing, only the bitmap is drawn — tap to edit again.
 */
public class DraggableSignatureView extends View {

    private Bitmap signatureBitmap;
    /** Logical position/size saved to the PDF. */
    private final RectF signatureRect = new RectF();
    /** Scratch rect for magnified display / hit-testing while editing. */
    private final RectF displayRect = new RectF();

    private Paint borderPaint;
    private Paint handlePaint;
    private Paint handleIconPaint;
    private Paint deletePaint;
    private Paint acceptPaint;
    private Paint acceptCheckPaint;
    private Paint hintPaint;
    private Paint hitHaloPaint;
    private Bitmap trashIcon;

    /** When false, no handles/highlight — tap body to edit again. */
    private boolean isEditing = true;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private int activeHandle = -1;

    private float lastTouchX, lastTouchY;
    private float downTouchX, downTouchY;
    private boolean gestureMoved = false;
    private boolean edgeTransferQueued = false;
    private float minWidthPx;
    private float minHeightPx;
    private float handleRadius;
    private float touchTolerance;
    private float bodyPad;
    private float sideHandleGap;
    private float editMagnification = 1f;
    private float dragFeedback = 1f;

    private static final int HANDLE_SCALE = 0;
    private static final int HANDLE_DELETE = 1;
    private static final int HANDLE_ACCEPT = 2;

    private OnSignatureChangedListener listener;
    private OnSignatureDeletedListener deleteListener;
    private OnSignatureAcceptedListener acceptListener;
    private OnSignatureSelectedListener selectedListener;
    private OnSignatureEdgeTransferListener edgeTransferListener;

    public interface OnSignatureChangedListener {
        void onSignatureMoved(float x, float y, float width, float height);
    }

    public interface OnSignatureDeletedListener {
        void onSignatureDeleted();
    }

    public interface OnSignatureAcceptedListener {
        void onSignatureAccepted();
    }

    public interface OnSignatureSelectedListener {
        void onSignatureSelected(DraggableSignatureView view);
    }

    /** Called when the signature is dragged past the top (−1) or bottom (+1) of the page. */
    public interface OnSignatureEdgeTransferListener {
        void onTransferOffPage(DraggableSignatureView view, int direction);
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
        minWidthPx = 48f * d;
        minHeightPx = 28f * d;
        handleRadius = 15f * d;
        touchTolerance = 30f * d;
        bodyPad = 16f * d;
        sideHandleGap = 10f * d;

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#4C45D6"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f * d);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{8 * d, 6 * d}, 0));

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.parseColor("#4C45D6"));
        handlePaint.setStyle(Paint.Style.FILL);

        handleIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleIconPaint.setColor(Color.WHITE);
        handleIconPaint.setStyle(Paint.Style.STROKE);
        handleIconPaint.setStrokeWidth(2.8f * d);
        handleIconPaint.setStrokeCap(Paint.Cap.ROUND);

        deletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deletePaint.setColor(Color.parseColor("#BA1A1A"));
        deletePaint.setStyle(Paint.Style.FILL);

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

    public void setOnSignatureSelectedListener(OnSignatureSelectedListener listener) {
        this.selectedListener = listener;
    }

    public void setOnSignatureEdgeTransferListener(OnSignatureEdgeTransferListener listener) {
        this.edgeTransferListener = listener;
    }

    public boolean isEditing() {
        return isEditing;
    }

    /** @deprecated use {@link #isEditing()} */
    public boolean isAccepted() {
        return !isEditing;
    }

    public void setEditing(boolean editing) {
        isEditing = editing;
        if (!editing) {
            isDragging = false;
            isResizing = false;
            editMagnification = 1f;
            dragFeedback = 1f;
        }
        invalidate();
    }

    public void resetAccepted() {
        setEditing(true);
    }

    public void setSignature(Bitmap bitmap, float initialX, float initialY, float width, float height) {
        this.signatureBitmap = bitmap;
        signatureRect.set(initialX, initialY, initialX + width, initialY + height);
        isEditing = true;
        edgeTransferQueued = false;
        refreshEditMagnification();
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
            isEditing = true;
            refreshEditMagnification();
        }
        invalidate();
    }

    public void clearSignature() {
        this.signatureBitmap = null;
        isEditing = true;
        editMagnification = 1f;
        invalidate();
    }

    public boolean hasSignature() {
        return signatureBitmap != null;
    }

    public Bitmap getSignatureBitmap() {
        return signatureBitmap;
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

    private void refreshEditMagnification() {
        editMagnification = 1f;
    }

    private float activeMagnification() {
        if (!isEditing) return 1f;
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

    private float rightHandleX() {
        return displayRect.right + sideHandleGap + handleRadius;
    }

    private float scaleHandleY() {
        return displayRect.centerY();
    }

    private float deleteHandleX() {
        return displayRect.left - sideHandleGap - handleRadius;
    }

    private float deleteHandleY() {
        return displayRect.top - sideHandleGap - handleRadius;
    }

    /** Keep a small sliver on-page so edges/corners are reachable; center can cross for page transfer. */
    private void clampLogicalToParent() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float minVisible = Math.min(signatureRect.width(), signatureRect.height()) * 0.2f;
        minVisible = Math.max(minVisible, 12f);

        float dx = 0, dy = 0;
        if (signatureRect.right < minVisible) dx = minVisible - signatureRect.right;
        if (signatureRect.left > getWidth() - minVisible) dx = getWidth() - minVisible - signatureRect.left;
        if (signatureRect.bottom < minVisible) dy = minVisible - signatureRect.bottom;
        if (signatureRect.top > getHeight() - minVisible) dy = getHeight() - minVisible - signatureRect.top;
        if (dx != 0 || dy != 0) {
            signatureRect.offset(dx, dy);
        }
    }

    /** Pull the signature so its center stays on this page (e.g. when page transfer is impossible). */
    public void snapCenterOntoPage() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float cx = signatureRect.centerX();
        float cy = signatureRect.centerY();
        float dx = 0, dy = 0;
        if (cx < 0) dx = -cx + 4f;
        else if (cx > getWidth()) dx = getWidth() - cx - 4f;
        if (cy < 0) dy = -cy + 4f;
        else if (cy > getHeight()) dy = getHeight() - cy - 4f;
        if (dx != 0 || dy != 0) {
            signatureRect.offset(dx, dy);
            clampLogicalToParent();
            invalidate();
            notifyListener();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (signatureBitmap == null || signatureBitmap.isRecycled()) return;

        updateDisplayRect();
        canvas.drawBitmap(signatureBitmap, null, displayRect, null);

        if (!isEditing) {
            // No highlight when confirmed / Done — tap to edit again
            return;
        }

        RectF halo = new RectF(displayRect);
        halo.inset(-bodyPad * 0.35f, -bodyPad * 0.35f);
        canvas.drawRoundRect(halo, 8, 8, hitHaloPaint);
        canvas.drawRect(displayRect, borderPaint);

        drawDeleteHandle(canvas, deleteHandleX(), deleteHandleY());
        drawScaleHandle(canvas, rightHandleX(), scaleHandleY());
        drawAcceptHandle(canvas, displayRect.centerX(), displayRect.bottom + handleRadius * 0.35f);

        if (isDragging && activeMagnification() > 1.05f) {
            String hint = "Magnified while dragging";
            float textW = hintPaint.measureText(hint);
            float tx = Math.max(8, Math.min(displayRect.centerX() - textW / 2f, getWidth() - textW - 8));
            float ty = Math.max(hintPaint.getTextSize() + 4, displayRect.top - 10);
            canvas.drawText(hint, tx, ty, hintPaint);
        }
    }

    private void drawScaleHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, handleRadius, handlePaint);
        float arm = handleRadius * 0.38f;
        // Corner-bracket icon suggesting resize
        canvas.drawLine(x - arm, y - arm, x - arm * 0.15f, y - arm, handleIconPaint);
        canvas.drawLine(x - arm, y - arm, x - arm, y - arm * 0.15f, handleIconPaint);
        canvas.drawLine(x + arm, y + arm, x + arm * 0.15f, y + arm, handleIconPaint);
        canvas.drawLine(x + arm, y + arm, x + arm, y + arm * 0.15f, handleIconPaint);
    }

    private void drawDeleteHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, handleRadius, deletePaint);
        Bitmap icon = getTrashIcon();
        if (icon != null) {
            float iw = icon.getWidth();
            float ih = icon.getHeight();
            canvas.drawBitmap(icon, x - iw / 2f, y - ih / 2f, null);
        }
    }

    private Bitmap getTrashIcon() {
        if (trashIcon != null && !trashIcon.isRecycled()) return trashIcon;
        Drawable d = ContextCompat.getDrawable(getContext(), R.drawable.ic_delete);
        if (d == null) return null;
        int size = Math.max(1, Math.round(handleRadius * 1.15f));
        trashIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(trashIcon);
        d = d.mutate();
        d.setBounds(0, 0, size, size);
        d.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        d.draw(c);
        return trashIcon;
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
                if (!isEditing) {
                    if (hitBody(x, y)) {
                        isEditing = true;
                        edgeTransferQueued = false;
                        refreshEditMagnification();
                        invalidate();
                        if (selectedListener != null) selectedListener.onSignatureSelected(this);
                        disallowParentIntercept();
                        return true;
                    }
                    return false;
                }

                activeHandle = getActiveHandle(x, y);
                downTouchX = lastTouchX = x;
                downTouchY = lastTouchY = y;
                gestureMoved = false;
                edgeTransferQueued = false;

                if (activeHandle == HANDLE_DELETE) {
                    if (deleteListener != null) deleteListener.onSignatureDeleted();
                    activeHandle = -1;
                    return true;
                }
                if (activeHandle == HANDLE_ACCEPT) {
                    setEditing(false);
                    if (acceptListener != null) acceptListener.onSignatureAccepted();
                    activeHandle = -1;
                    return true;
                }
                if (activeHandle == HANDLE_SCALE) {
                    isResizing = true;
                    isDragging = false;
                } else if (hitBody(x, y)) {
                    isDragging = true;
                    isResizing = false;
                    dragFeedback = 1f;
                    if (selectedListener != null) selectedListener.onSignatureSelected(this);
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
                    signatureRect.offset(dx, dy);
                    clampLogicalToParent();

                    if (!edgeTransferQueued && edgeTransferListener != null) {
                        float cy = signatureRect.centerY();
                        if (cy < 0) {
                            edgeTransferQueued = true;
                            edgeTransferListener.onTransferOffPage(this, -1);
                            return true;
                        } else if (cy > getHeight()) {
                            edgeTransferQueued = true;
                            edgeTransferListener.onTransferOffPage(this, 1);
                            return true;
                        }
                    }

                    invalidate();
                    notifyListener();
                } else if (isResizing && activeHandle == HANDLE_SCALE) {
                    disallowParentIntercept();
                    scaleUniform(dx, dy);
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
                edgeTransferQueued = false;
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
        if (isNearPoint(x, y, deleteHandleX(), deleteHandleY())) return HANDLE_DELETE;
        if (isNearPoint(x, y, rightHandleX(), scaleHandleY())) return HANDLE_SCALE;
        if (isNearPoint(x, y, displayRect.centerX(), displayRect.bottom + handleRadius * 0.35f)) {
            return HANDLE_ACCEPT;
        }
        return -1;
    }

    private boolean isNearPoint(float x, float y, float px, float py) {
        float dx = x - px;
        float dy = y - py;
        return dx * dx + dy * dy <= touchTolerance * touchTolerance;
    }

    /** Uniform scale from center. Drag away (right/down) enlarges; toward shrinks. */
    private void scaleUniform(float dx, float dy) {
        float basis = Math.max(signatureRect.width(), 1f);
        float delta = dx + dy * 0.35f;
        float factor = 1f + delta / basis;
        factor = Math.max(0.92f, Math.min(1.08f, factor));

        float cx = signatureRect.centerX();
        float cy = signatureRect.centerY();
        float newW = signatureRect.width() * factor;
        float newH = signatureRect.height() * factor;

        float maxW = getWidth() * 0.95f;
        float maxH = getHeight() * 0.95f;
        if (newW < minWidthPx || newH < minHeightPx || newW > maxW || newH > maxH) return;

        signatureRect.set(cx - newW / 2f, cy - newH / 2f, cx + newW / 2f, cy + newH / 2f);
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onSignatureMoved(
                    signatureRect.left, signatureRect.top,
                    signatureRect.width(), signatureRect.height());
        }
    }
}
