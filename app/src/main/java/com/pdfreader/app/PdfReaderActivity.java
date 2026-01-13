package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PdfReaderActivity extends AppCompatActivity {

    private ImageView pdfImageView;
    private Button prevButton, nextButton;
    private String pdfPath;
    private String pdfTitle;
    private int currentPage = 0;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPdfPage;
    private ParcelFileDescriptor parcelFileDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        pdfImageView = findViewById(R.id.pdfImageView);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);

        pdfPath = getIntent().getStringExtra("PDF_PATH");
        pdfTitle = getIntent().getStringExtra("PDF_TITLE");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pdfTitle != null ? pdfTitle : "PDF Reader");
        }

        if (pdfPath != null) {
            displayPdf();
        } else {
            Toast.makeText(this, "Error: PDF path not found", Toast.LENGTH_SHORT).show();
            finish();
        }

        prevButton.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                showPage(currentPage);
            }
        });

        nextButton.setOnClickListener(v -> {
            if (pdfRenderer != null && currentPage < pdfRenderer.getPageCount() - 1) {
                currentPage++;
                showPage(currentPage);
            }
        });
    }

    private void displayPdf() {
        try {
            File file;
            if (pdfPath.startsWith("content://")) {
                // Handle content URI - copy to cache
                Uri uri = Uri.parse(pdfPath);
                file = new File(getCacheDir(), "temp.pdf");
                InputStream inputStream = getContentResolver().openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
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
            
            showPage(currentPage);

        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }
    }

    private void showPage(int index) {
        if (pdfRenderer == null) return;

        if (currentPdfPage != null) {
            currentPdfPage.close();
        }

        currentPdfPage = pdfRenderer.openPage(index);
        
        Bitmap bitmap = Bitmap.createBitmap(currentPdfPage.getWidth() * 2, 
                                           currentPdfPage.getHeight() * 2, 
                                           Bitmap.Config.ARGB_8888);
        currentPdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        
        pdfImageView.setImageBitmap(bitmap);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(
                String.format("Page %d of %d", index + 1, pdfRenderer.getPageCount())
            );
        }

        prevButton.setEnabled(index > 0);
        nextButton.setEnabled(index < pdfRenderer.getPageCount() - 1);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (currentPdfPage != null) {
                currentPdfPage.close();
            }
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentPage", currentPage);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentPage = savedInstanceState.getInt("currentPage", 0);
    }
}
