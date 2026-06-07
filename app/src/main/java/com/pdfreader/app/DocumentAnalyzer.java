package com.pdfreader.app;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

/**
 * Analyses each camera frame to locate a document (rectangular object with
 * strong edges against the background).  Uses a simple Sobel edge detector on
 * the YUV luminance plane, downsampled to 80×60 for speed.
 *
 * Detected corners are smoothed with a low-pass filter to avoid jitter, then
 * posted to the supplied callback on the main thread.
 *
 * Coordinate mapping: the raw image may be rotated relative to the screen.
 * rotationDegrees from ImageProxy is used to transform corners from image
 * space into normalised screen space [0..1].
 */
public class DocumentAnalyzer implements ImageAnalysis.Analyzer {

    public interface DetectionCallback {
        /** Called on the main thread. corners = TL,TR,BR,BL each (nx,ny) in [0,1]; null if nothing found. */
        void onResult(float[] corners, boolean detected);
    }

    private static final int W      = 80;
    private static final int H      = 60;
    private static final float ALPHA = 0.25f; // low-pass smoothing (0 = frozen, 1 = raw)

    private final DetectionCallback callback;
    private final Handler mainHandler;
    private float[] smoothed;
    private boolean lastDetected;

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

            final float[]  out = (smoothed != null) ? smoothed.clone() : null;
            final boolean  det = lastDetected;
            mainHandler.post(() -> callback.onResult(out, det));
        } finally {
            image.close();
        }
    }

    // -------------------------------------------------------------------------

    private float[] detectInFrame(ImageProxy image) {
        ImageProxy.PlaneProxy yPlane    = image.getPlanes()[0];
        ByteBuffer            buf       = yPlane.getBuffer();
        int                   imgW      = image.getWidth();
        int                   imgH      = image.getHeight();
        int                   rowStride = yPlane.getRowStride();
        int                   pxStride  = yPlane.getPixelStride();

        // Downsample to W×H
        byte[] luma = new byte[W * H];
        for (int sy = 0; sy < H; sy++) {
            int oy = sy * imgH / H;
            for (int sx = 0; sx < W; sx++) {
                int ox  = sx * imgW / W;
                int idx = oy * rowStride + ox * pxStride;
                luma[sy * W + sx] = (idx < buf.limit()) ? buf.get(idx) : 0;
            }
        }

        // Sobel gradient magnitude
        int[] mag    = new int[W * H];
        int   maxMag = 0;
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int gx = -(luma[(y-1)*W+(x-1)]&0xFF) + (luma[(y-1)*W+(x+1)]&0xFF)
                         -2*(luma[y*W+(x-1)]&0xFF)   + 2*(luma[y*W+(x+1)]&0xFF)
                         -(luma[(y+1)*W+(x-1)]&0xFF) + (luma[(y+1)*W+(x+1)]&0xFF);
                int gy =  (luma[(y-1)*W+(x-1)]&0xFF) + 2*(luma[(y-1)*W+x]&0xFF) + (luma[(y-1)*W+(x+1)]&0xFF)
                         -(luma[(y+1)*W+(x-1)]&0xFF) - 2*(luma[(y+1)*W+x]&0xFF) - (luma[(y+1)*W+(x+1)]&0xFF);
                int m = Math.abs(gx) + Math.abs(gy);
                mag[y * W + x] = m;
                if (m > maxMag) maxMag = m;
            }
        }

        if (maxMag < 50) return null; // scene has no significant edges

        int threshold = maxMag * 2 / 5;
        int minX = W, maxX = 0, minY = H, maxY = 0, count = 0;

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (mag[y * W + x] >= threshold) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    count++;
                }
            }
        }

        if (count < 30 || (maxX - minX) < W / 5 || (maxY - minY) < H / 5) return null;

        // Return normalised corners TL, TR, BR, BL
        float nx0 = (float) minX / W, ny0 = (float) minY / H;
        float nx1 = (float) maxX / W, ny1 = (float) maxY / H;
        return new float[]{ nx0, ny0,  nx1, ny0,  nx1, ny1,  nx0, ny1 };
    }

    /**
     * Rotates normalised image-space corners into screen space.
     *
     * rotationDegrees = how much to rotate the image CW to match the display.
     *   0  → image already upright (landscape device)
     *   90 → back camera, portrait: image is landscape, rotate 90° CW
     *  180 → upside down
     *  270 → front camera, portrait: image is landscape, rotate 270° CW
     */
    private float[] rotateCorners(float[] c, int rotation) {
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            float nx = c[i * 2], ny = c[i * 2 + 1];
            float sx, sy;
            switch (rotation) {
                case 90:  sx = 1 - ny; sy = nx;     break;
                case 180: sx = 1 - nx; sy = 1 - ny; break;
                case 270: sx = ny;     sy = 1 - nx; break;
                default:  sx = nx;     sy = ny;      break;
            }
            out[i * 2]     = sx;
            out[i * 2 + 1] = sy;
        }
        return out;
    }
}
