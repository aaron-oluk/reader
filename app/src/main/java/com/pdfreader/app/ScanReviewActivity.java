package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScanReviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATHS = "image_paths";
    public static final int RESULT_ADD_MORE = 100;

    private List<String> imagePaths;
    private File tempPdfFile;
    private String savedFilePath;

    private ProgressBar loadingIndicator;
    private RecyclerView pagesRecycler;
    private TextView pageCountText;
    private View btnShare;
    private View btnPrint;
    private MaterialButton btnSave;
    private final List<Bitmap> previewPages = new ArrayList<>();
    private ActivityResultLauncher<Intent> cropLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowInsetsHelper.enableEdgeToEdge(this, false);
        super.onCreate(savedInstanceState);
        cropLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) generatePreview();
                });
        setContentView(R.layout.activity_scan_review);

        View __appBar = findViewById(R.id.app_bar);
        if (__appBar != null) {
            WindowInsetsHelper.applyAppBarInsets(__appBar);
        }

        imagePaths = getIntent().getStringArrayListExtra(EXTRA_IMAGE_PATHS);
        if (imagePaths == null) imagePaths = new ArrayList<>();

        loadingIndicator = findViewById(R.id.loading_indicator);
        pagesRecycler = findViewById(R.id.pages_recycler);
        pageCountText = findViewById(R.id.page_count_text);
        btnShare = findViewById(R.id.btn_share);
        btnPrint = findViewById(R.id.btn_print);
        btnSave = findViewById(R.id.btn_save_pdf);

        pagesRecycler.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((MaterialButton) findViewById(R.id.btn_add_page)).setOnClickListener(v -> {
            setResult(RESULT_ADD_MORE);
            finish();
        });

        btnSave.setOnClickListener(v -> showSaveDialog());
        btnShare.setOnClickListener(v -> {
            if (savedFilePath != null) shareFile(savedFilePath);
            else if (tempPdfFile != null) shareFile(tempPdfFile.getAbsolutePath());
        });
        btnPrint.setOnClickListener(v -> printCurrentDocument());

        generatePreview();
    }

    private void generatePreview() {
        loadingIndicator.setVisibility(View.VISIBLE);
        pagesRecycler.setVisibility(View.GONE);
        pageCountText.setText("Generating preview…");

        new Thread(() -> {
            try {
                // Build temp PDF from captured images
                tempPdfFile = new File(getCacheDir(), "preview_" + System.currentTimeMillis() + ".pdf");
                writePdf(imagePaths, tempPdfFile);

                // Render each page to a Bitmap
                List<Bitmap> pages = renderPdfPages(tempPdfFile);

                runOnUiThread(() -> {
                    pagesRecycler.setAdapter(null);
                    recyclePreviewPages();
                    previewPages.addAll(pages);
                    loadingIndicator.setVisibility(View.GONE);
                    pagesRecycler.setVisibility(View.VISIBLE);
                    int n = pages.size();
                    pageCountText.setText(n + (n == 1 ? " page" : " pages")
                            + " · Crop before saving");
                    pagesRecycler.setAdapter(new PageBitmapAdapter(pages, this::openCrop));
                    btnPrint.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    pageCountText.setText("Preview failed");
                    Toast.makeText(this, "Could not render preview: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void writePdf(List<String> paths, File outFile) throws Exception {
        PdfDocument doc = new PdfDocument();
        int pageNum = 1;
        for (String path : paths) {
            Bitmap bmp = decodeScan(path);
            if (bmp == null) continue;
            // Page bounds match the scan. The bitmap is drawn edge to edge so
            // print does not add a white mat around the captured page.
            int[] pageSize = pageSizeForScan(bmp.getWidth(), bmp.getHeight());
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                    pageSize[0], pageSize[1], pageNum++).create();
            PdfDocument.Page page = doc.startPage(info);
            Canvas c = page.getCanvas();
            c.drawBitmap(bmp, null, new Rect(0, 0, pageSize[0], pageSize[1]), null);
            doc.finishPage(page);
            bmp.recycle();
        }
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            doc.writeTo(fos);
        }
        doc.close();
    }

    /** Downsample only enough to stay in memory. The page is still filled by this bitmap. */
    private static Bitmap decodeScan(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longEdge / sample > 2400) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, opts);
    }

    /** Long edge is 11 inches. Aspect ratio stays the scan's, so the sheet is the scan. */
    private static int[] pageSizeForScan(int imageWidth, int imageHeight) {
        float longPts = 11f * 72f;
        float w = Math.max(1, imageWidth);
        float h = Math.max(1, imageHeight);
        if (w >= h) {
            int pageW = Math.round(longPts);
            int pageH = Math.max(1, Math.round(longPts * h / w));
            return new int[]{pageW, pageH};
        }
        int pageH = Math.round(longPts);
        int pageW = Math.max(1, Math.round(longPts * w / h));
        return new int[]{pageW, pageH};
    }

    private List<Bitmap> renderPdfPages(File pdfFile) throws Exception {
        List<Bitmap> pages = new ArrayList<>();
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {

            int count = renderer.getPageCount();
            int width = getResources().getDisplayMetrics().widthPixels - 64; // margins
            for (int i = 0; i < count; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                int renderWidth = width;
                int renderHeight = (int) ((float) pageHeight / pageWidth * renderWidth);

                Bitmap bmp = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                pages.add(bmp);
            }
        }
        return pages;
    }

    private void showSaveDialog() {
        String defaultName = "Scan_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        EditText input = new EditText(this);
        input.setText(defaultName);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
            .setTitle("Name your document")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = defaultName;
                if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
                savePermanently(name);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void savePermanently(String fileName) {
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        new Thread(() -> {
            try {
                byte[] pdfBytes;
                if (tempPdfFile != null && tempPdfFile.exists()) {
                    // Re-read temp file bytes
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(tempPdfFile)) {
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = fis.read(buf)) != -1) baos.write(buf, 0, read);
                    }
                    pdfBytes = baos.toByteArray();
                } else {
                    // Regenerate from images
                    File tmp = new File(getCacheDir(), "save_tmp.pdf");
                    writePdf(imagePaths, tmp);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(tmp)) {
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = fis.read(buf)) != -1) baos.write(buf, 0, read);
                    }
                    pdfBytes = baos.toByteArray();
                }

                FileManager fm = new FileManager(this);
                String path = fm.savePdf(pdfBytes, fileName, FileManager.CATEGORY_SCANNED);

                // Fallback to internal files dir
                if (path == null) {
                    File fallback = new File(getFilesDir(), fileName);
                    try (FileOutputStream fos = new FileOutputStream(fallback)) {
                        fos.write(pdfBytes);
                    }
                    path = fallback.getAbsolutePath();
                }

                final String finalPath = path;
                runOnUiThread(() -> {
                    savedFilePath = finalPath;
                    btnSave.setEnabled(true);
                    btnSave.setText("Save & Share");
                    btnShare.setVisibility(View.VISIBLE);

                    // Delete temp image files
                    for (String imgPath : imagePaths) new File(imgPath).delete();

                    new AlertDialog.Builder(this)
                        .setTitle("Saved!")
                        .setMessage("Your document was saved. Share or print it now?")
                        .setPositiveButton("Share", (d, w) -> shareFile(finalPath))
                        .setNeutralButton("Print", (d, w) ->
                                DocumentPrinter.printPdf(this, finalPath, "Scanned document"))
                        .setNegativeButton("Done", (d, w) -> {
                            setResult(RESULT_OK);
                            finish();
                        })
                        .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save & Share");
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void printCurrentDocument() {
        if (savedFilePath != null) {
            DocumentPrinter.printPdf(this, savedFilePath, "Scanned document");
        } else if (tempPdfFile != null && tempPdfFile.exists()) {
            DocumentPrinter.printPdf(this, tempPdfFile.getAbsolutePath(), "Scanned document");
        } else {
            Toast.makeText(this, "Document not ready to print", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                // Try sharing the temp preview file
                if (tempPdfFile != null && tempPdfFile.exists()) {
                    file = tempPdfFile;
                } else {
                    Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recyclePreviewPages();
        if (tempPdfFile != null && tempPdfFile.exists() && savedFilePath != null) {
            tempPdfFile.delete();
        }
    }

    // Simple adapter that shows pre-rendered page Bitmaps
    private static class PageBitmapAdapter extends RecyclerView.Adapter<PageBitmapAdapter.VH> {
        interface OnCropPage {
            void onCrop(int index);
        }

        private final List<Bitmap> pages;
        private final OnCropPage cropListener;

        PageBitmapAdapter(List<Bitmap> pages, OnCropPage cropListener) {
            this.pages = pages;
            this.cropListener = cropListener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pdf_preview_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Bitmap page = pages.get(pos);
            if (page != null && !page.isRecycled()) h.image.setImageBitmap(page);
            else h.image.setImageDrawable(null);
            h.crop.setOnClickListener(v -> {
                int index = h.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION && cropListener != null) {
                    cropListener.onCrop(index);
                }
            });
        }

        @Override
        public int getItemCount() { return pages.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            View crop;
            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.page_image);
                crop = v.findViewById(R.id.btn_crop_page);
            }
        }
    }

    private void openCrop(int index) {
        if (index < 0 || index >= imagePaths.size()) return;
        Intent intent = new Intent(this, ScanCropActivity.class);
        intent.putExtra(ScanCropActivity.EXTRA_IMAGE_PATH, imagePaths.get(index));
        cropLauncher.launch(intent);
    }

    private void recyclePreviewPages() {
        for (Bitmap page : previewPages) {
            if (page != null && !page.isRecycled()) page.recycle();
        }
        previewPages.clear();
    }
}
