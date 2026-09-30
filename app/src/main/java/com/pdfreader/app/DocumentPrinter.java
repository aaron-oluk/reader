package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Sends an open document to the system print dialog (printers and "Save as PDF").
 * Full PDFs are spooled as-is so fonts and vectors stay intact. A page-range
 * selection is written as a smaller PDF, with a bitmap fallback if the file
 * cannot be split.
 */
public final class DocumentPrinter {

    private static final String TAG = "DocumentPrinter";
    private static final int RASTER_MAX_EDGE = 2400;

    private DocumentPrinter() {}

    public static void printPdf(Context context, String path, String jobName) {
        if (context == null) return;
        if (path == null || path.isEmpty()) {
            toast(context, "Document not ready to print");
            return;
        }
        if (isEpub(path)) {
            toast(context, "Open this book, then tap Print");
            return;
        }

        final Context appContext = context.getApplicationContext();
        final String documentName = pdfDocumentName(jobName);
        final String printJobName = displayName(jobName);

        new Thread(() -> {
            final File file;
            final boolean deleteWhenFinished;
            try {
                if (path.startsWith("content://")) {
                    file = copyContentUri(appContext, Uri.parse(path));
                    deleteWhenFinished = true;
                } else {
                    file = new File(path);
                    if (!file.exists() || !file.isFile()) {
                        throw new IOException("File not found");
                    }
                    deleteWhenFinished = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to open document for printing", e);
                toast(context, "Unable to open document for printing");
                return;
            }

            final PrintAttributes attributes = attributesFor(file);
            new Handler(Looper.getMainLooper()).post(() -> startPdfPrint(
                    context, file, appContext.getCacheDir(), documentName, printJobName,
                    deleteWhenFinished, attributes));
        }, "pdf-print").start();
    }

    public static void printWebView(Context context, WebView webView, String jobName) {
        if (context == null) return;
        if (webView == null || webView.getContentHeight() <= 0) {
            toast(context, "Document not ready to print");
            return;
        }
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) {
            toast(context, "Printing is not available on this device");
            return;
        }
        String name = displayName(jobName);
        try {
            printManager.print(name, webView.createPrintDocumentAdapter(name), null);
        } catch (Exception e) {
            Log.e(TAG, "WebView print failed", e);
            toast(context, "Unable to print document");
        }
    }

    /**
     * Paper size matches the document page and margins are zero, so the print
     * service does not mat the page onto a larger sheet.
     */
    private static PrintAttributes attributesFor(File file) {
        PrintAttributes.Builder builder = new PrintAttributes.Builder()
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS);
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() > 0) {
                PdfRenderer.Page page = renderer.openPage(0);
                try {
                    int widthMils = Math.max(1, Math.round(page.getWidth() * 1000f / 72f));
                    int heightMils = Math.max(1, Math.round(page.getHeight() * 1000f / 72f));
                    PrintAttributes.MediaSize media = new PrintAttributes.MediaSize(
                            "document_page", "Document",
                            Math.min(widthMils, heightMils),
                            Math.max(widthMils, heightMils));
                    builder.setMediaSize(widthMils > heightMils ? media.asLandscape() : media.asPortrait());
                } finally {
                    page.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Using borderless print defaults", e);
        } finally {
            if (renderer != null) renderer.close();
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
        }
        return builder.build();
    }

    private static void startPdfPrint(Context context, File file, File cacheDir,
                                      String documentName, String printJobName,
                                      boolean deleteWhenFinished, PrintAttributes attributes) {
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) {
            toast(context, "Printing is not available on this device");
            if (deleteWhenFinished) {
                file.delete();
            }
            return;
        }
        try {
            printManager.print(printJobName,
                    new PdfFilePrintAdapter(file, cacheDir, documentName, deleteWhenFinished),
                    attributes);
        } catch (Exception e) {
            Log.e(TAG, "Unable to start print job", e);
            toast(context, "Unable to print document");
            if (deleteWhenFinished) {
                file.delete();
            }
        }
    }

    private static boolean isEpub(String path) {
        String lower = path.toLowerCase(Locale.US);
        return lower.contains(".epub");
    }

    private static String displayName(String jobName) {
        if (jobName == null) return "Document";
        String name = jobName.trim();
        if (name.isEmpty()) return "Document";
        if (name.length() > 80) name = name.substring(0, 80);
        return name;
    }

    private static String pdfDocumentName(String jobName) {
        String name = displayName(jobName);
        if (name.toLowerCase(Locale.US).endsWith(".pdf")) return name;
        return name + ".pdf";
    }

    private static File copyContentUri(Context context, Uri uri) throws IOException {
        File out = File.createTempFile("print_src_", ".pdf", context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open document");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    fos.write(buffer, 0, n);
                }
            }
        } catch (IOException e) {
            out.delete();
            throw e;
        }
        return out;
    }

    private static void toast(Context context, String message) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
    }

    private static final class PdfFilePrintAdapter extends PrintDocumentAdapter {
        private final File file;
        private final File cacheDir;
        private final String documentName;
        private final boolean deleteWhenFinished;

        PdfFilePrintAdapter(File file, File cacheDir, String documentName, boolean deleteWhenFinished) {
            this.file = file;
            this.cacheDir = cacheDir;
            this.documentName = documentName;
            this.deleteWhenFinished = deleteWhenFinished;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                              CancellationSignal cancellationSignal, LayoutResultCallback callback,
                              Bundle extras) {
            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }

            int pageCount = PrintDocumentInfo.PAGE_COUNT_UNKNOWN;
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);
                if (renderer.getPageCount() > 0) {
                    pageCount = renderer.getPageCount();
                }
            } catch (Exception e) {
                Log.w(TAG, "Page count unavailable", e);
            } finally {
                if (renderer != null) {
                    renderer.close();
                }
                if (pfd != null) {
                    try {
                        pfd.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            PrintDocumentInfo info = new PrintDocumentInfo.Builder(documentName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal, WriteResultCallback callback) {
            if (cancellationSignal.isCanceled()) {
                callback.onWriteCancelled();
                return;
            }

            OutputStream out = null;
            try {
                out = new FileOutputStream(destination.getFileDescriptor());
                int pageCount = countPages();
                int[] selected = selectedPages(pages, pageCount);
                if (pageCount > 0 && selected.length == 0) {
                    callback.onWriteFailed("Unable to print the selected pages");
                    return;
                }
                if (pageCount > 0 && selected.length < pageCount) {
                    if (!writeSubset(selected, out, cancellationSignal)) {
                        callback.onWriteFailed("Unable to print the selected pages");
                        return;
                    }
                } else {
                    copyFile(file, out, cancellationSignal);
                }
                out.flush();
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                } else {
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                }
            } catch (PrintCancelledException e) {
                callback.onWriteCancelled();
            } catch (Exception e) {
                Log.e(TAG, "Print write failed", e);
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                } else {
                    callback.onWriteFailed("Unable to print document");
                }
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        @Override
        public void onFinish() {
            if (deleteWhenFinished && file.exists()) {
                file.delete();
            }
        }

        private int countPages() {
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);
                return renderer.getPageCount();
            } catch (Exception e) {
                return 0;
            } finally {
                if (renderer != null) renderer.close();
                if (pfd != null) {
                    try {
                        pfd.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private boolean writeSubset(int[] pages, OutputStream out, CancellationSignal cancellationSignal)
                throws IOException {
            File subset = File.createTempFile("print_subset_", ".pdf", cacheDir);
            try {
                boolean wrote = writeWithPdfBox(pages, subset) || writeRaster(pages, subset, cancellationSignal);
                if (!wrote || cancellationSignal.isCanceled()) return false;
                copyFile(subset, out, cancellationSignal);
                return true;
            } finally {
                subset.delete();
            }
        }

        private boolean writeWithPdfBox(int[] pages, File destFile) {
            PDDocument source = null;
            PDDocument dest = null;
            try {
                source = PDDocument.load(file);
                dest = new PDDocument();
                int count = source.getNumberOfPages();
                for (int index : pages) {
                    if (index < 0 || index >= count) continue;
                    dest.importPage(source.getPage(index));
                }
                if (dest.getNumberOfPages() == 0) return false;
                dest.save(destFile);
                return destFile.length() > 0;
            } catch (Exception e) {
                Log.w(TAG, "Unable to split PDF for the selected pages", e);
                return false;
            } finally {
                if (dest != null) {
                    try {
                        dest.close();
                    } catch (IOException ignored) {
                    }
                }
                if (source != null) {
                    try {
                        source.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private boolean writeRaster(int[] pages, File destFile, CancellationSignal cancellationSignal) {
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);
                int written = 0;
                for (int index : pages) {
                    if (cancellationSignal.isCanceled()) return false;
                    if (index < 0 || index >= renderer.getPageCount()) continue;
                    PdfRenderer.Page page = renderer.openPage(index);
                    Bitmap bitmap = null;
                    try {
                        int pageWidth = Math.max(1, page.getWidth());
                        int pageHeight = Math.max(1, page.getHeight());
                        float scale = Math.min(
                                (float) RASTER_MAX_EDGE / pageWidth,
                                (float) RASTER_MAX_EDGE / pageHeight);
                        scale = Math.max(1f, scale);
                        int bmpW = Math.max(1, Math.round(pageWidth * scale));
                        int bmpH = Math.max(1, Math.round(pageHeight * scale));
                        bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

                        android.graphics.pdf.PdfDocument.PageInfo info =
                                new android.graphics.pdf.PdfDocument.PageInfo.Builder(
                                        pageWidth, pageHeight, written + 1).create();
                        android.graphics.pdf.PdfDocument.Page pdfPage = document.startPage(info);
                        Canvas canvas = pdfPage.getCanvas();
                        canvas.drawBitmap(bitmap, null, new Rect(0, 0, pageWidth, pageHeight), null);
                        document.finishPage(pdfPage);
                        written++;
                    } finally {
                        if (bitmap != null) bitmap.recycle();
                        page.close();
                    }
                }
                if (written == 0) return false;
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    document.writeTo(fos);
                }
                return destFile.length() > 0;
            } catch (Exception e) {
                Log.w(TAG, "Raster print fallback failed", e);
                return false;
            } finally {
                document.close();
                if (renderer != null) renderer.close();
                if (pfd != null) {
                    try {
                        pfd.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private static int[] selectedPages(PageRange[] ranges, int pageCount) {
        if (pageCount <= 0) return new int[0];
        if (ranges == null || ranges.length == 0) {
            return allPages(pageCount);
        }
        for (PageRange range : ranges) {
            if (range == null) continue;
            if (PageRange.ALL_PAGES.equals(range)
                    || (range.getStart() <= 0 && range.getEnd() >= pageCount - 1 && ranges.length == 1)) {
                return allPages(pageCount);
            }
        }
        LinkedHashSet<Integer> indexes = new LinkedHashSet<>();
        for (PageRange range : ranges) {
            if (range == null) continue;
            int start = Math.max(0, range.getStart());
            int end = Math.min(pageCount - 1, range.getEnd());
            for (int i = start; i <= end; i++) {
                indexes.add(i);
            }
        }
        if (indexes.isEmpty()) return new int[0];
        if (indexes.size() == pageCount) return allPages(pageCount);
        int[] selected = new int[indexes.size()];
        int n = 0;
        for (int index : indexes) selected[n++] = index;
        return selected;
    }

    private static int[] allPages(int pageCount) {
        int[] all = new int[pageCount];
        for (int i = 0; i < pageCount; i++) all[i] = i;
        return all;
    }

    private static void copyFile(File file, OutputStream out, CancellationSignal cancellationSignal)
            throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) {
                if (cancellationSignal.isCanceled()) {
                    throw new PrintCancelledException();
                }
                out.write(buffer, 0, n);
            }
        }
    }

    private static final class PrintCancelledException extends IOException {
    }
}
