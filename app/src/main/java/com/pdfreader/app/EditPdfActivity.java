package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditPdfActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_PATH = "pdf_path";

    private RecyclerView pagesRecycler;
    private ProgressBar loadingIndicator;
    private View emptyState;
    private TextView pageCountText;
    private MaterialButton btnSave;
    private MaterialButton btnToolText;
    private LinearLayout textModeBanner;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private File currentPdfCacheFile;
    boolean isTextMode = false;
    private boolean hasAnnotations = false;

    // Current text color and size for new annotations
    int currentTextColor = Color.BLACK;
    int currentTextSize = 16;

    // annotations per page
    final Map<Integer, List<TextAnnotation>> annotations = new HashMap<>();
    EditPageAdapter pageAdapter;

    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_pdf);

        pagesRecycler = findViewById(R.id.pages_recycler);
        loadingIndicator = findViewById(R.id.loading_indicator);
        emptyState = findViewById(R.id.empty_state);
        pageCountText = findViewById(R.id.page_count_text);
        btnSave = findViewById(R.id.btn_save);
        btnToolText = findViewById(R.id.btn_tool_text);
        textModeBanner = findViewById(R.id.text_mode_banner);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_select_pdf).setOnClickListener(v -> openFilePicker());
        findViewById(R.id.btn_exit_text_mode).setOnClickListener(v -> setTextMode(false));
        btnToolText.setOnClickListener(v -> setTextMode(!isTextMode));
        btnSave.setOnClickListener(v -> saveAnnotatedPdf());

        pagesRecycler.setLayoutManager(new LinearLayoutManager(this));

        String passedPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        if (passedPath != null) loadFromPath(passedPath);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null)
            loadPdf(data.getData());
    }

    private void loadFromPath(String path) {
        loadingIndicator.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                currentPdfCacheFile = new File(path);
                parcelFileDescriptor = ParcelFileDescriptor.open(currentPdfCacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(parcelFileDescriptor);
                onPdfLoaded();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Could not open PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadPdf(Uri uri) {
        loadingIndicator.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                currentPdfCacheFile = new File(getCacheDir(), "edit_temp.pdf");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(currentPdfCacheFile)) {
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                closePdfRenderer();
                parcelFileDescriptor = ParcelFileDescriptor.open(currentPdfCacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(parcelFileDescriptor);
                onPdfLoaded();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Could not open PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onPdfLoaded() {
        int count = pdfRenderer.getPageCount();
        annotations.clear();
        for (int i = 0; i < count; i++) annotations.put(i, new ArrayList<>());
        mainHandler.post(() -> {
            loadingIndicator.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            pagesRecycler.setVisibility(View.VISIBLE);
            pageCountText.setText(count + (count == 1 ? " page" : " pages"));
            btnToolText.setEnabled(true);
            btnSave.setEnabled(false);
            pageAdapter = new EditPageAdapter();
            pagesRecycler.setAdapter(pageAdapter);
        });
    }

    void setTextMode(boolean on) {
        isTextMode = on;
        btnToolText.setText(on ? "Text On" : "Add Text");
        textModeBanner.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    /** Called by VH when user taps on the page overlay in text mode. */
    void onPageTapped(int pageIndex, FrameLayout overlay, float tapX, float tapY) {
        // Place an inline EditText at the tap position
        EditText et = new EditText(this);
        et.setBackground(null);
        et.setTextColor(currentTextColor);
        et.setTextSize(currentTextSize);
        et.setHint("Type here…");
        et.setHintTextColor(0x66888888);
        et.setSingleLine(false);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setMaxLines(4);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) tapX;
        lp.topMargin = (int) tapY;
        et.setLayoutParams(lp);

        overlay.addView(et);
        et.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);

        // Commit on Done key
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                commitEdit(et, overlay, pageIndex);
                return true;
            }
            return false;
        });

        // Commit when focus is lost (user taps elsewhere)
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitEdit(et, overlay, pageIndex);
        });
    }

    private void commitEdit(EditText et, FrameLayout overlay, int pageIndex) {
        String text = et.getText().toString().trim();
        overlay.removeView(et);

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(overlay.getWindowToken(), 0);

        if (text.isEmpty()) return;

        // Coordinates as fractions of the page image dimensions
        float xF = et.getLeft() / (float) Math.max(1, overlay.getWidth());
        float yF = (et.getTop() + et.getTextSize()) / (float) Math.max(1, overlay.getHeight());

        List<TextAnnotation> list = annotations.get(pageIndex);
        if (list != null) {
            list.add(new TextAnnotation(pageIndex, xF, yF, text, currentTextSize, currentTextColor));
        }
        hasAnnotations = true;
        btnSave.setEnabled(true);

        if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
    }

    private void saveAnnotatedPdf() {
        if (pdfRenderer == null) return;
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        executor.execute(() -> {
            try {
                int pageCount = pdfRenderer.getPageCount();
                int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;

                PdfDocument doc = new PdfDocument();
                for (int i = 0; i < pageCount; i++) {
                    synchronized (pdfRenderer) {
                        PdfRenderer.Page page = pdfRenderer.openPage(i);
                        int w = screenWidth;
                        int h = (int) ((float) page.getHeight() / page.getWidth() * w);
                        float scale = (float) w / page.getWidth();

                        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        page.close();

                        List<TextAnnotation> anns = annotations.get(i);
                        if (anns != null && !anns.isEmpty()) {
                            Canvas canvas = new Canvas(bmp);
                            Paint paint = new Paint();
                            paint.setAntiAlias(true);
                            paint.setTypeface(Typeface.DEFAULT);
                            for (TextAnnotation ann : anns) {
                                paint.setColor(ann.color);
                                paint.setTextSize(ann.textSize * scale);
                                canvas.drawText(ann.text, ann.xFraction * w, ann.yFraction * h, paint);
                            }
                        }

                        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(w, h, i + 1).create();
                        PdfDocument.Page docPage = doc.startPage(info);
                        docPage.getCanvas().drawBitmap(bmp, 0, 0, null);
                        doc.finishPage(docPage);
                        bmp.recycle();
                    }
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.writeTo(baos);
                doc.close();
                byte[] bytes = baos.toByteArray();

                String fileName = "edited_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
                FileManager fm = new FileManager(this);
                String saved = fm.savePdf(bytes, fileName, FileManager.CATEGORY_SIGNED);
                if (saved == null) {
                    File fallback = new File(getFilesDir(), fileName);
                    try (FileOutputStream fos = new FileOutputStream(fallback)) { fos.write(bytes); }
                    saved = fallback.getAbsolutePath();
                }
                final String finalPath = saved;

                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                    new AlertDialog.Builder(this)
                        .setTitle("Saved")
                        .setMessage("PDF saved. Share it now?")
                        .setPositiveButton("Share", (d, w) -> sharePdf(finalPath))
                        .setNegativeButton("Done", null)
                        .show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void sharePdf(String path) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", new File(path));
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void closePdfRenderer() {
        try {
            if (pdfRenderer != null) { pdfRenderer.close(); pdfRenderer = null; }
            if (parcelFileDescriptor != null) { parcelFileDescriptor.close(); parcelFileDescriptor = null; }
        } catch (IOException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        closePdfRenderer();
    }

    // ── TextAnnotation model ──────────────────────────────────────────────────

    static class TextAnnotation {
        int pageIndex;
        float xFraction, yFraction;
        String text;
        int textSize;
        int color;

        TextAnnotation(int pi, float xF, float yF, String t, int sz, int col) {
            pageIndex = pi; xFraction = xF; yFraction = yF; text = t; textSize = sz; color = col;
        }
    }

    // ── Page adapter ──────────────────────────────────────────────────────────

    class EditPageAdapter extends RecyclerView.Adapter<EditPageAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_edit_pdf_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) { h.bind(position); }

        @Override
        public int getItemCount() { return pdfRenderer != null ? pdfRenderer.getPageCount() : 0; }

        void refreshPage(int index) { notifyItemChanged(index); }

        class VH extends RecyclerView.ViewHolder {
            ImageView pageImage;
            ProgressBar progress;
            TextView pageNum;
            FrameLayout textOverlay;
            int boundPos = -1;

            VH(@NonNull View v) {
                super(v);
                pageImage = v.findViewById(R.id.page_image);
                progress = v.findViewById(R.id.page_progress);
                pageNum = v.findViewById(R.id.page_number);
                textOverlay = v.findViewById(R.id.text_overlay);

                final float[] tap = {0f, 0f};
                textOverlay.setOnTouchListener((vv, ev) -> {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        tap[0] = ev.getX();
                        tap[1] = ev.getY();
                    }
                    return false;
                });
                textOverlay.setOnClickListener(vv -> {
                    if (isTextMode && boundPos >= 0) {
                        onPageTapped(boundPos, textOverlay, tap[0], tap[1]);
                    }
                });
            }

            void bind(int position) {
                boundPos = position;
                pageNum.setText(String.valueOf(position + 1));
                progress.setVisibility(View.VISIBLE);
                pageImage.setImageBitmap(null);

                executor.execute(() -> {
                    Bitmap bmp = renderPage(position);
                    if (bmp == null) {
                        mainHandler.post(() -> progress.setVisibility(View.GONE));
                        return;
                    }
                    // Draw committed annotations
                    List<TextAnnotation> anns = annotations.get(position);
                    if (anns != null && !anns.isEmpty()) {
                        Canvas c = new Canvas(bmp);
                        Paint p = new Paint();
                        p.setAntiAlias(true);
                        for (TextAnnotation ann : anns) {
                            p.setColor(ann.color);
                            p.setTextSize(ann.textSize);
                            c.drawText(ann.text, ann.xFraction * bmp.getWidth(),
                                    ann.yFraction * bmp.getHeight(), p);
                        }
                    }
                    final Bitmap finalBmp = bmp;
                    mainHandler.post(() -> {
                        if (getBindingAdapterPosition() == position) {
                            pageImage.setImageBitmap(finalBmp);
                            progress.setVisibility(View.GONE);
                        } else {
                            finalBmp.recycle();
                        }
                    });
                });
            }

            private Bitmap renderPage(int index) {
                if (pdfRenderer == null) return null;
                try {
                    synchronized (pdfRenderer) {
                        PdfRenderer.Page page = pdfRenderer.openPage(index);
                        int screenW = getResources().getDisplayMetrics().widthPixels - 64;
                        int h = (int) ((float) page.getHeight() / page.getWidth() * screenW);
                        Bitmap bmp = Bitmap.createBitmap(screenW, h, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();
                        return bmp;
                    }
                } catch (Exception e) { return null; }
            }
        }
    }
}
