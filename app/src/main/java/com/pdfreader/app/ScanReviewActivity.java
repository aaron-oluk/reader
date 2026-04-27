package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
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
    private ScanPageAdapter adapter;
    private TextView pageCountText;
    private View btnShare;
    private String savedFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_review);

        imagePaths = getIntent().getStringArrayListExtra(EXTRA_IMAGE_PATHS);
        if (imagePaths == null) imagePaths = new ArrayList<>();

        pageCountText = findViewById(R.id.page_count_text);
        btnShare = findViewById(R.id.btn_share);

        RecyclerView recycler = findViewById(R.id.pages_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new ScanPageAdapter(imagePaths);
        adapter.setOnPageDeleteListener(position -> {
            imagePaths.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, imagePaths.size());
            updatePageCount();
            if (imagePaths.isEmpty()) finish();
        });
        recycler.setAdapter(adapter);
        updatePageCount();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((MaterialButton) findViewById(R.id.btn_add_page)).setOnClickListener(v -> {
            setResult(RESULT_ADD_MORE);
            finish();
        });

        ((MaterialButton) findViewById(R.id.btn_save_pdf)).setOnClickListener(v -> showSaveDialog());

        btnShare.setOnClickListener(v -> {
            if (savedFilePath != null) shareFile(savedFilePath);
        });
    }

    private void updatePageCount() {
        int n = imagePaths.size();
        pageCountText.setText(n + (n == 1 ? " page" : " pages"));
    }

    private void showSaveDialog() {
        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "No pages to save", Toast.LENGTH_SHORT).show();
            return;
        }

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
                savePdf(name);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void savePdf(String fileName) {
        MaterialButton btnSave = findViewById(R.id.btn_save_pdf);
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        final String finalFileName = fileName;
        new Thread(() -> {
            try {
                PdfDocument document = new PdfDocument();

                for (String path : imagePaths) {
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    if (bitmap == null) continue;

                    // Scale to A4-ish at 150 dpi: max 1240×1754
                    bitmap = scaleBitmap(bitmap, 1240, 1754);

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                            bitmap.getWidth(), bitmap.getHeight(),
                            document.getPages().size() + 1).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    page.getCanvas().drawBitmap(bitmap, 0, 0, null);
                    document.finishPage(page);
                    bitmap.recycle();
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                document.writeTo(baos);
                document.close();
                byte[] pdfBytes = baos.toByteArray();

                FileManager fileManager = new FileManager(this);
                String savedPath = fileManager.savePdf(pdfBytes, finalFileName, FileManager.CATEGORY_SCANNED);

                // Fallback: write directly to app files dir
                if (savedPath == null) {
                    File fallback = new File(getFilesDir(), finalFileName);
                    try (FileOutputStream fos = new FileOutputStream(fallback)) {
                        fos.write(pdfBytes);
                    }
                    savedPath = fallback.getAbsolutePath();
                }

                final String finalPath = savedPath;
                runOnUiThread(() -> {
                    savedFilePath = finalPath;
                    btnSave.setEnabled(true);
                    btnSave.setText("Save PDF");
                    btnShare.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Saved to Scanned folder", Toast.LENGTH_SHORT).show();

                    // Offer share immediately
                    new AlertDialog.Builder(this)
                        .setTitle("Document saved")
                        .setMessage("Your scanned PDF has been saved. Would you like to share it?")
                        .setPositiveButton("Share", (d, w) -> shareFile(finalPath))
                        .setNegativeButton("Done", null)
                        .show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save PDF");
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void shareFile(String filePath) {
        try {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share file", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap scaleBitmap(Bitmap src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }
}
