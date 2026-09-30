package com.pdfreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
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
        if (quad != null) {
            drawQuad(canvas, quad, detected);
        } else {
            drawDefaultBrackets(canvas);
        }
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
}
