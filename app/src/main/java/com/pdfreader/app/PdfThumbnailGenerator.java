package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class PdfThumbnailGenerator {

    private static final String TAG = "PdfThumbnailGenerator";

    public static Bitmap generateThumbnail(Context context, String pdfPath, int maxWidth, int maxHeight) {
        ParcelFileDescriptor pfd = null;
        PdfBoxRenderer renderer = null;

        try {
            // Handle both URI strings and file paths
            if (pdfPath.startsWith("content://") || pdfPath.startsWith("file://")) {
                // URI path
                Uri uri = Uri.parse(pdfPath);
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            } else {
                // File path
                File file = new File(pdfPath);
                if (file.exists()) {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                } else {
                    // Try as URI anyway
                    Uri uri = Uri.parse(pdfPath);
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                }
            }
            
            if (pfd == null) {
                Log.w(TAG, "Failed to open PDF: " + pdfPath);
                return null;
            }
            
            renderer = new PdfBoxRenderer(context, pfd);

            if (renderer.getPageCount() == 0) {
                return null;
            }

            // Calculate scale to fit within max dimensions
            float pageWidth = renderer.getPageWidthPoints(0);
            float pageHeight = renderer.getPageHeightPoints(0);

            float scaleX = maxWidth / pageWidth;
            float scaleY = maxHeight / pageHeight;
            float scale = Math.min(scaleX, scaleY);

            // Render first page
            return renderer.renderPage(0, scale);
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating thumbnail for: " + pdfPath, e);
            return null;
        } finally {
            if (renderer != null) {
                renderer.close();
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
