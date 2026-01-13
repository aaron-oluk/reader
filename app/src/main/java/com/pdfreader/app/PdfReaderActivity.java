package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PdfReaderActivity extends AppCompatActivity {

    private LinearLayout pagesContainer;
    private ScrollView scrollView;
    private String pdfPath;
    private String pdfTitle;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private ReadingProgressManager progressManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        pagesContainer = findViewById(R.id.pagesContainer);
        scrollView = findViewById(R.id.scrollView);
        progressManager = new ReadingProgressManager(this);

        pdfPath = getIntent().getStringExtra("PDF_PATH");
        pdfTitle = getIntent().getStringExtra("PDF_TITLE");

        if (pdfTitle != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pdfTitle);
        }

        if (pdfPath != null && !pdfPath.isEmpty()) {
            displayPdf();
        } else {
            Toast.makeText(this, "Error: PDF path not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void displayPdf() {
        try {
            File file;
            if (pdfPath.startsWith("content://")) {
                // Handle content URI - copy to cache
                Uri uri = Uri.parse(pdfPath);
                file = new File(getCacheDir(), "temp.pdf");
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    Toast.makeText(this, "Cannot open PDF file", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                FileOutputStream outputStream = new FileOutputStream(file);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();
            } else {
                file = new File(pdfPath);
            }

            if (!file.exists()) {
                Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);

            int pageCount = pdfRenderer.getPageCount();

            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(pageCount + " pages");
            }

            // Render all pages
            renderAllPages();

            // Restore scroll position after layout
            scrollView.post(() -> {
                int savedPosition = progressManager.getProgress(pdfPath);
                scrollView.scrollTo(0, savedPosition);
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }
    }

    private void renderAllPages() {
        if (pdfRenderer == null) return;

        int pageCount = pdfRenderer.getPageCount();

        // Get screen width for scaling
        int screenWidth = getResources().getDisplayMetrics().widthPixels - 32; // Account for padding

        for (int i = 0; i < pageCount; i++) {
            PdfRenderer.Page page = pdfRenderer.openPage(i);

            // Calculate scale to fit screen width
            float scale = (float) screenWidth / page.getWidth();
            int scaledWidth = screenWidth;
            int scaledHeight = (int) (page.getHeight() * scale);

            Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xFFFFFFFF); // White background

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();

            // Create ImageView for this page
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);

            // Add margin between pages
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            imageView.setLayoutParams(params);

            pagesContainer.addView(imageView);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save scroll position
        if (pdfPath != null && scrollView != null) {
            progressManager.saveProgress(pdfPath, scrollView.getScrollY());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) {
                pdfRenderer.close();
            }
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
