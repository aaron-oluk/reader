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

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PdfReaderActivity extends AppCompatActivity {

    private LinearLayout pagesContainer;
    private ScrollView scrollView;
    private CoordinatorLayout coordinatorLayout;
    private String pdfPath;
    private String pdfTitle;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private ReadingProgressManager progressManager;

    // UI Elements
    private TextView toolbarTitle;
    private TextView toolbarSubtitle;
    private TextView currentPageText;
    private TextView totalPagesText;
    private TextView pageIndicator;
    private SeekBar pageSlider;
    private SeekBar brightnessSlider;
    private LinearLayout themeSelector;
    private LinearLayout brightnessSliderContainer;
    private View bottomControls;
    private View topToolbar;

    // Theme state
    private enum ReaderTheme { LIGHT, SEPIA, DARK }
    private ReaderTheme currentTheme = ReaderTheme.LIGHT;

    // Page tracking
    private int pageCount = 0;
    private int currentPage = 1;
    private int[] pagePositions;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideIndicatorRunnable;

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
        pagesContainer = findViewById(R.id.pagesContainer);
        scrollView = findViewById(R.id.scrollView);
        toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarSubtitle = findViewById(R.id.toolbar_subtitle);
        currentPageText = findViewById(R.id.current_page_text);
        totalPagesText = findViewById(R.id.total_pages_text);
        pageIndicator = findViewById(R.id.page_indicator);
        pageSlider = findViewById(R.id.page_slider);
        brightnessSlider = findViewById(R.id.brightness_slider);
        themeSelector = findViewById(R.id.theme_selector);
        brightnessSliderContainer = findViewById(R.id.brightness_slider_container);
        bottomControls = findViewById(R.id.bottom_controls);
        topToolbar = findViewById(R.id.top_toolbar);
    }

    private void setupListeners() {
        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Share button
        ImageButton btnShare = findViewById(R.id.btn_share);
        btnShare.setOnClickListener(v -> shareDocument());

        // Theme button
        View btnTheme = findViewById(R.id.btn_theme);
        btnTheme.setOnClickListener(v -> toggleThemeSelector());

        // Brightness button
        View btnBrightness = findViewById(R.id.btn_brightness);
        btnBrightness.setOnClickListener(v -> toggleBrightnessSlider());

        // Theme options
        findViewById(R.id.theme_light).setOnClickListener(v -> setTheme(ReaderTheme.LIGHT));
        findViewById(R.id.theme_sepia).setOnClickListener(v -> setTheme(ReaderTheme.SEPIA));
        findViewById(R.id.theme_dark).setOnClickListener(v -> setTheme(ReaderTheme.DARK));

        // Page slider
        pageSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && pageCount > 0) {
                    int targetPage = Math.max(1, Math.min(pageCount, progress + 1));
                    currentPageText.setText(String.valueOf(targetPage));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (pageCount > 0) {
                    int targetPage = Math.max(1, Math.min(pageCount, seekBar.getProgress() + 1));
                    scrollToPage(targetPage);
                }
            }
        });

        // Brightness slider
        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float brightness = progress / 100f;
                    WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                    layoutParams.screenBrightness = Math.max(0.01f, brightness);
                    getWindow().setAttributes(layoutParams);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Scroll listener for page tracking
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            updateCurrentPageFromScroll();
        });
    }

    private void toggleThemeSelector() {
        if (themeSelector.getVisibility() == View.VISIBLE) {
            themeSelector.setVisibility(View.GONE);
        } else {
            themeSelector.setVisibility(View.VISIBLE);
            brightnessSliderContainer.setVisibility(View.GONE);
        }
    }

    private void toggleBrightnessSlider() {
        if (brightnessSliderContainer.getVisibility() == View.VISIBLE) {
            brightnessSliderContainer.setVisibility(View.GONE);
        } else {
            brightnessSliderContainer.setVisibility(View.VISIBLE);
            themeSelector.setVisibility(View.GONE);
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

        themeSelector.setVisibility(View.GONE);
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
            pagePositions = new int[pageCount];

            // Update UI
            toolbarSubtitle.setText(pageCount + " pages");
            totalPagesText.setText(String.valueOf(pageCount));
            pageSlider.setMax(Math.max(1, pageCount - 1));

            // Render all pages
            renderAllPages();

            // Restore scroll position after layout
            scrollView.post(() -> {
                int savedPosition = progressManager.getProgress(pdfPath);
                scrollView.scrollTo(0, savedPosition);
                updateCurrentPageFromScroll();
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            finish();
        }
    }

    private void renderAllPages() {
        if (pdfRenderer == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels - 32;
        int accumulatedHeight = 0;

        for (int i = 0; i < pageCount; i++) {
            PdfRenderer.Page page = pdfRenderer.openPage(i);

            float scale = (float) screenWidth / page.getWidth();
            int scaledWidth = screenWidth;
            int scaledHeight = (int) (page.getHeight() * scale);

            // Store page position for navigation
            pagePositions[i] = accumulatedHeight;
            accumulatedHeight += scaledHeight + 16; // 16dp margin

            Bitmap bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xFFFFFFFF);

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();

            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            imageView.setLayoutParams(params);

            pagesContainer.addView(imageView);
        }
    }

    private void scrollToPage(int pageNumber) {
        if (pageNumber < 1 || pageNumber > pageCount || pagePositions == null) return;

        int position = pagePositions[pageNumber - 1];
        scrollView.smoothScrollTo(0, position);
        currentPage = pageNumber;
        updatePageIndicators();
        showPageIndicator();
    }

    private void updateCurrentPageFromScroll() {
        if (pagePositions == null || pageCount == 0) return;

        int scrollY = scrollView.getScrollY();
        int newPage = 1;

        for (int i = 0; i < pageCount; i++) {
            if (scrollY >= pagePositions[i]) {
                newPage = i + 1;
            } else {
                break;
            }
        }

        if (newPage != currentPage) {
            currentPage = newPage;
            updatePageIndicators();
        }
    }

    private void updatePageIndicators() {
        currentPageText.setText(String.valueOf(currentPage));
        toolbarSubtitle.setText("Page " + currentPage + " of " + pageCount);
        pageSlider.setProgress(currentPage - 1);
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
        if (pdfPath != null && scrollView != null) {
            progressManager.saveProgress(pdfPath, scrollView.getScrollY());
        }
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

        if (hideIndicatorRunnable != null) {
            handler.removeCallbacks(hideIndicatorRunnable);
        }
    }
}
