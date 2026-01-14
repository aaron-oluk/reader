package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

public class PdfThumbnailGenerator {
    
    public static Bitmap generateThumbnail(Context context, String pdfPath, int maxWidth, int maxHeight) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        
        try {
            Uri uri = Uri.parse(pdfPath);
            pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            
            if (pfd == null) {
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
            
        } catch (IOException e) {
            e.printStackTrace();
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
