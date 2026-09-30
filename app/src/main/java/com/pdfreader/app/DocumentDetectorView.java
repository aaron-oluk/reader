package com.pdfreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class DocumentDetectorView extends View {

    private static final int COLOR_DETECTED  = 0xFF6366F1; // brand indigo
    private static final int COLOR_SEARCHING = 0xFFFFFFFF; // white

    private final Paint edgePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleRing = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnCornersChangedListener {
        void onCornersChanged(float[] normalizedCorners);
    }

    // 8 values: TL(x,y) TR(x,y) BR(x,y) BL(x,y) in view pixels; null = show default brackets
    private float[] quad;
    private float[] pendingNormalized;
    private boolean detected;
    private boolean interactive;
    private boolean guideMode;
    /** Portrait page height / width. Zero means the frame can be any shape. */
    private float guideAspect;
    private final android.graphics.RectF guideRect = new android.graphics.RectF();
    private ScaleGestureDetector scaleDetector;
    private int guideDrag = -1;
    private float guideLastX;
    private float guideLastY;
    private static final int GUIDE_MOVE = 4;
    private int dragCorner = -1;
    private OnCornersChangedListener cornersChangedListener;

    public DocumentDetectorView(Context context) { super(context); init(); }
    public DocumentDetectorView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        float d = getContext().getResources().getDisplayMetrics().density;
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2f * d);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(4f * d);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        handleFill.setStyle(Paint.Style.FILL);
        handleFill.setColor(Color.WHITE);
        handleRing.setStyle(Paint.Style.STROKE);
        handleRing.setStrokeWidth(3f * d);
        handleRing.setColor(COLOR_DETECTED);
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleGuide(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    /**
     * Manual scan shows a rectangle to fit the page into.
     * {@code portraitHeightOverWidth} is the paper shape, such as A4. Zero allows any shape.
     */
    public void setGuideFrame(boolean enabled, float portraitHeightOverWidth) {
        guideMode = enabled;
        guideAspect = portraitHeightOverWidth;
        interactive = enabled;
        dragCorner = -1;
        guideDrag = -1;
        setClickable(enabled);
        if (enabled && getWidth() > 0 && getHeight() > 0) {
            layoutGuide();
        }
        invalidate();
    }

    public void setOnCornersChangedListener(OnCornersChangedListener listener) {
        cornersChangedListener = listener;
    }

    /** Manual mode draws draggable corner handles and ignores the live detector. */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) dragCorner = -1;
        invalidate();
    }

    /**
     * @param normalizedCorners TL, TR, BR, BL each as (x,y) in [0,1]; null to reset to default brackets
     * @param documentFound     true when a document boundary is confidently detected
     */
    public void setCorners(float[] normalizedCorners, boolean documentFound) {
        pendingNormalized = normalizedCorners == null ? null : normalizedCorners.clone();
        this.detected = documentFound;
        applyPendingCorners();
    }

    /** Current outline in normalized view coordinates, or null when no quad is shown. */
    public float[] getNormalizedCorners() {
        if (guideMode && guideRect.width() > 1f && getWidth() > 0) {
            float w = getWidth();
            float h = getHeight();
            return new float[]{
                    guideRect.left / w, guideRect.top / h,
                    guideRect.right / w, guideRect.top / h,
                    guideRect.right / w, guideRect.bottom / h,
                    guideRect.left / w, guideRect.bottom / h
            };
        }
        if (quad == null || getWidth() == 0 || getHeight() == 0) {
            return pendingNormalized == null ? null : pendingNormalized.clone();
        }
        float[] normalized = new float[8];
        float w = getWidth();
        float h = getHeight();
        for (int i = 0; i < 4; i++) {
            normalized[i * 2] = quad[i * 2] / w;
            normalized[i * 2 + 1] = quad[i * 2 + 1] / h;
        }
        return normalized;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyPendingCorners();
        if (guideMode) layoutGuide();
    }

    private void applyPendingCorners() {
        int w = getWidth();
        int h = getHeight();
        if (pendingNormalized == null || w == 0 || h == 0) {
            if (pendingNormalized == null) quad = null;
        } else {
            quad = new float[8];
            for (int i = 0; i < 4; i++) {
                quad[i * 2] = pendingNormalized[i * 2] * w;
                quad[i * 2 + 1] = pendingNormalized[i * 2 + 1] * h;
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (guideMode) return onGuideTouch(event);
        if (!interactive || quad == null) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragCorner = nearestCorner(x, y);
                return dragCorner >= 0;
            case MotionEvent.ACTION_MOVE:
                if (dragCorner < 0) return false;
                moveCorner(dragCorner, x, y);
                publishCorners();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean consumed = dragCorner >= 0;
                dragCorner = -1;
                return consumed;
            default:
                return false;
        }
    }

    private int nearestCorner(float x, float y) {
        float slop = 32f * getResources().getDisplayMetrics().density;
        int best = -1;
        float bestDist = slop * slop;
        for (int i = 0; i < 4; i++) {
            float dx = x - quad[i * 2];
            float dy = y - quad[i * 2 + 1];
            float dist = dx * dx + dy * dy;
            if (dist <= bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void moveCorner(int index, float x, float y) {
        float gap = 36f * getResources().getDisplayMetrics().density;
        float minX = gap;
        float maxX = getWidth() - gap;
        float minY = gap;
        float maxY = getHeight() - gap;
        switch (index) {
            case 0: // TL
                maxX = Math.min(maxX, quad[2] - gap);
                maxY = Math.min(maxY, quad[7] - gap);
                break;
            case 1: // TR
                minX = Math.max(minX, quad[0] + gap);
                maxY = Math.min(maxY, quad[5] - gap);
                break;
            case 2: // BR
                minX = Math.max(minX, quad[6] + gap);
                minY = Math.max(minY, quad[3] + gap);
                break;
            default: // BL
                maxX = Math.min(maxX, quad[4] - gap);
                minY = Math.max(minY, quad[1] + gap);
                break;
        }
        quad[index * 2] = Math.max(minX, Math.min(maxX, x));
        quad[index * 2 + 1] = Math.max(minY, Math.min(maxY, y));
        pendingNormalized = getNormalizedCorners();
    }

    private void publishCorners() {
        if (cornersChangedListener == null) return;
        float[] corners = getNormalizedCorners();
        if (corners != null) cornersChangedListener.onCornersChanged(corners);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawAlignmentGrid(canvas);
        if (guideMode) {
            if (guideRect.width() < 1f) layoutGuide();
            drawGuide(canvas);
            return;
        }
        if (quad != null) {
            drawQuad(canvas, quad, detected);
        } else {
            drawDefaultBrackets(canvas);
        }
    }

    private boolean showGrid;

    public void setShowGrid(boolean show) {
        showGrid = show;
        invalidate();
    }

    private void drawAlignmentGrid(Canvas canvas) {
        if (!showGrid) return;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        int color = edgePaint.getColor();
        float stroke = edgePaint.getStrokeWidth();
        edgePaint.setColor(0x66FFFFFF);
        edgePaint.setStrokeWidth(density());
        for (int i = 1; i <= 2; i++) {
            float x = w * i / 3f;
            float y = h * i / 3f;
            canvas.drawLine(x, 0, x, h, edgePaint);
            canvas.drawLine(0, y, w, y, edgePaint);
        }
        edgePaint.setColor(color);
        edgePaint.setStrokeWidth(stroke);
    }

    private void drawQuad(Canvas canvas, float[] q, boolean det) {
        int color = det ? COLOR_DETECTED : COLOR_SEARCHING;
        edgePaint.setColor(color);
        cornerPaint.setColor(color);
        fillPaint.setColor(det ? Color.argb(35, 99, 102, 241) : Color.argb(18, 255, 255, 255));

        Path fill = new Path();
        fill.moveTo(q[0], q[1]);
        fill.lineTo(q[2], q[3]);
        fill.lineTo(q[4], q[5]);
        fill.lineTo(q[6], q[7]);
        fill.close();
        canvas.drawPath(fill, fillPaint);

        canvas.drawLine(q[0], q[1], q[2], q[3], edgePaint);
        canvas.drawLine(q[2], q[3], q[4], q[5], edgePaint);
        canvas.drawLine(q[4], q[5], q[6], q[7], edgePaint);
        canvas.drawLine(q[6], q[7], q[0], q[1], edgePaint);

        float len = 36f * getResources().getDisplayMetrics().density;
        bracket(canvas, q[0], q[1], q[2], q[3], q[6], q[7], len);
        bracket(canvas, q[2], q[3], q[0], q[1], q[4], q[5], len);
        bracket(canvas, q[4], q[5], q[2], q[3], q[6], q[7], len);
        bracket(canvas, q[6], q[7], q[4], q[5], q[0], q[1], len);

        if (!interactive) return;
        float radius = 11f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(q[i * 2], q[i * 2 + 1], radius, handleFill);
            canvas.drawCircle(q[i * 2], q[i * 2 + 1], radius, handleRing);
        }
    }

    private void drawDefaultBrackets(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float d  = getResources().getDisplayMetrics().density;
        float mg = 24f * d;
        float len = 36f * d;
        cornerPaint.setColor(COLOR_SEARCHING);

        float x0 = mg,     y0 = mg;
        float x1 = w - mg, y1 = mg;
        float x2 = w - mg, y2 = h - mg;
        float x3 = mg,     y3 = h - mg;

        bracket(canvas, x0, y0, x1, y0, x3, y3, len);
        bracket(canvas, x1, y1, x0, y0, x2, y2, len);
        bracket(canvas, x2, y2, x1, y1, x3, y3, len);
        bracket(canvas, x3, y3, x2, y2, x0, y0, len);
    }

    private void bracket(Canvas canvas,
                         float cx, float cy,
                         float n1x, float n1y,
                         float n2x, float n2y,
                         float maxLen) {
        float d1x = n1x - cx, d1y = n1y - cy;
        float m1  = (float) Math.sqrt(d1x * d1x + d1y * d1y);
        float d2x = n2x - cx, d2y = n2y - cy;
        float m2  = (float) Math.sqrt(d2x * d2x + d2y * d2y);
        if (m1 == 0 || m2 == 0) return;
        float len = Math.min(maxLen, Math.min(m1, m2) * 0.35f);
        canvas.drawLine(cx, cy, cx + d1x / m1 * len, cy + d1y / m1 * len, cornerPaint);
        canvas.drawLine(cx, cy, cx + d2x / m2 * len, cy + d2y / m2 * len, cornerPaint);
    }

    private void layoutGuide() {
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;
        float margin = 28f * density();
        float maxW = Math.max(1f, viewW - margin * 2f);
        float maxH = Math.max(1f, viewH - margin * 2f);
        float aspect = orientedAspect();
        float frameW;
        float frameH;
        if (aspect <= 0f) {
            frameW = maxW * 0.86f;
            frameH = maxH * 0.86f;
        } else if (maxW * aspect <= maxH) {
            frameW = maxW * 0.92f;
            frameH = frameW * aspect;
        } else {
            frameH = maxH * 0.92f;
            frameW = frameH / aspect;
        }
        float left = (viewW - frameW) / 2f;
        float top = (viewH - frameH) / 2f;
        guideRect.set(left, top, left + frameW, top + frameH);
    }

    private float orientedAspect() {
        if (guideAspect <= 0f) return 0f;
        return getHeight() >= getWidth() ? guideAspect : 1f / guideAspect;
    }

    private boolean onGuideTouch(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() > 1 || scaleDetector.isInProgress()) {
            guideDrag = -1;
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                guideDrag = hitGuide(x, y);
                guideLastX = x;
                guideLastY = y;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(guideDrag >= 0);
                }
                return guideDrag >= 0;
            case MotionEvent.ACTION_MOVE:
                if (guideDrag < 0) return false;
                if (guideDrag == GUIDE_MOVE) {
                    moveGuide(x - guideLastX, y - guideLastY);
                } else {
                    resizeGuide(guideDrag, x, y);
                }
                guideLastX = x;
                guideLastY = y;
                invalidate();
                publishCorners();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                guideDrag = -1;
                break;
            default:
                break;
        }
        return true;
    }

    private int hitGuide(float x, float y) {
        float slop = 28f * density();
        if (near(x, y, guideRect.left, guideRect.top, slop)) return 0;
        if (near(x, y, guideRect.right, guideRect.top, slop)) return 1;
        if (near(x, y, guideRect.right, guideRect.bottom, slop)) return 2;
        if (near(x, y, guideRect.left, guideRect.bottom, slop)) return 3;
        if (guideRect.contains(x, y)) return GUIDE_MOVE;
        return -1;
    }

    private void moveGuide(float dx, float dy) {
        float w = guideRect.width();
        float h = guideRect.height();
        float left = clamp(guideRect.left + dx, 0f, getWidth() - w);
        float top = clamp(guideRect.top + dy, 0f, getHeight() - h);
        guideRect.set(left, top, left + w, top + h);
    }

    private void resizeGuide(int corner, float x, float y) {
        float min = 72f * density();
        float aspect = orientedAspect();
        if (aspect <= 0f) {
            resizeFree(corner, x, y, min);
            return;
        }
        float anchorX = (corner == 0 || corner == 3) ? guideRect.right : guideRect.left;
        float anchorY = (corner == 0 || corner == 1) ? guideRect.bottom : guideRect.top;
        float width = Math.abs(x - anchorX);
        float height = width * aspect;
        if (height < min) {
            height = min;
            width = height / aspect;
        }
        if (width < min) {
            width = min;
            height = width * aspect;
        }
        float left = (corner == 0 || corner == 3) ? anchorX - width : anchorX;
        float top = (corner == 0 || corner == 1) ? anchorY - height : anchorY;
        guideRect.set(left, top, left + width, top + height);
        clampGuideToView();
    }

    private void resizeFree(int corner, float x, float y, float min) {
        float left = guideRect.left;
        float top = guideRect.top;
        float right = guideRect.right;
        float bottom = guideRect.bottom;
        if (corner == 0 || corner == 3) left = Math.min(x, right - min);
        else right = Math.max(x, left + min);
        if (corner == 0 || corner == 1) top = Math.min(y, bottom - min);
        else bottom = Math.max(y, top + min);
        guideRect.set(left, top, right, bottom);
        clampGuideToView();
    }

    private void scaleGuide(float factor, float focusX, float focusY) {
        if (guideRect.width() < 1f) layoutGuide();
        float aspect = orientedAspect();
        float width = guideRect.width() * factor;
        float height = aspect > 0f ? width * aspect : guideRect.height() * factor;
        float min = 72f * density();
        width = Math.max(min, width);
        height = Math.max(min, aspect > 0f ? width * aspect : height);
        float left = focusX - width * (focusX - guideRect.left) / guideRect.width();
        float top = focusY - height * (focusY - guideRect.top) / Math.max(1f, guideRect.height());
        guideRect.set(left, top, left + width, top + height);
        clampGuideToView();
        invalidate();
        publishCorners();
    }

    private void clampGuideToView() {
        float min = 72f * density();
        if (guideRect.width() < min) guideRect.right = guideRect.left + min;
        if (guideRect.height() < min) guideRect.bottom = guideRect.top + min;
        if (guideRect.left < 0f) guideRect.offset(-guideRect.left, 0f);
        if (guideRect.top < 0f) guideRect.offset(0f, -guideRect.top);
        if (guideRect.right > getWidth()) guideRect.offset(getWidth() - guideRect.right, 0f);
        if (guideRect.bottom > getHeight()) guideRect.offset(0f, getHeight() - guideRect.bottom);
        if (guideRect.left < 0f) guideRect.left = 0f;
        if (guideRect.top < 0f) guideRect.top = 0f;
        if (guideRect.right > getWidth()) guideRect.right = getWidth();
        if (guideRect.bottom > getHeight()) guideRect.bottom = getHeight();
    }

    private void drawGuide(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || guideRect.width() < 1f) return;
        fillPaint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawRect(0, 0, w, guideRect.top, fillPaint);
        canvas.drawRect(0, guideRect.bottom, w, h, fillPaint);
        canvas.drawRect(0, guideRect.top, guideRect.left, guideRect.bottom, fillPaint);
        canvas.drawRect(guideRect.right, guideRect.top, w, guideRect.bottom, fillPaint);

        edgePaint.setColor(Color.WHITE);
        canvas.drawRect(guideRect, edgePaint);
        cornerPaint.setColor(Color.WHITE);
        float len = 28f * density();
        bracket(canvas, guideRect.left, guideRect.top, guideRect.right, guideRect.top, guideRect.left, guideRect.bottom, len);
        bracket(canvas, guideRect.right, guideRect.top, guideRect.left, guideRect.top, guideRect.right, guideRect.bottom, len);
        bracket(canvas, guideRect.right, guideRect.bottom, guideRect.left, guideRect.bottom, guideRect.right, guideRect.top, len);
        bracket(canvas, guideRect.left, guideRect.bottom, guideRect.right, guideRect.bottom, guideRect.left, guideRect.top, len);
    }

    private boolean near(float x, float y, float px, float py, float slop) {
        return Math.abs(x - px) <= slop && Math.abs(y - py) <= slop;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
