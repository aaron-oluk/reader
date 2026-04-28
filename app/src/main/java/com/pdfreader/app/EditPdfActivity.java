package com.pdfreader.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

    // Bottom tool palette
    private View toolPalette;
    private LinearLayout toolOpenBtn, toolTextBtn, toolSignBtn;
    private MaterialCardView toolTextIconBg, toolSignIconBg;
    private ImageView toolTextIcon, toolSignIcon;

    // Mode hint bar
    private LinearLayout modeHintBar;
    private ImageView modeHintIcon;
    private TextView modeHintText;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private File currentPdfCacheFile;

    boolean isTextMode = false;
    boolean isSignMode = false;
    private boolean hasAnnotations = false;

    int currentTextColor = Color.BLACK;
    int currentTextSize = 16;

    final Map<Integer, List<TextAnnotation>> annotations = new HashMap<>();
    final Map<Integer, List<SignatureOverlay>> signatureOverlays = new HashMap<>();
    EditPageAdapter pageAdapter;

    private SignatureManager signatureManager;
    private ActivityResultLauncher<Intent> cameraSignatureLauncher;
    private Bitmap pendingSigBitmap = null;

    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        signatureManager = new SignatureManager(this);

        cameraSignatureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String savedPath = result.getData().getStringExtra(
                                ReviewSignatureActivity.EXTRA_SAVED_SIGNATURE_PATH);
                        if (savedPath != null) {
                            pendingSigBitmap = signatureManager.loadSignature(savedPath);
                            if (pendingSigBitmap != null) {
                                Toast.makeText(this,
                                        "Signature ready — tap a page to place it",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

        setContentView(R.layout.activity_edit_pdf);

        pagesRecycler    = findViewById(R.id.pages_recycler);
        loadingIndicator = findViewById(R.id.loading_indicator);
        emptyState       = findViewById(R.id.empty_state);
        pageCountText    = findViewById(R.id.page_count_text);
        btnSave          = findViewById(R.id.btn_save);

        toolPalette      = findViewById(R.id.tool_palette);
        toolOpenBtn      = findViewById(R.id.tool_open_btn);
        toolTextBtn      = findViewById(R.id.tool_text_btn);
        toolSignBtn      = findViewById(R.id.tool_sign_btn);
        toolTextIconBg   = findViewById(R.id.tool_text_icon_bg);
        toolSignIconBg   = findViewById(R.id.tool_sign_icon_bg);
        toolTextIcon     = findViewById(R.id.tool_text_icon);
        toolSignIcon     = findViewById(R.id.tool_sign_icon);

        modeHintBar      = findViewById(R.id.mode_hint_bar);
        modeHintIcon     = findViewById(R.id.mode_hint_icon);
        modeHintText     = findViewById(R.id.mode_hint_text);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_exit_mode).setOnClickListener(v -> exitAllModes());

        toolOpenBtn.setOnClickListener(v -> openFilePicker());
        toolTextBtn.setOnClickListener(v -> {
            if (pdfRenderer == null) {
                Toast.makeText(this, "Open a PDF first", Toast.LENGTH_SHORT).show();
                return;
            }
            setTextMode(!isTextMode);
        });
        toolSignBtn.setOnClickListener(v -> {
            if (pdfRenderer == null) {
                Toast.makeText(this, "Open a PDF first", Toast.LENGTH_SHORT).show();
                return;
            }
            setSignMode(!isSignMode);
        });

        btnSave.setOnClickListener(v -> saveAnnotatedPdf());
        pagesRecycler.setLayoutManager(new LinearLayoutManager(this));

        String passedPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        if (passedPath != null) loadFromPath(passedPath);
    }

    // ── File loading ──────────────────────────────────────────────────────────

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
        signatureOverlays.clear();
        for (int i = 0; i < count; i++) {
            annotations.put(i, new ArrayList<>());
            signatureOverlays.put(i, new ArrayList<>());
        }
        mainHandler.post(() -> {
            loadingIndicator.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            pagesRecycler.setVisibility(View.VISIBLE);
            pageCountText.setText(count + (count == 1 ? " page" : " pages"));
            btnSave.setEnabled(false);
            pageAdapter = new EditPageAdapter();
            pagesRecycler.setAdapter(pageAdapter);
        });
    }

    // ── Tool mode management ──────────────────────────────────────────────────

    void setTextMode(boolean on) {
        isTextMode = on;
        if (on) isSignMode = false;
        updateToolStates();
        if (on) {
            modeHintIcon.setImageResource(R.drawable.ic_draw);
            modeHintText.setText("Tap anywhere on a page to type  •  Done to finish");
            modeHintBar.setVisibility(View.VISIBLE);
            toolPalette.setVisibility(View.GONE);
        } else if (!isSignMode) {
            modeHintBar.setVisibility(View.GONE);
            toolPalette.setVisibility(View.VISIBLE);
        }
    }

    void setSignMode(boolean on) {
        isSignMode = on;
        if (on) isTextMode = false;
        updateToolStates();
        if (on) {
            modeHintIcon.setImageResource(R.drawable.ic_signature);
            modeHintText.setText("Tap anywhere on a page to place signature  •  Done to finish");
            modeHintBar.setVisibility(View.VISIBLE);
            toolPalette.setVisibility(View.GONE);
        } else if (!isTextMode) {
            modeHintBar.setVisibility(View.GONE);
            toolPalette.setVisibility(View.VISIBLE);
        }
    }

    private void exitAllModes() {
        isTextMode = false;
        isSignMode = false;
        updateToolStates();
        modeHintBar.setVisibility(View.GONE);
        toolPalette.setVisibility(View.VISIBLE);
    }

    private void updateToolStates() {
        applyToolState(toolTextIconBg, toolTextIcon, isTextMode);
        applyToolState(toolSignIconBg, toolSignIcon, isSignMode);
    }

    private void applyToolState(MaterialCardView bg, ImageView icon, boolean active) {
        if (active) {
            bg.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue));
            icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        } else {
            bg.setCardBackgroundColor(Color.parseColor("#EEF2FF"));
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue)));
        }
    }

    // ── Text annotation ───────────────────────────────────────────────────────

    void onPageTapped(int pageIndex, FrameLayout overlay, float tapX, float tapY) {
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
        lp.topMargin  = (int) tapY;
        et.setLayoutParams(lp);

        overlay.addView(et);
        et.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);

        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                commitEdit(et, overlay, pageIndex);
                return true;
            }
            return false;
        });

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

        float xF = et.getLeft()  / (float) Math.max(1, overlay.getWidth());
        float yF = (et.getTop() + et.getTextSize()) / (float) Math.max(1, overlay.getHeight());

        List<TextAnnotation> list = annotations.get(pageIndex);
        if (list != null) {
            list.add(new TextAnnotation(pageIndex, xF, yF, text, currentTextSize, currentTextColor));
        }
        hasAnnotations = true;
        btnSave.setEnabled(true);
        if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
    }

    // ── Signature placement ───────────────────────────────────────────────────

    void onSignPageTapped(int pageIndex, DraggableSignatureView sigOverlay, float tapX, float tapY) {
        if (pendingSigBitmap != null) {
            placeSignatureOnView(pageIndex, sigOverlay, pendingSigBitmap, tapX, tapY);
            pendingSigBitmap = null;
            return;
        }
        showSignatureSelectorDialog(pageIndex, sigOverlay, tapX, tapY);
    }

    private void showSignatureSelectorDialog(int pageIndex, DraggableSignatureView sigOverlay,
                                             float tapX, float tapY) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select or Create Signature");
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature_selector, null);

        RecyclerView recyclerView    = dialogView.findViewById(R.id.signatures_recycler);
        View cardDraw                = dialogView.findViewById(R.id.card_draw_signature);
        View cardCamera              = dialogView.findViewById(R.id.card_camera_signature);
        View emptyStateContainer     = dialogView.findViewById(R.id.empty_state_container);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        List<String> saved = signatureManager.getSavedSignatures();
        recyclerView.setVisibility(saved.isEmpty() ? View.GONE : View.VISIBLE);
        emptyStateContainer.setVisibility(saved.isEmpty() ? View.VISIBLE : View.GONE);

        SignatureAdapter adapter = new SignatureAdapter(saved, signatureManager);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnSignatureClickListener(filePath -> {
            Bitmap bmp = signatureManager.loadSignature(filePath);
            if (bmp != null) {
                placeSignatureOnView(pageIndex, sigOverlay, bmp, tapX, tapY);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Failed to load signature", Toast.LENGTH_SHORT).show();
            }
        });

        adapter.setOnSignatureDeleteListener(filePath -> new AlertDialog.Builder(this)
                .setTitle("Delete Signature")
                .setMessage("Are you sure you want to delete this signature?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (signatureManager.deleteSignature(filePath)) {
                        List<String> updated = signatureManager.getSavedSignatures();
                        adapter.updateSignatures(updated);
                        if (updated.isEmpty()) {
                            recyclerView.setVisibility(View.GONE);
                            emptyStateContainer.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(this, "Signature deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show());

        cardDraw.setOnClickListener(v -> {
            dialog.dismiss();
            showDrawSignatureDialog(pageIndex, sigOverlay, tapX, tapY);
        });

        cardCamera.setOnClickListener(v -> {
            dialog.dismiss();
            cameraSignatureLauncher.launch(new Intent(this, CaptureSignatureActivity.class));
        });

        dialog.show();
    }

    private void showDrawSignatureDialog(int pageIndex, DraggableSignatureView sigOverlay,
                                         float tapX, float tapY) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature, null);
        SignatureView signatureView = dialogView.findViewById(R.id.signatureView);
        Button btnClear = dialogView.findViewById(R.id.btnClear);
        Button btnDone  = dialogView.findViewById(R.id.btnDone);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnClear.setOnClickListener(v -> signatureView.clear());
        btnDone.setOnClickListener(v -> {
            if (signatureView.hasSignature()) {
                placeSignatureOnView(pageIndex, sigOverlay, signatureView.getSignatureBitmap(), tapX, tapY);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please draw your signature first", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void placeSignatureOnView(int pageIndex, DraggableSignatureView sigOverlay,
                                      Bitmap bitmap, float tapX, float tapY) {
        sigOverlay.setVisibility(View.VISIBLE);
        sigOverlay.post(() -> {
            int refW = sigOverlay.getWidth() > 0 ? sigOverlay.getWidth() : sigOverlay.getRootView().getWidth();
            int refH = sigOverlay.getHeight() > 0 ? sigOverlay.getHeight() : refW;
            float w = refW * 0.35f;
            float h = w * ((float) bitmap.getHeight() / Math.max(1, bitmap.getWidth()));

            float x = (tapX > 0) ? tapX - w / 2f : (refW - w) / 2f;
            float y = (tapY > 0) ? tapY - h / 2f : (refH - h) / 2f;
            x = Math.max(0, Math.min(x, refW - w));
            y = Math.max(0, Math.min(y, refH - h));

            sigOverlay.setSignature(bitmap, x, y, w, h);
        });

        sigOverlay.setOnSignatureAcceptedListener(() -> {
            float oW = sigOverlay.getWidth();
            float oH = sigOverlay.getHeight();
            if (oW <= 0 || oH <= 0) return;

            float xF = sigOverlay.getSignatureX()      / oW;
            float yF = sigOverlay.getSignatureY()      / oH;
            float wF = sigOverlay.getSignatureWidth()  / oW;
            float hF = sigOverlay.getSignatureHeight() / oH;

            List<SignatureOverlay> list = signatureOverlays.get(pageIndex);
            if (list == null) { list = new ArrayList<>(); signatureOverlays.put(pageIndex, list); }
            list.add(new SignatureOverlay(pageIndex, xF, yF, wF, hF, bitmap));

            sigOverlay.clearSignature();
            sigOverlay.setVisibility(View.GONE);
            hasAnnotations = true;
            btnSave.setEnabled(true);
            if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
        });

        sigOverlay.setOnSignatureDeletedListener(() -> {
            sigOverlay.clearSignature();
            sigOverlay.setVisibility(View.GONE);
        });
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveAnnotatedPdf() {
        if (pdfRenderer == null) return;
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        executor.execute(() -> {
            try {
                int pageCount  = pdfRenderer.getPageCount();
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
                        List<SignatureOverlay> sigs = signatureOverlays.get(i);
                        boolean hasText = anns != null && !anns.isEmpty();
                        boolean hasSigs = sigs != null && !sigs.isEmpty();

                        if (hasText || hasSigs) {
                            Canvas canvas = new Canvas(bmp);
                            if (hasText) {
                                Paint paint = new Paint();
                                paint.setAntiAlias(true);
                                paint.setTypeface(Typeface.DEFAULT);
                                for (TextAnnotation ann : anns) {
                                    paint.setColor(ann.color);
                                    paint.setTextSize(ann.textSize * scale);
                                    canvas.drawText(ann.text, ann.xFraction * w, ann.yFraction * h, paint);
                                }
                            }
                            if (hasSigs) {
                                Paint sigPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                                for (SignatureOverlay sig : sigs) {
                                    RectF dst = new RectF(
                                            sig.xFraction * w,
                                            sig.yFraction * h,
                                            (sig.xFraction + sig.widthFraction) * w,
                                            (sig.yFraction + sig.heightFraction) * h);
                                    canvas.drawBitmap(sig.bitmap, null, dst, sigPaint);
                                }
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

    // ── Models ────────────────────────────────────────────────────────────────

    static class TextAnnotation {
        int pageIndex;
        float xFraction, yFraction;
        String text;
        int textSize, color;

        TextAnnotation(int pi, float xF, float yF, String t, int sz, int col) {
            pageIndex = pi; xFraction = xF; yFraction = yF; text = t; textSize = sz; color = col;
        }
    }

    static class SignatureOverlay {
        int pageIndex;
        float xFraction, yFraction, widthFraction, heightFraction;
        Bitmap bitmap;

        SignatureOverlay(int pi, float xF, float yF, float wF, float hF, Bitmap bmp) {
            pageIndex = pi; xFraction = xF; yFraction = yF;
            widthFraction = wF; heightFraction = hF; bitmap = bmp;
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
            DraggableSignatureView signatureOverlay;
            int boundPos = -1;

            VH(@NonNull View v) {
                super(v);
                pageImage        = v.findViewById(R.id.page_image);
                progress         = v.findViewById(R.id.page_progress);
                pageNum          = v.findViewById(R.id.page_number);
                textOverlay      = v.findViewById(R.id.text_overlay);
                signatureOverlay = v.findViewById(R.id.signature_overlay);

                signatureOverlay.setOnSignatureDeletedListener(() -> {
                    signatureOverlay.clearSignature();
                    signatureOverlay.setVisibility(View.GONE);
                });

                final float[] tap = {0f, 0f};
                textOverlay.setOnTouchListener((vv, ev) -> {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        tap[0] = ev.getX();
                        tap[1] = ev.getY();
                    }
                    return false;
                });
                textOverlay.setOnClickListener(vv -> {
                    if (boundPos < 0) return;
                    if (isTextMode) {
                        onPageTapped(boundPos, textOverlay, tap[0], tap[1]);
                    } else if (isSignMode && !signatureOverlay.hasSignature()) {
                        onSignPageTapped(boundPos, signatureOverlay, tap[0], tap[1]);
                    }
                });
            }

            void bind(int position) {
                boundPos = position;
                pageNum.setText(String.valueOf(position + 1));
                progress.setVisibility(View.VISIBLE);
                pageImage.setImageBitmap(null);

                // Reset the signature overlay for recycled VHs
                signatureOverlay.clearSignature();
                signatureOverlay.setVisibility(View.GONE);

                executor.execute(() -> {
                    Bitmap bmp = renderPage(position);
                    if (bmp == null) {
                        mainHandler.post(() -> progress.setVisibility(View.GONE));
                        return;
                    }

                    List<TextAnnotation> anns = annotations.get(position);
                    List<SignatureOverlay> sigs = signatureOverlays.get(position);
                    boolean hasText = anns != null && !anns.isEmpty();
                    boolean hasSigs = sigs != null && !sigs.isEmpty();

                    if (hasText || hasSigs) {
                        Canvas c = new Canvas(bmp);
                        if (hasText) {
                            Paint p = new Paint();
                            p.setAntiAlias(true);
                            for (TextAnnotation ann : anns) {
                                p.setColor(ann.color);
                                p.setTextSize(ann.textSize);
                                c.drawText(ann.text,
                                        ann.xFraction * bmp.getWidth(),
                                        ann.yFraction * bmp.getHeight(), p);
                            }
                        }
                        if (hasSigs) {
                            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                            for (SignatureOverlay sig : sigs) {
                                RectF dst = new RectF(
                                        sig.xFraction * bmp.getWidth(),
                                        sig.yFraction * bmp.getHeight(),
                                        (sig.xFraction + sig.widthFraction) * bmp.getWidth(),
                                        (sig.yFraction + sig.heightFraction) * bmp.getHeight());
                                c.drawBitmap(sig.bitmap, null, dst, sp);
                            }
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
