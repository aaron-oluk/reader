package com.pdfreader.app;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;

import java.io.File;

public class PdfReaderActivity extends AppCompatActivity implements OnPageChangeListener,
        OnLoadCompleteListener, OnPageErrorListener {

    private PDFView pdfView;
    private String pdfPath;
    private String pdfTitle;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        pdfView = findViewById(R.id.pdfView);

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
    }

    private void displayPdf() {
        try {
            if (pdfPath.startsWith("content://")) {
                // Handle content URI
                Uri uri = Uri.parse(pdfPath);
                pdfView.fromUri(uri)
                        .defaultPage(currentPage)
                        .onPageChange(this)
                        .enableAnnotationRendering(true)
                        .onLoad(this)
                        .scrollHandle(new DefaultScrollHandle(this))
                        .spacing(10)
                        .onPageError(this)
                        .load();
            } else {
                // Handle file path
                File file = new File(pdfPath);
                if (file.exists()) {
                    pdfView.fromFile(file)
                            .defaultPage(currentPage)
                            .onPageChange(this)
                            .enableAnnotationRendering(true)
                            .onLoad(this)
                            .scrollHandle(new DefaultScrollHandle(this))
                            .spacing(10)
                            .onPageError(this)
                            .load();
                } else {
                    Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        currentPage = page;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(String.format("Page %d of %d", page + 1, pageCount));
        }
    }

    @Override
    public void loadComplete(int nbPages) {
        Toast.makeText(this, "PDF loaded successfully", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Toast.makeText(this, "Error on page " + page + ": " + t.getMessage(),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
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
