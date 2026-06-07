package com.pdfreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class DocumentDetectorView extends View {

    private static final int COLOR_DETECTED  = 0xFF4CAF50; // green
    private static final int COLOR_SEARCHING = 0xFFFFFFFF; // white

    private final Paint edgePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 8 values: TL(x,y) TR(x,y) BR(x,y) BL(x,y) in view pixels; null = show default brackets
    private float[] quad;
    private boolean detected;

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
    }

    /**
     * @param normalizedCorners TL, TR, BR, BL each as (x,y) in [0,1]; null to reset to default brackets
     * @param documentFound     true when a document boundary is confidently detected
     */
    public void setCorners(float[] normalizedCorners, boolean documentFound) {
        int w = getWidth(), h = getHeight();
        if (normalizedCorners == null || w == 0 || h == 0) {
            quad = null;
        } else {
            quad = new float[8];
            for (int i = 0; i < 4; i++) {
                quad[i * 2]     = normalizedCorners[i * 2]     * w;
                quad[i * 2 + 1] = normalizedCorners[i * 2 + 1] * h;
            }
        }
        this.detected = documentFound;
        invalidate();
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
        fillPaint.setColor(det ? Color.argb(35, 76, 175, 80) : Color.argb(18, 255, 255, 255));

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
