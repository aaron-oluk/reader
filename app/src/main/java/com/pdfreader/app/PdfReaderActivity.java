package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PdfReaderActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CoordinatorLayout coordinatorLayout;
    private String pdfPath;
    private String pdfTitle;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private ReadingProgressManager progressManager;
    private PdfPageAdapter pdfPageAdapter;

    // UI Elements
    private TextView toolbarTitle;
    private TextView toolbarSubtitle;
    private TextView pageIndicator;
    private View topToolbar;

    // Theme state
    private enum ReaderTheme { LIGHT, SEPIA, DARK }
    private ReaderTheme currentTheme = ReaderTheme.LIGHT;

    // Page tracking
    private int pageCount = 0;
    private int currentPage = 1;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideIndicatorRunnable;
    private LinearLayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        initViews();
        setupListeners();

        progressManager = new ReadingProgressManager(this);

        pdfPath = getIntent().getStringExtra("PDF_PATH");
        pdfTitle = getIntent().getStringExtra("PDF_TITLE");

        if (pdfTitle != null) {
            toolbarTitle.setText(pdfTitle);
        }

        if (pdfPath != null && !pdfPath.isEmpty()) {
            displayPdf();
        } else {
            Toast.makeText(this, "Error: PDF path not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        recyclerView = findViewById(R.id.pdf_recycler_view);
        toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarSubtitle = findViewById(R.id.toolbar_subtitle);
        pageIndicator = findViewById(R.id.page_indicator);
        topToolbar = findViewById(R.id.top_toolbar);
        
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
    }

    private void setupListeners() {
        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Share button
        ImageButton btnShare = findViewById(R.id.btn_share);
        btnShare.setOnClickListener(v -> shareDocument());
        
        // Search button
        ImageButton btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> {
            Toast.makeText(this, "Search feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Theme button - cycles through themes
        View btnTheme = findViewById(R.id.btn_theme);
        btnTheme.setOnClickListener(v -> cycleTheme());

        // Scroll listener for page tracking
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateCurrentPageFromScroll();
            }
        });
    }

    private void cycleTheme() {
        // Cycle through: LIGHT -> SEPIA -> DARK -> LIGHT
        switch (currentTheme) {
            case LIGHT:
                setTheme(ReaderTheme.SEPIA);
                break;
            case SEPIA:
                setTheme(ReaderTheme.DARK);
                break;
            case DARK:
                setTheme(ReaderTheme.LIGHT);
                break;
        }
    }

    private void setTheme(ReaderTheme theme) {
        currentTheme = theme;
        int backgroundColor;
        int textColor;

        switch (theme) {
            case SEPIA:
                backgroundColor = ContextCompat.getColor(this, R.color.reader_background_sepia);
                textColor = ContextCompat.getColor(this, R.color.reader_text_sepia);
                break;
            case DARK:
                backgroundColor = ContextCompat.getColor(this, R.color.reader_background_dark);
                textColor = ContextCompat.getColor(this, R.color.reader_text_dark);
                break;
            default: // LIGHT
                backgroundColor = ContextCompat.getColor(this, R.color.reader_background_light);
                textColor = ContextCompat.getColor(this, R.color.reader_text_primary);
                break;
        }

        coordinatorLayout.setBackgroundColor(backgroundColor);
        topToolbar.setBackgroundColor(backgroundColor);
        toolbarTitle.setTextColor(textColor);
        toolbarSubtitle.setTextColor(textColor);
        
        // Show toast with current theme
        String themeName = theme == ReaderTheme.LIGHT ? "Light" : 
                          theme == ReaderTheme.SEPIA ? "Sepia" : "Dark";
        Toast.makeText(this, themeName + " theme", Toast.LENGTH_SHORT).show();
    }

    private void shareDocument() {
        if (pdfPath == null) return;

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");

            if (pdfPath.startsWith("content://")) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(pdfPath));
            } else {
                File file = new File(pdfPath);
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        file
                );
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            }

            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share document", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayPdf() {
        try {
            File file;
            if (pdfPath.startsWith("content://")) {
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

            pageCount = pdfRenderer.getPageCount();

            // Update UI
            toolbarSubtitle.setText(pageCount + " pages");

            // Setup RecyclerView adapter with lazy loading
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            pdfPageAdapter = new PdfPageAdapter(this, pdfRenderer, screenWidth);
            recyclerView.setAdapter(pdfPageAdapter);

            // Restore scroll position after layout
            recyclerView.post(() -> {
                int savedPage = progressManager.getProgress(pdfPath) / 1000; // Convert to page number
                if (savedPage > 0 && savedPage < pageCount) {
                    layoutManager.scrollToPositionWithOffset(savedPage, 0);
                }
                updateCurrentPageFromScroll();
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }
    }


    private void scrollToPage(int pageNumber) {
        if (pageNumber < 1 || pageNumber > pageCount) return;

        layoutManager.scrollToPositionWithOffset(pageNumber - 1, 0);
        currentPage = pageNumber;
        updatePageIndicators();
        showPageIndicator();
    }

    private void updateCurrentPageFromScroll() {
        if (pageCount == 0 || layoutManager == null) return;

        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        int newPage = firstVisiblePosition + 1;

        if (newPage > 0 && newPage != currentPage) {
            currentPage = newPage;
            updatePageIndicators();
        }
    }

    private void updatePageIndicators() {
        toolbarSubtitle.setText("Page " + currentPage + " of " + pageCount);
    }

    private void showPageIndicator() {
        pageIndicator.setText("Page " + currentPage + " of " + pageCount);
        pageIndicator.setVisibility(View.VISIBLE);

        if (hideIndicatorRunnable != null) {
            handler.removeCallbacks(hideIndicatorRunnable);
        }

        hideIndicatorRunnable = () -> pageIndicator.setVisibility(View.GONE);
        handler.postDelayed(hideIndicatorRunnable, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pdfPath != null && layoutManager != null) {
            // Save current page as progress
            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            progressManager.saveProgress(pdfPath, firstVisiblePosition * 1000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup adapter and bitmaps
        if (pdfPageAdapter != null) {
            pdfPageAdapter.cleanup();
        }
        
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

        if (hideIndicatorRunnable != null) {
            handler.removeCallbacks(hideIndicatorRunnable);
        }
        
        // Clear cache
        File cacheFile = new File(getCacheDir(), "temp.pdf");
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }
}
