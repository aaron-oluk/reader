package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.AnnotationFilter;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
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

    // PdfBox-Android detects PDF blend modes (see PDFRenderer.hasBlendMode) but never actually
    // implements them -- it has no Xfermode/blend-compositing logic at all, so a Highlight
    // annotation's intended Multiply blend (which lets the underlying text show through the
    // color) renders as flat opaque color instead, hiding the text. We work around this by
    // rendering highlight annotations in their own pass and compositing that pass with a real
    // PorterDuff.Mode.MULTIPLY ourselves; every other annotation type composites normally.
    private static final AnnotationFilter HIGHLIGHT_FILTER = new AnnotationFilter() {
        @Override
        public boolean accept(PDAnnotation annotation) {
            return PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT.equals(annotation.getSubtype());
        }
    };
    private static final AnnotationFilter NON_HIGHLIGHT_FILTER = new AnnotationFilter() {
        @Override
        public boolean accept(PDAnnotation annotation) {
            return !PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT.equals(annotation.getSubtype());
        }
    };
    private static final Paint MULTIPLY_PAINT = new Paint();
    static {
        MULTIPLY_PAINT.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

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

    // Uses CropBox + accounts for /Rotate, matching PDFRenderer.renderImage()'s own internal
    // sizing exactly (it swaps width/height for 90/270deg pages) so the content bitmap we
    // create and the annotation overlay PdfBox-Android produces always end up pixel-aligned.
    public float getPageWidthPoints(int index) {
        com.tom_roush.pdfbox.pdmodel.PDPage page = document.getPage(index);
        com.tom_roush.pdfbox.pdmodel.common.PDRectangle box = page.getCropBox();
        int rotation = page.getRotation();
        return (rotation == 90 || rotation == 270) ? box.getHeight() : box.getWidth();
    }

    public float getPageHeightPoints(int index) {
        com.tom_roush.pdfbox.pdmodel.PDPage page = document.getPage(index);
        com.tom_roush.pdfbox.pdmodel.common.PDRectangle box = page.getCropBox();
        int rotation = page.getRotation();
        return (rotation == 90 || rotation == 270) ? box.getWidth() : box.getHeight();
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

        Canvas canvas = new Canvas(content);

        annotationRenderer.setAnnotationsFilter(HIGHLIGHT_FILTER);
        Bitmap highlights = annotationRenderer.renderImage(index, scale, ImageType.ARGB);
        // PorterDuff.Mode.MULTIPLY multiplies the ALPHA channels too (resultAlpha = Sa * Da),
        // unlike the standard CSS/PDF "Multiply" blend mode -- compositing it directly onto
        // `content` would zero out content's alpha (and colors) everywhere the highlight
        // overlay is transparent, wiping out the rest of the page. So the multiply is done on
        // a scratch copy first; that copy's resulting alpha exactly matches the highlight's own
        // alpha (since `content` is fully opaque going in), so compositing the scratch copy back
        // with normal SRC_OVER only affects the highlighted region and leaves everything else untouched.
        Bitmap multiplied = content.copy(Bitmap.Config.ARGB_8888, true);
        new Canvas(multiplied).drawBitmap(highlights, 0, 0, MULTIPLY_PAINT);
        highlights.recycle();
        canvas.drawBitmap(multiplied, 0, 0, null);
        multiplied.recycle();

        annotationRenderer.setAnnotationsFilter(NON_HIGHLIGHT_FILTER);
        Bitmap otherAnnotations = annotationRenderer.renderImage(index, scale, ImageType.ARGB);
        canvas.drawBitmap(otherAnnotations, 0, 0, null);
        otherAnnotations.recycle();

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
