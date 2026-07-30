package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.rendering.ImageType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Renders PDF pages as a composite of two engines:
 *  - the page's own content (text, images, vector graphics) via the stock
 *    android.graphics.pdf.PdfRenderer, which handles embedded font programs reliably;
 *  - the annotation/AcroForm widget appearance layer (e.g. content filled in by another
 *    app) via PdfBox-Android, since the stock renderer never draws that layer at all.
 *
 * PdfBox-Android's own font/glyph handling has known gaps for certain embedded, subsetted
 * CFF/CID fonts (as produced by Acrobat's font subsetting), so it is deliberately NOT used
 * for page content here -- only for the annotation overlay, which it renders correctly.
 */
public class PdfBoxRenderer implements Closeable {

    private static final String TAG = "PdfBoxRenderer";

    private final PDDocument document;
    private final AnnotationOnlyPDFRenderer annotationRenderer;
    private final android.graphics.pdf.PdfRenderer contentRenderer;
    private final ParcelFileDescriptor contentPfd;
    private final File contentTempFile;

    public PdfBoxRenderer(Context context, ParcelFileDescriptor pfd) throws IOException {
        // Read the source once, up front, so the stock renderer and PdfBox-Android each get
        // their own independent file handle -- sharing one ParcelFileDescriptor between two
        // decoders would race on its single underlying read position.
        byte[] bytes = readAllBytes(pfd);

        try {
            document = PDDocument.load(new ByteArrayInputStream(bytes));
        } catch (InvalidPasswordException e) {
            // Preserve existing catch(SecurityException) call sites unchanged.
            throw new SecurityException(e.getMessage(), e);
        }
        annotationRenderer = new AnnotationOnlyPDFRenderer(document);

        contentTempFile = File.createTempFile("pdfbox_content_", ".pdf", context.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(contentTempFile)) {
            fos.write(bytes);
        }
        contentPfd = ParcelFileDescriptor.open(contentTempFile, ParcelFileDescriptor.MODE_READ_ONLY);
        contentRenderer = new android.graphics.pdf.PdfRenderer(contentPfd);
    }

    private static byte[] readAllBytes(ParcelFileDescriptor pfd) throws IOException {
        try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    public int getPageCount() {
        return document.getNumberOfPages();
    }

    public float getPageWidthPoints(int index) {
        return document.getPage(index).getMediaBox().getWidth();
    }

    public float getPageHeightPoints(int index) {
        return document.getPage(index).getMediaBox().getHeight();
    }

    public synchronized Bitmap renderPage(int index, float scale) throws IOException {
        // Matches PDFRenderer's own internal width/height rounding exactly, so the
        // annotation overlay always comes out pixel-aligned with the content bitmap.
        int widthPx = (int) Math.max(Math.floor(getPageWidthPoints(index) * scale), 1);
        int heightPx = (int) Math.max(Math.floor(getPageHeightPoints(index) * scale), 1);

        Bitmap content = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        content.eraseColor(Color.WHITE);
        android.graphics.pdf.PdfRenderer.Page page = contentRenderer.openPage(index);
        page.render(content, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();

        Bitmap annotations = annotationRenderer.renderImage(index, scale, ImageType.ARGB);
        new Canvas(content).drawBitmap(annotations, 0, 0, null);
        annotations.recycle();

        return content;
    }

    public synchronized void close() {
        try {
            document.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing PDDocument", e);
        }
        contentRenderer.close();
        try {
            contentPfd.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing content ParcelFileDescriptor", e);
        }
        if (contentTempFile != null) {
            contentTempFile.delete();
        }
    }
}
