package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_review);

        imagePaths = getIntent().getStringArrayListExtra(EXTRA_IMAGE_PATHS);
        if (imagePaths == null) imagePaths = new ArrayList<>();

        loadingIndicator = findViewById(R.id.loading_indicator);
        pagesRecycler = findViewById(R.id.pages_recycler);
        pageCountText = findViewById(R.id.page_count_text);
        btnShare = findViewById(R.id.btn_share);
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
                    loadingIndicator.setVisibility(View.GONE);
                    pagesRecycler.setVisibility(View.VISIBLE);
                    int n = pages.size();
                    pageCountText.setText(n + (n == 1 ? " page" : " pages"));
                    pagesRecycler.setAdapter(new PageBitmapAdapter(pages));
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
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) continue;
            bmp = scaleBitmap(bmp, 1240, 1754);
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                    bmp.getWidth(), bmp.getHeight(), pageNum++).create();
            PdfDocument.Page page = doc.startPage(info);
            // White background so the image sits on white paper
            Canvas c = page.getCanvas();
            c.drawColor(Color.WHITE);
            c.drawBitmap(bmp, 0, 0, null);
            doc.finishPage(page);
            bmp.recycle();
        }
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            doc.writeTo(fos);
        }
        doc.close();
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
                        .setMessage("Your document was saved. Share it now?")
                        .setPositiveButton("Share", (d, w) -> shareFile(finalPath))
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

    private Bitmap scaleBitmap(Bitmap src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        Bitmap scaled = Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
        src.recycle();
        return scaled;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up temp preview file
        if (tempPdfFile != null && tempPdfFile.exists() && savedFilePath != null) {
            tempPdfFile.delete();
        }
    }

    // Simple adapter that shows pre-rendered page Bitmaps
    private static class PageBitmapAdapter extends RecyclerView.Adapter<PageBitmapAdapter.VH> {
        private final List<Bitmap> pages;

        PageBitmapAdapter(List<Bitmap> pages) { this.pages = pages; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pdf_preview_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.image.setImageBitmap(pages.get(pos));
        }

        @Override
        public int getItemCount() { return pages.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.page_image);
            }
        }
    }
}
