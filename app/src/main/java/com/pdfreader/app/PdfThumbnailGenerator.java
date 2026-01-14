package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class PdfThumbnailGenerator {
    
    private static final String TAG = "PdfThumbnailGenerator";
    
    public static Bitmap generateThumbnail(Context context, String pdfPath, int maxWidth, int maxHeight) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        
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
            
            renderer = new PdfRenderer(pfd);
            
            if (renderer.getPageCount() == 0) {
                return null;
            }
            
            // Render first page
            PdfRenderer.Page page = renderer.openPage(0);
            
            // Calculate scale to fit within max dimensions
            int pageWidth = page.getWidth();
            int pageHeight = page.getHeight();
            
            float scaleX = (float) maxWidth / pageWidth;
            float scaleY = (float) maxHeight / pageHeight;
            float scale = Math.min(scaleX, scaleY);
            
            int scaledWidth = (int) (pageWidth * scale);
            int scaledHeight = (int) (pageHeight * scale);
            
            // Create bitmap
            Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xFFFFFFFF); // White background
            
            // Render page to bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            
            return bitmap;
            
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
