package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds a document page by its edges.
 *
 * Each frame is reduced to luminance, blurred, and scanned from the outside in
 * for the transition onto the page. The middle of each side is fit to a line
 * so corners and interior text do not pull the outline off the paper. A short
 * run of agreeing frames is required before the outline is treated as stable.
 */
public class DocumentAnalyzer implements ImageAnalysis.Analyzer {

    public interface DetectionCallback {
        /** Called on the main thread. corners = TL,TR,BR,BL each (nx,ny) in [0,1]. */
        void onResult(@Nullable float[] corners, boolean detected);
    }

    private static final int MAX_GRID = 240;
    private static final float INSET = 0.008f;
    private static final float JUMP = 0.08f;

    private final DetectionCallback callback;
    private final Handler mainHandler;

    private float[] stable;
    private float[] pending;
    private int stableFrames;
    private int pendingFrames;
    private int missedFrames;

    public DocumentAnalyzer(DetectionCallback callback, Handler mainHandler) {
        this.callback = callback;
        this.mainHandler = mainHandler;
    }

    /**
     * Detects a document in an upright bitmap. Returns TL,TR,BR,BL in [0,1], or null.
     */
    @Nullable
    public static float[] detect(@NonNull Bitmap bitmap) {
        int srcW = bitmap.getWidth();
        int srcH = bitmap.getHeight();
        if (srcW < 16 || srcH < 16) return null;

        int gw = MAX_GRID;
        int gh = Math.max(16, Math.round(gw * (srcH / (float) srcW)));
        if (gh > MAX_GRID) {
            gh = MAX_GRID;
            gw = Math.max(16, Math.round(gh * (srcW / (float) srcH)));
        }

        Bitmap small = Bitmap.createScaledBitmap(bitmap, gw, gh, true);
        int[] pixels = new int[gw * gh];
        small.getPixels(pixels, 0, gw, 0, 0, gw, gh);
        if (small != bitmap) small.recycle();

        byte[] luma = new byte[gw * gh];
        for (int i = 0; i < pixels.length; i++) {
            luma[i] = (byte) luminance(pixels[i]);
        }
        return detectOnLuma(luma, gw, gh);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            float[] raw = detectInFrame(image);
            int rotation = image.getImageInfo().getRotationDegrees();
            if (raw != null) {
                raw = rotateCorners(raw, rotation);
                raw = sortCorners(raw);
            }
            boolean detected = track(raw);
            final float[] out = stable == null ? null : stable.clone();
            final boolean found = detected;
            mainHandler.post(() -> callback.onResult(out, found));
        } finally {
            image.close();
        }
    }

    /** True once the same page outline has held still for a couple of frames. */
    private boolean track(@Nullable float[] raw) {
        if (raw == null || !plausible(raw)) {
            pending = null;
            pendingFrames = 0;
            missedFrames++;
            if (missedFrames >= 5) {
                stable = null;
                stableFrames = 0;
            }
            return false;
        }

        missedFrames = 0;
        if (stable == null) {
            if (pending != null && maxDelta(pending, raw) < JUMP) {
                pendingFrames++;
            } else {
                pending = raw;
                pendingFrames = 1;
            }
            if (pendingFrames >= 2) {
                stable = raw.clone();
                stableFrames = pendingFrames;
                pending = null;
                pendingFrames = 0;
                return true;
            }
            return false;
        }

        float delta = maxDelta(stable, raw);
        if (delta < JUMP) {
            smoothInto(stable, raw, 0.42f);
            stableFrames++;
            pending = null;
            pendingFrames = 0;
            return stableFrames >= 2;
        }

        if (pending != null && maxDelta(pending, raw) < JUMP) {
            pendingFrames++;
        } else {
            pending = raw;
            pendingFrames = 1;
        }
        if (pendingFrames >= 3) {
            stable = raw.clone();
            stableFrames = pendingFrames;
            pending = null;
            pendingFrames = 0;
            return true;
        }
        return stableFrames >= 2;
    }

    @Nullable
    private float[] detectInFrame(ImageProxy image) {
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ByteBuffer buf = yPlane.getBuffer();
        int rowStride = yPlane.getRowStride();
        int pxStride = yPlane.getPixelStride();
        Rect crop = image.getCropRect();
        if (crop == null || crop.width() < 16 || crop.height() < 16) {
            crop = new Rect(0, 0, image.getWidth(), image.getHeight());
        }

        int cropW = crop.width();
        int cropH = crop.height();
        int gw = MAX_GRID;
        int gh = Math.max(16, Math.round(gw * (cropH / (float) cropW)));
        if (gh > MAX_GRID) {
            gh = MAX_GRID;
            gw = Math.max(16, Math.round(gh * (cropW / (float) cropH)));
        }

        byte[] luma = new byte[gw * gh];
        int limit = buf.limit();
        for (int sy = 0; sy < gh; sy++) {
            int oy = crop.top + sy * cropH / gh;
            if (oy >= crop.bottom) oy = crop.bottom - 1;
            for (int sx = 0; sx < gw; sx++) {
                int ox = crop.left + sx * cropW / gw;
                if (ox >= crop.right) ox = crop.right - 1;
                int idx = oy * rowStride + ox * pxStride;
                luma[sy * gw + sx] = (idx >= 0 && idx < limit) ? buf.get(idx) : 0;
            }
        }
        return detectOnLuma(luma, gw, gh);
    }

    @Nullable
    static float[] detectOnLuma(byte[] luma, int w, int h) {
        byte[] blurred = blur(luma, w, h);
        int[] mag = sobel(blurred, w, h);
        int threshold = Math.max(18, percentile(mag, 0.90f) / 3);

        float[] quad = findQuad(blurred, mag, w, h, threshold, Math.max(2, Math.min(w, h) / 80));
        if (quad == null || areaOf(quad) > 0.93f * w * h) {
            int margin = Math.max(4, Math.min(w, h) / 14);
            float[] inner = findQuad(blurred, mag, w, h, threshold, margin);
            if (inner != null) quad = inner;
        }
        if (quad == null || !reasonable(quad, w, h)) return null;
        return normalizeInset(quad, w, h);
    }

    @Nullable
    private static float[] findQuad(byte[] luma, int[] mag, int w, int h, int threshold, int margin) {
        List<int[]> left = new ArrayList<>();
        List<int[]> right = new ArrayList<>();
        List<int[]> top = new ArrayList<>();
        List<int[]> bottom = new ArrayList<>();

        for (int y = margin; y < h - margin; y++) {
            int lx = scanHorizontal(luma, mag, w, h, y, margin, true, threshold);
            int rx = scanHorizontal(luma, mag, w, h, y, margin, false, threshold);
            if (lx >= 0 && rx > lx + w / 5) {
                left.add(new int[]{lx, y});
                right.add(new int[]{rx, y});
            }
        }
        for (int x = margin; x < w - margin; x++) {
            int ty = scanVertical(luma, mag, w, h, x, margin, true, threshold);
            int by = scanVertical(luma, mag, w, h, x, margin, false, threshold);
            if (ty >= 0 && by > ty + h / 5) {
                top.add(new int[]{x, ty});
                bottom.add(new int[]{x, by});
            }
        }

        left = middleSpan(left, false);
        right = middleSpan(right, false);
        top = middleSpan(top, true);
        bottom = middleSpan(bottom, true);

        if (left.size() < 6 || right.size() < 6 || top.size() < 6 || bottom.size() < 6) {
            return null;
        }

        float[] leftLine = fitRobust(left, true);
        float[] rightLine = fitRobust(right, true);
        float[] topLine = fitRobust(top, false);
        float[] bottomLine = fitRobust(bottom, false);
        if (leftLine == null || rightLine == null || topLine == null || bottomLine == null) {
            return null;
        }

        float[] tl = intersect(leftLine, topLine);
        float[] tr = intersect(rightLine, topLine);
        float[] br = intersect(rightLine, bottomLine);
        float[] bl = intersect(leftLine, bottomLine);
        if (tl == null || tr == null || br == null || bl == null) return null;

        return new float[]{tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]};
    }

    /**
     * Walks inward from one side of a row until the page edge: a strong gradient
     * where the interior is a sustained brighter region (the paper), not a thin highlight.
     */
    private static int scanHorizontal(byte[] luma, int[] mag, int w, int h, int y,
                                      int margin, boolean fromLeft, int threshold) {
        int start = fromLeft ? margin : w - 1 - margin;
        int end = fromLeft ? (w * 58 / 100) : (w * 42 / 100);
        int step = fromLeft ? 1 : -1;
        for (int x = start; fromLeft ? x < end : x > end; x += step) {
            int m = mag[y * w + x];
            if (m < threshold) continue;
            int interior = fromLeft
                    ? average(luma, w, x + 1, x + 12, y, y)
                    : average(luma, w, x - 12, x - 1, y, y);
            int exterior = fromLeft
                    ? average(luma, w, x - 8, x - 1, y, y)
                    : average(luma, w, x + 1, x + 8, y, y);
            if (interior >= 78 && interior - exterior >= 8) return x;
        }
        return -1;
    }

    private static int scanVertical(byte[] luma, int[] mag, int w, int h, int x,
                                    int margin, boolean fromTop, int threshold) {
        int start = fromTop ? margin : h - 1 - margin;
        int end = fromTop ? (h * 58 / 100) : (h * 42 / 100);
        int step = fromTop ? 1 : -1;
        for (int y = start; fromTop ? y < end : y > end; y += step) {
            int m = mag[y * w + x];
            if (m < threshold) continue;
            int interior = fromTop
                    ? average(luma, w, x, x, y + 1, y + 12)
                    : average(luma, w, x, x, y - 12, y - 1);
            int exterior = fromTop
                    ? average(luma, w, x, x, y - 8, y - 1)
                    : average(luma, w, x, x, y + 1, y + 8);
            if (interior >= 78 && interior - exterior >= 8) return y;
        }
        return -1;
    }

    /** Drops the points nearest the corners so the line follows the side, not the corner. */
    private static List<int[]> middleSpan(List<int[]> pts, boolean horizontal) {
        if (pts.size() < 8) return pts;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] p : pts) {
            int v = horizontal ? p[0] : p[1];
            if (v < min) min = v;
            if (v > max) max = v;
        }
        int span = max - min;
        if (span < 8) return pts;
        int lo = min + span / 5;
        int hi = max - span / 5;
        List<int[]> kept = new ArrayList<>();
        for (int[] p : pts) {
            int v = horizontal ? p[0] : p[1];
            if (v >= lo && v <= hi) kept.add(p);
        }
        return kept.size() >= 6 ? kept : pts;
    }

    private static byte[] blur(byte[] luma, int w, int h) {
        byte[] out = new byte[w * h];
        System.arraycopy(luma, 0, out, 0, luma.length);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int s = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        s += luma[(y + dy) * w + (x + dx)] & 0xFF;
                    }
                }
                out[y * w + x] = (byte) (s / 9);
            }
        }
        return out;
    }

    private static int[] sobel(byte[] blurred, int w, int h) {
        int[] mag = new int[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int gx = -(blurred[(y - 1) * w + (x - 1)] & 0xFF) + (blurred[(y - 1) * w + (x + 1)] & 0xFF)
                        - 2 * (blurred[y * w + (x - 1)] & 0xFF) + 2 * (blurred[y * w + (x + 1)] & 0xFF)
                        - (blurred[(y + 1) * w + (x - 1)] & 0xFF) + (blurred[(y + 1) * w + (x + 1)] & 0xFF);
                int gy = (blurred[(y - 1) * w + (x - 1)] & 0xFF) + 2 * (blurred[(y - 1) * w + x] & 0xFF)
                        + (blurred[(y - 1) * w + (x + 1)] & 0xFF)
                        - (blurred[(y + 1) * w + (x - 1)] & 0xFF) - 2 * (blurred[(y + 1) * w + x] & 0xFF)
                        - (blurred[(y + 1) * w + (x + 1)] & 0xFF);
                mag[y * w + x] = Math.abs(gx) + Math.abs(gy);
            }
        }
        return mag;
    }

    private static int percentile(int[] values, float p) {
        int[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int index = Math.min(copy.length - 1, Math.max(0, Math.round(p * (copy.length - 1))));
        return copy[index];
    }

    private static int average(byte[] luma, int w, int x0, int x1, int y0, int y1) {
        if (x1 < x0) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        if (y1 < y0) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        int h = luma.length / w;
        x0 = clamp(x0, 0, w - 1);
        x1 = clamp(x1, 0, w - 1);
        y0 = clamp(y0, 0, h - 1);
        y1 = clamp(y1, 0, h - 1);
        int sum = 0;
        int n = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                sum += luma[y * w + x] & 0xFF;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** @param xOnY true fits x = a*y + b, false fits y = a*x + b */
    @Nullable
    private static float[] fitRobust(List<int[]> pts, boolean xOnY) {
        float[] line = fit(pts, xOnY);
        if (line == null) return null;
        float[] residuals = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            float expected = xOnY ? line[0] * p[1] + line[1] : line[0] * p[0] + line[1];
            float actual = xOnY ? p[0] : p[1];
            residuals[i] = Math.abs(actual - expected);
        }
        float[] sorted = Arrays.copyOf(residuals, residuals.length);
        Arrays.sort(sorted);
        float tol = Math.max(3.5f, sorted[sorted.length / 2] * 2.5f);
        List<int[]> inliers = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            if (residuals[i] <= tol) inliers.add(pts.get(i));
        }
        if (inliers.size() < pts.size() / 2 || inliers.size() < 5) return null;
        return fit(inliers, xOnY);
    }

    @Nullable
    private static float[] fit(List<int[]> pts, boolean xOnY) {
        int n = pts.size();
        if (n < 5) return null;
        double s1 = 0, s2 = 0, s11 = 0, s12 = 0;
        for (int[] p : pts) {
            double a = xOnY ? p[1] : p[0];
            double b = xOnY ? p[0] : p[1];
            s1 += a;
            s2 += b;
            s11 += a * a;
            s12 += a * b;
        }
        double denom = n * s11 - s1 * s1;
        if (Math.abs(denom) < 1e-6) return null;
        float slope = (float) ((n * s12 - s1 * s2) / denom);
        float intercept = (float) ((s2 - slope * s1) / n);
        return new float[]{slope, intercept};
    }

    @Nullable
    private static float[] intersect(float[] vertical, float[] horizontal) {
        float aV = vertical[0], bV = vertical[1];
        float aH = horizontal[0], bH = horizontal[1];
        float denom = 1f - aH * aV;
        if (Math.abs(denom) < 0.02f) return null;
        float y = (aH * bV + bH) / denom;
        float x = aV * y + bV;
        return new float[]{x, y};
    }

    private static boolean reasonable(float[] q, int w, int h) {
        float[] tl = {q[0], q[1]};
        float[] tr = {q[2], q[3]};
        float[] br = {q[4], q[5]};
        float[] bl = {q[6], q[7]};
        float slackX = w * 0.08f;
        float slackY = h * 0.08f;
        for (float[] c : new float[][]{tl, tr, br, bl}) {
            if (c[0] < -slackX || c[0] > w + slackX || c[1] < -slackY || c[1] > h + slackY) {
                return false;
            }
        }
        float area = areaOf(q);
        if (area < 0.12f * w * h || area > 0.96f * w * h) return false;
        float wind = cross(tl, tr, br);
        if (wind == 0) return false;
        if (wind > 0) {
            if (cross(tr, br, bl) <= 0 || cross(br, bl, tl) <= 0 || cross(bl, tl, tr) <= 0) return false;
        } else if (cross(tr, br, bl) >= 0 || cross(br, bl, tl) >= 0 || cross(bl, tl, tr) >= 0) {
            return false;
        }
        return angleOk(tl, tr, br) && angleOk(tr, br, bl) && angleOk(br, bl, tl) && angleOk(bl, tl, tr);
    }

    private static boolean angleOk(float[] a, float[] b, float[] c) {
        float v1x = a[0] - b[0], v1y = a[1] - b[1];
        float v2x = c[0] - b[0], v2y = c[1] - b[1];
        float n1 = (float) Math.hypot(v1x, v1y);
        float n2 = (float) Math.hypot(v2x, v2y);
        if (n1 < 1f || n2 < 1f) return false;
        float cos = (v1x * v2x + v1y * v2y) / (n1 * n2);
        return cos < 0.75f && cos > -0.85f;
    }

    private static float areaOf(float[] q) {
        float sum = 0f;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            sum += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1];
        }
        return Math.abs(sum) * 0.5f;
    }

    private static float cross(float[] a, float[] b, float[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static float[] normalizeInset(float[] q, int w, int h) {
        float cx = (q[0] + q[2] + q[4] + q[6]) / 4f;
        float cy = (q[1] + q[3] + q[5] + q[7]) / 4f;
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            float x = q[i * 2] + (cx - q[i * 2]) * INSET;
            float y = q[i * 2 + 1] + (cy - q[i * 2 + 1]) * INSET;
            out[i * 2] = clamp(x / w, 0f, 1f);
            out[i * 2 + 1] = clamp(y / h, 0f, 1f);
        }
        return sortCorners(out);
    }

    private static boolean plausible(float[] c) {
        if (c.length != 8) return false;
        for (float v : c) {
            if (v < -0.02f || v > 1.02f) return false;
        }
        return areaOf(c) > 0.1f && areaOf(c) < 0.98f;
    }

    private static void smoothInto(float[] dst, float[] src, float alpha) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = dst[i] * (1f - alpha) + src[i] * alpha;
        }
    }

    private static float maxDelta(float[] a, float[] b) {
        float m = 0f;
        for (int i = 0; i < a.length; i++) {
            m = Math.max(m, Math.abs(a[i] - b[i]));
        }
        return m;
    }

    private static float[] rotateCorners(float[] c, int rotation) {
        if (rotation == 0) return c;
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            float nx = c[i * 2], ny = c[i * 2 + 1];
            float sx, sy;
            switch (rotation) {
                case 90:
                    sx = 1f - ny;
                    sy = nx;
                    break;
                case 180:
                    sx = 1f - nx;
                    sy = 1f - ny;
                    break;
                case 270:
                    sx = ny;
                    sy = 1f - nx;
                    break;
                default:
                    sx = nx;
                    sy = ny;
                    break;
            }
            out[i * 2] = sx;
            out[i * 2 + 1] = sy;
        }
        return out;
    }

    private static float[] sortCorners(float[] c) {
        float[][] pts = {{c[0], c[1]}, {c[2], c[3]}, {c[4], c[5]}, {c[6], c[7]}};
        float[] tl = pts[0], tr = pts[0], br = pts[0], bl = pts[0];
        float minSum = Float.MAX_VALUE, maxSum = -Float.MAX_VALUE;
        float minDiff = Float.MAX_VALUE, maxDiff = -Float.MAX_VALUE;
        for (float[] p : pts) {
            float sum = p[0] + p[1], diff = p[0] - p[1];
            if (sum < minSum) { minSum = sum; tl = p; }
            if (sum > maxSum) { maxSum = sum; br = p; }
            if (diff < minDiff) { minDiff = diff; bl = p; }
            if (diff > maxDiff) { maxDiff = diff; tr = p; }
        }
        return new float[]{tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]};
    }

    private static int luminance(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 54 + g * 183 + b * 19) >> 8;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
