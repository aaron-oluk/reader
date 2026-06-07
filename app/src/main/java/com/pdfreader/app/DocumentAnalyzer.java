package com.pdfreader.app;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects a document rectangle in each camera frame using:
 *   1. 3×3 box blur on the luminance channel (noise reduction)
 *   2. Sobel edge detection
 *   3. Per-row / per-column edge projection to collect boundary points
 *   4. Least-squares line fitting to the left, right, top and bottom boundaries
 *   5. Intersection of the 4 fitted lines → actual document corners
 *
 * This approach handles mildly tilted documents and is robust against
 * background clutter because it fits a line through the outermost edge
 * points rather than taking a simple bounding box.
 *
 * Results are smoothed with a low-pass filter and posted to the main thread.
 */
public class DocumentAnalyzer implements ImageAnalysis.Analyzer {

    public interface DetectionCallback {
        /** Called on the main thread.  corners = TL,TR,BR,BL each (nx,ny) ∈ [0,1]; null = nothing found. */
        void onResult(float[] corners, boolean detected);
    }

    private static final int   W     = 160;
    private static final int   H     = 120;
    private static final float ALPHA = 0.2f; // low-pass smoothing (lower = smoother / more lag)

    private final DetectionCallback callback;
    private final Handler           mainHandler;
    private float[]  smoothed;
    private boolean  lastDetected;

    public DocumentAnalyzer(DetectionCallback callback, Handler mainHandler) {
        this.callback    = callback;
        this.mainHandler = mainHandler;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            float[] raw      = detectInFrame(image);
            int     rotation = image.getImageInfo().getRotationDegrees();

            if (raw != null) {
                raw = rotateCorners(raw, rotation);
                raw = sortCorners(raw);
                if (smoothed == null) {
                    smoothed = raw.clone();
                } else {
                    for (int i = 0; i < 8; i++) {
                        smoothed[i] = smoothed[i] * (1f - ALPHA) + raw[i] * ALPHA;
                    }
                }
                lastDetected = true;
            } else {
                lastDetected = false;
            }

            final float[] out = (smoothed != null) ? smoothed.clone() : null;
            final boolean det = lastDetected;
            mainHandler.post(() -> callback.onResult(out, det));
        } finally {
            image.close();
        }
    }

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    private float[] detectInFrame(ImageProxy image) {
        ImageProxy.PlaneProxy yPlane    = image.getPlanes()[0];
        ByteBuffer            buf       = yPlane.getBuffer();
        int                   imgW      = image.getWidth();
        int                   imgH      = image.getHeight();
        int                   rowStride = yPlane.getRowStride();
        int                   pxStride  = yPlane.getPixelStride();

        // --- 1. Downsample luminance to W×H --------------------------------
        byte[] luma = new byte[W * H];
        for (int sy = 0; sy < H; sy++) {
            int oy = sy * imgH / H;
            for (int sx = 0; sx < W; sx++) {
                int ox  = sx * imgW / W;
                int idx = oy * rowStride + ox * pxStride;
                luma[sy * W + sx] = (idx < buf.limit()) ? buf.get(idx) : 0;
            }
        }

        // --- 2. 3×3 box blur -----------------------------------------------
        byte[] blurred = new byte[W * H];
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int s = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        s += luma[(y + dy) * W + (x + dx)] & 0xFF;
                blurred[y * W + x] = (byte) (s / 9);
            }
        }

        // --- 3. Sobel edge magnitude ----------------------------------------
        int[] mag    = new int[W * H];
        int   maxMag = 0;
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int gx = -(blurred[(y-1)*W+(x-1)]&0xFF) + (blurred[(y-1)*W+(x+1)]&0xFF)
                         -2*(blurred[y*W+(x-1)]&0xFF)   + 2*(blurred[y*W+(x+1)]&0xFF)
                         -(blurred[(y+1)*W+(x-1)]&0xFF) + (blurred[(y+1)*W+(x+1)]&0xFF);
                int gy =  (blurred[(y-1)*W+(x-1)]&0xFF) + 2*(blurred[(y-1)*W+x]&0xFF) + (blurred[(y-1)*W+(x+1)]&0xFF)
                         -(blurred[(y+1)*W+(x-1)]&0xFF) - 2*(blurred[(y+1)*W+x]&0xFF) - (blurred[(y+1)*W+(x+1)]&0xFF);
                int m = Math.abs(gx) + Math.abs(gy);
                mag[y * W + x] = m;
                if (m > maxMag) maxMag = m;
            }
        }

        if (maxMag < 40) return null; // blank / featureless scene

        int threshold = maxMag * 3 / 10;

        // --- 4. Row / column projection to find boundary points -------------
        // Exclude the 3-pixel border to avoid image-edge artifacts.
        final int BORDER = 3;

        List<int[]> leftPts   = new ArrayList<>();  // points for left edge:   {x, y}
        List<int[]> rightPts  = new ArrayList<>();  // points for right edge:  {x, y}
        List<int[]> topPts    = new ArrayList<>();  // points for top edge:    {x, y}
        List<int[]> bottomPts = new ArrayList<>();  // points for bottom edge: {x, y}

        for (int y = BORDER; y < H - BORDER; y++) {
            int lx = -1, rx = -1;
            for (int x = BORDER; x < W - BORDER; x++) {
                if (mag[y * W + x] >= threshold) {
                    if (lx < 0) lx = x;
                    rx = x;
                }
            }
            if (lx >= 0 && rx > lx + W / 6) {
                // Only include rows where the two edge sides are far enough apart
                // (avoids isolated noise pixels being treated as a document edge)
                leftPts.add(new int[]{lx, y});
                rightPts.add(new int[]{rx, y});
            }
        }

        for (int x = BORDER; x < W - BORDER; x++) {
            int ty = -1, by = -1;
            for (int y = BORDER; y < H - BORDER; y++) {
                if (mag[y * W + x] >= threshold) {
                    if (ty < 0) ty = y;
                    by = y;
                }
            }
            if (ty >= 0 && by > ty + H / 6) {
                topPts.add(new int[]{x, ty});
                bottomPts.add(new int[]{x, by});
            }
        }

        // Need enough data on all 4 sides
        if (leftPts.size() < 8 || rightPts.size() < 8
                || topPts.size() < 8 || bottomPts.size() < 8) return null;

        // --- 5. Fit lines to each boundary ----------------------------------
        // Left / right:   x = a*y + b
        // Top  / bottom:  y = a*x + b
        float[] leftLine   = fitXonY(leftPts);
        float[] rightLine  = fitXonY(rightPts);
        float[] topLine    = fitYonX(topPts);
        float[] bottomLine = fitYonX(bottomPts);

        if (leftLine == null || rightLine == null || topLine == null || bottomLine == null)
            return null;

        // --- 6. Find the 4 corner intersections -----------------------------
        float[] tl = intersect(leftLine,  topLine);
        float[] tr = intersect(rightLine, topLine);
        float[] br = intersect(rightLine, bottomLine);
        float[] bl = intersect(leftLine,  bottomLine);

        if (tl == null || tr == null || br == null || bl == null) return null;

        // --- 7. Validate ----------------------------------------------------
        // All corners should be roughly inside (or just outside) the image
        final float SLACK = 0.15f; // allow 15% outside the image boundary
        for (float[] c : new float[][]{tl, tr, br, bl}) {
            if (c[0] < -SLACK * W || c[0] > W * (1 + SLACK)
                    || c[1] < -SLACK * H || c[1] > H * (1 + SLACK))
                return null;
        }

        // Document should cover at least 15% of the frame area
        float quadArea = quadArea(tl, tr, br, bl);
        if (quadArea < 0.15f * W * H) return null;

        // Normalise to [0,1]
        return new float[]{
            tl[0] / W, tl[1] / H,
            tr[0] / W, tr[1] / H,
            br[0] / W, br[1] / H,
            bl[0] / W, bl[1] / H
        };
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /** Least-squares fit of x = a*y + b through a list of {x,y} points. */
    private float[] fitXonY(List<int[]> pts) {
        int n = pts.size();
        if (n < 5) return null;
        double sy = 0, sx = 0, sy2 = 0, sxy = 0;
        for (int[] p : pts) { sx += p[0]; sy += p[1]; sy2 += (double)p[1]*p[1]; sxy += (double)p[0]*p[1]; }
        double denom = n * sy2 - sy * sy;
        if (Math.abs(denom) < 1e-6) return null;
        float a = (float) ((n * sxy - sx * sy) / denom);
        float b = (float) ((sx - a * sy) / n);
        return new float[]{a, b};
    }

    /** Least-squares fit of y = a*x + b through a list of {x,y} points. */
    private float[] fitYonX(List<int[]> pts) {
        int n = pts.size();
        if (n < 5) return null;
        double sx = 0, sy = 0, sx2 = 0, sxy = 0;
        for (int[] p : pts) { sx += p[0]; sy += p[1]; sx2 += (double)p[0]*p[0]; sxy += (double)p[0]*p[1]; }
        double denom = n * sx2 - sx * sx;
        if (Math.abs(denom) < 1e-6) return null;
        float a = (float) ((n * sxy - sx * sy) / denom);
        float b = (float) ((sy - a * sx) / n);
        return new float[]{a, b};
    }

    /**
     * Intersect  x = aV*y + bV  (vertical edge)  with  y = aH*x + bH  (horizontal edge).
     * Returns {x, y} of intersection, or null if lines are parallel.
     */
    private float[] intersect(float[] vertLine, float[] horizLine) {
        float aV = vertLine[0],  bV = vertLine[1];
        float aH = horizLine[0], bH = horizLine[1];
        // y = aH*(aV*y + bV) + bH  →  y(1 - aH*aV) = aH*bV + bH
        float denom = 1f - aH * aV;
        if (Math.abs(denom) < 0.01f) return null;
        float y = (aH * bV + bH) / denom;
        float x = aV * y + bV;
        return new float[]{x, y};
    }

    /** Shoelace formula for (approximate) quad area. */
    private float quadArea(float[] a, float[] b, float[] c, float[] d) {
        float area = Math.abs(
            (a[0]*b[1] - b[0]*a[1]) + (b[0]*c[1] - c[0]*b[1]) +
            (c[0]*d[1] - d[0]*c[1]) + (d[0]*a[1] - a[0]*d[1])
        ) / 2f;
        return area;
    }

    // -------------------------------------------------------------------------
    // Coordinate transforms
    // -------------------------------------------------------------------------

    /**
     * Rotates normalised image-space corners into screen space.
     *   0  → no rotation needed (landscape device)
     *  90  → portrait, back camera (rotate 90° CW)
     * 180  → upside-down
     * 270  → portrait, front camera (rotate 270° CW)
     */
    private float[] rotateCorners(float[] c, int rotation) {
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            float nx = c[i * 2], ny = c[i * 2 + 1];
            float sx, sy;
            switch (rotation) {
                case 90:  sx = 1f - ny; sy = nx;       break;
                case 180: sx = 1f - nx; sy = 1f - ny;  break;
                case 270: sx = ny;      sy = 1f - nx;  break;
                default:  sx = nx;      sy = ny;        break;
            }
            out[i * 2]     = sx;
            out[i * 2 + 1] = sy;
        }
        return out;
    }

    /**
     * Re-orders 4 corners into TL, TR, BR, BL.
     * TL = smallest (x+y), BR = largest (x+y), TR = largest (x-y), BL = smallest (x-y).
     */
    private float[] sortCorners(float[] c) {
        float[][] pts = {
            {c[0], c[1]}, {c[2], c[3]}, {c[4], c[5]}, {c[6], c[7]}
        };
        float[] tl = null, tr = null, br = null, bl = null;
        float minSum = Float.MAX_VALUE, maxSum = -Float.MAX_VALUE;
        float minDiff = Float.MAX_VALUE, maxDiff = -Float.MAX_VALUE;
        for (float[] p : pts) {
            float sum = p[0] + p[1], diff = p[0] - p[1];
            if (sum  < minSum)  { minSum  = sum;  tl = p; }
            if (sum  > maxSum)  { maxSum  = sum;  br = p; }
            if (diff < minDiff) { minDiff = diff; bl = p; }
            if (diff > maxDiff) { maxDiff = diff; tr = p; }
        }
        if (tl == null || tr == null || br == null || bl == null) return c;
        return new float[]{ tl[0],tl[1], tr[0],tr[1], br[0],br[1], bl[0],bl[1] };
    }
}
