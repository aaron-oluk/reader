package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/**
 * Utility class for processing and cleaning up signature images captured from photos.
 * Removes paper background and isolates the signature ink.
 */
public class SignatureProcessor {

    /**
     * Process a captured signature image to extract the signature from paper background.
     *
     * @param originalBitmap The original captured image
     * @param sensitivity Cleanup sensitivity from 0 to 100 (higher = more aggressive cleanup)
     * @return Processed bitmap with transparent background and clean signature
     */
    public static Bitmap processSignature(Bitmap originalBitmap, int sensitivity) {
        if (originalBitmap == null) return null;

        try {
            // Step 1: Convert to grayscale for better processing
            Bitmap grayscale = toGrayscale(originalBitmap);
            
            // Step 2: Increase contrast to make signature stand out
            Bitmap contrasted = increaseContrast(grayscale, 1.5f);
            
            // Don't need grayscale anymore
            if (grayscale != originalBitmap) {
                grayscale.recycle();
            }

            // Step 3: Create a mutable copy for pixel manipulation
            Bitmap processedBitmap = contrasted.copy(Bitmap.Config.ARGB_8888, true);
            
            // Don't need contrasted anymore
            if (contrasted != originalBitmap) {
                contrasted.recycle();
            }

            int width = processedBitmap.getWidth();
            int height = processedBitmap.getHeight();

            // Convert sensitivity (0-100) to threshold
            // Threshold range: 220 (low sensitivity) to 120 (high sensitivity)
            int threshold = 220 - (int)(sensitivity * 1.0);

            // Process each pixel
            int[] pixels = new int[width * height];
            processedBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // Calculate luminance
                int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                if (luminance > threshold) {
                    // This is likely paper/background - make it transparent
                    pixels[i] = Color.TRANSPARENT;
                } else {
                    // This is likely ink - make it darker for better visibility
                    int darkness = Math.max(0, 255 - (threshold - luminance) * 2);
                    pixels[i] = Color.rgb(darkness, darkness, darkness);
                }
            }

            processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            // Crop to signature bounds
            return cropToSignatureBounds(processedBitmap);
        } catch (Exception e) {
            e.printStackTrace();
            // If processing fails, return a copy of the original
            return originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
    }

    /**
     * Crop the bitmap to only contain the signature area (remove excess transparent space)
     */
    private static Bitmap cropToSignatureBounds(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= 0 || height <= 0) return bitmap;

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;

        boolean foundSignature = false;

        // Find the bounds of non-transparent pixels
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                if (Color.alpha(pixel) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    foundSignature = true;
                }
            }
        }

        // If no signature found, return original
        if (!foundSignature || minX >= maxX || minY >= maxY) {
            return bitmap;
        }

        // Add some padding
        int padding = 20;
        minX = Math.max(0, minX - padding);
        minY = Math.max(0, minY - padding);
        maxX = Math.min(width - 1, maxX + padding);
        maxY = Math.min(height - 1, maxY + padding);

        // Calculate new dimensions
        int newWidth = maxX - minX + 1;
        int newHeight = maxY - minY + 1;

        // Validate dimensions
        if (newWidth <= 0 || newHeight <= 0 || newWidth > width || newHeight > height) {
            return bitmap;
        }

        try {
            // Create cropped bitmap
            return Bitmap.createBitmap(bitmap, minX, minY, newWidth, newHeight);
        } catch (IllegalArgumentException e) {
            // Invalid crop parameters
            e.printStackTrace();
            return bitmap;
        }
    }

    /**
     * Apply grayscale filter to a bitmap
     */
    public static Bitmap toGrayscale(Bitmap original) {
        Bitmap grayscale = Bitmap.createBitmap(original.getWidth(), original.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayscale);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);
        return grayscale;
    }

    /**
     * Increase contrast of a bitmap (useful as a pre-processing step)
     */
    public static Bitmap increaseContrast(Bitmap original, float contrast) {
        // contrast 0 to 10. 1 is original
        ColorMatrix cm = new ColorMatrix(new float[] {
                contrast, 0, 0, 0, 0,
                0, contrast, 0, 0, 0,
                0, 0, contrast, 0, 0,
                0, 0, 0, 1, 0
        });

        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(original, 0, 0, paint);
        return result;
    }

    /**
     * Scale a signature bitmap to a reasonable size for document use
     */
    public static Bitmap scaleSignature(Bitmap signature, int maxWidth, int maxHeight) {
        if (signature == null) return null;

        int width = signature.getWidth();
        int height = signature.getHeight();

        // Validate dimensions
        if (width <= 0 || height <= 0) {
            return null;
        }

        float scaleWidth = (float) maxWidth / width;
        float scaleHeight = (float) maxHeight / height;
        float scale = Math.min(scaleWidth, scaleHeight);

        if (scale >= 1) {
            // Already smaller than max size
            return signature;
        }

        int newWidth = Math.max(1, (int) (width * scale));
        int newHeight = Math.max(1, (int) (height * scale));

        try {
            return Bitmap.createScaledBitmap(signature, newWidth, newHeight, true);
        } catch (IllegalArgumentException e) {
            // Invalid dimensions
            e.printStackTrace();
            return null;
        }
    }
}
