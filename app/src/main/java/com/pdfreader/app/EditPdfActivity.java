package com.pdfreader.app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.util.TypedValue;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditPdfActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_PATH = "pdf_path";
    public static final String EXTRA_PDF_TITLE = "pdf_title";

    private RecyclerView pagesRecycler;
    private ProgressBar loadingIndicator;
    private View emptyState;
    private TextView pageCountText;
    private MaterialButton btnSave;

    // Bottom tool palette
    private View toolPalette;
    private View toolOpenBtn;
    private LinearLayout toolTextBtn, toolSignBtn, toolDateBtn;
    private MaterialCardView toolTextIconBg, toolSignIconBg, toolDateIconBg;
    private ImageView toolTextIcon, toolSignIcon, toolDateIcon;
    private TextView toolTextLabel, toolSignLabel, toolDateLabel;
    private View btnReplacePdf;

    // Mode hint bar
    private LinearLayout modeHintBar;
    private ImageView modeHintIcon;
    private TextView modeHintText;
    private View textStyleBar;
    private TextView styleSizeLabel;
    private MaterialCardView styleBoldBtn;
    private TextView styleBoldLabel;
    private LinearLayout styleColorRow;

    // Success state
    private View successState;
    private TextView successFileName;
    private TextView successFileMeta;
    private String lastSavedPdfPath;

    private PdfBoxRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private File currentPdfCacheFile;

    boolean isTextMode = false;
    boolean isSignMode = false;
    boolean isDateMode = false;
    private boolean hasAnnotations = false;

    int currentTextColor = Color.BLACK;
    int currentTextSizeSp = 14;
    boolean currentTextBold = false;
    private int selectedDateFormatIndex = 0;
    private Calendar stampCalendar = Calendar.getInstance();

    private static final String[] DATE_FORMAT_PATTERNS = {
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "MMM d, yyyy",
            "d MMMM yyyy",
            "EEEE, MMM d, yyyy",
            "MM/dd/yy"
    };

    private static final int[] TEXT_COLORS = {
            Color.BLACK, 0xFF1E1B4B, 0xFF2563EB, 0xFFDC2626, 0xFF10B981, 0xFFD97706
    };
    private static final int[] TEXT_SIZE_STEPS = {6, 7, 8, 9, 10, 12, 14, 16, 18, 22, 28, 36};

    final Map<Integer, List<TextAnnotation>> annotations = new HashMap<>();
    final Map<Integer, List<SignatureOverlay>> signatureOverlays = new HashMap<>();
    EditPageAdapter pageAdapter;

    private SignatureManager signatureManager;
    private ActivityResultLauncher<Intent> cameraSignatureLauncher;
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private Bitmap pendingSigBitmap = null;
    private View selectedAnnotationView = null;
    /** After cross-page transfer, reopen edit chrome on this model once views rebuild. */
    private SignatureOverlay signatureToResumeEditing = null;

    /** Live on-page text editor — kept above the IME via scroll + margin lift. */
    private View activeLiveEditBox = null;
    private FrameLayout activeLiveEditOverlay = null;
    private int liveEditBaseTopMargin = 0;
    private int liveEditKeyboardLift = 0;
    private int pagesRecyclerBasePadBottom = -1;
    private final Runnable liveEditImeRelayout = this::relayoutLiveEditorForIme;

    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowInsetsHelper.enableEdgeToEdge(this, true);
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

        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null
                            && result.getData().getData() != null) {
                        loadPdf(result.getData().getData());
                    }
                });

        setContentView(R.layout.activity_edit_pdf);

        View editAppBar = findViewById(R.id.app_bar);
        if (editAppBar != null) {
            WindowInsetsHelper.applyAppBarInsets(editAppBar);
        }
        View successAppBar = findViewById(R.id.success_app_bar);
        if (successAppBar != null) {
            WindowInsetsHelper.applyAppBarInsets(successAppBar);
        }

        pagesRecycler    = findViewById(R.id.pages_recycler);
        loadingIndicator = findViewById(R.id.loading_indicator);
        emptyState       = findViewById(R.id.empty_state);
        pageCountText    = findViewById(R.id.page_count_text);
        btnSave          = findViewById(R.id.btn_save);

        toolPalette      = findViewById(R.id.tool_palette);
        toolOpenBtn      = findViewById(R.id.tool_open_btn);
        toolTextBtn      = findViewById(R.id.tool_text_btn);
        toolSignBtn      = findViewById(R.id.tool_sign_btn);
        toolDateBtn      = findViewById(R.id.tool_date_btn);
        toolTextIconBg   = findViewById(R.id.tool_text_icon_bg);
        toolSignIconBg   = findViewById(R.id.tool_sign_icon_bg);
        toolDateIconBg   = findViewById(R.id.tool_date_icon_bg);
        toolTextIcon     = findViewById(R.id.tool_text_icon);
        toolSignIcon     = findViewById(R.id.tool_sign_icon);
        toolDateIcon     = findViewById(R.id.tool_date_icon);
        toolTextLabel    = findViewById(R.id.tool_text_label);
        toolSignLabel    = findViewById(R.id.tool_sign_label);
        toolDateLabel    = findViewById(R.id.tool_date_label);
        btnReplacePdf    = findViewById(R.id.btn_replace_pdf);

        modeHintBar      = findViewById(R.id.mode_hint_bar);
        modeHintIcon     = findViewById(R.id.mode_hint_icon);
        modeHintText     = findViewById(R.id.mode_hint_text);
        textStyleBar     = findViewById(R.id.text_style_bar);
        styleSizeLabel   = findViewById(R.id.style_size_label);
        styleBoldBtn     = findViewById(R.id.style_bold_btn);
        styleBoldLabel   = findViewById(R.id.style_bold_label);
        styleColorRow    = findViewById(R.id.style_color_row);

        setupTextStyleBar();

        successState     = findViewById(R.id.success_state);
        successFileName  = findViewById(R.id.success_file_name);
        successFileMeta  = findViewById(R.id.success_file_meta);
        findViewById(R.id.success_btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.success_btn_share).setOnClickListener(v -> {
            if (lastSavedPdfPath != null) sharePdf(lastSavedPdfPath);
        });
        findViewById(R.id.success_btn_back_to_documents).setOnClickListener(v -> finish());
        findViewById(R.id.success_btn_view_history).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivityNew.class);
            intent.putExtra("open_tab", R.id.navigation_library);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_exit_mode).setOnClickListener(v -> exitAllModes());

        if (btnReplacePdf != null) {
            btnReplacePdf.setOnClickListener(v -> openFilePicker());
        }
        if (toolOpenBtn != null) {
            toolOpenBtn.setOnClickListener(v -> openFilePicker());
        }

        View btnOpenPdfEmpty = findViewById(R.id.btn_open_pdf_empty);
        if (btnOpenPdfEmpty != null) btnOpenPdfEmpty.setOnClickListener(v -> openFilePicker());
        View btnRecentFilesEmpty = findViewById(R.id.btn_recent_files_empty);
        if (btnRecentFilesEmpty != null) btnRecentFilesEmpty.setOnClickListener(v -> openFilePicker());
        View btnScanEmpty = findViewById(R.id.btn_scan_empty);
        if (btnScanEmpty != null) btnScanEmpty.setOnClickListener(v ->
                startActivity(new Intent(this, ScanDocumentActivity.class)));
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
        if (toolDateBtn != null) {
            toolDateBtn.setOnClickListener(v -> {
                if (pdfRenderer == null) {
                    Toast.makeText(this, "Open a PDF first", Toast.LENGTH_SHORT).show();
                    return;
                }
                setDateMode(!isDateMode);
            });
        }

        btnSave.setOnClickListener(v -> saveAnnotatedPdf());
        pagesRecycler.setLayoutManager(new LinearLayoutManager(this));

        // Start without editing dock until a document is loaded
        updateDocumentChrome(false);

        String passedPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        if (passedPath != null && !passedPath.isEmpty()) {
            if (passedPath.startsWith("content://")) {
                loadPdf(Uri.parse(passedPath));
            } else {
                loadFromPath(passedPath);
            }
            String title = getIntent().getStringExtra(EXTRA_PDF_TITLE);
            if (title != null && !title.isEmpty()) {
                pageCountText.setText(title);
            }
        }
    }

    // ── File loading ──────────────────────────────────────────────────────────

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPickerLauncher.launch(intent);
    }

    private void loadFromPath(String path) {
        loadingIndicator.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                closePdfRenderer();
                currentPdfCacheFile = new File(path);
                parcelFileDescriptor = ParcelFileDescriptor.open(currentPdfCacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfBoxRenderer(this, parcelFileDescriptor);
                onPdfLoaded();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                    pagesRecycler.setVisibility(View.GONE);
                    updateDocumentChrome(false);
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
                pdfRenderer = new PdfBoxRenderer(this, parcelFileDescriptor);
                onPdfLoaded();
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                    pagesRecycler.setVisibility(View.GONE);
                    updateDocumentChrome(false);
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
            String title = getIntent().getStringExtra(EXTRA_PDF_TITLE);
            if (title != null && !title.isEmpty()) {
                pageCountText.setText(title + "  ·  " + count + (count == 1 ? " page" : " pages"));
            } else {
                pageCountText.setText(count + (count == 1 ? " page" : " pages"));
            }
            btnSave.setEnabled(false);
            pageAdapter = new EditPageAdapter();
            pagesRecycler.setAdapter(pageAdapter);
            updateDocumentChrome(true);
        });
    }

    // ── Tool mode management ──────────────────────────────────────────────────

    void setTextMode(boolean on) {
        isTextMode = on;
        if (on) {
            isSignMode = false;
            isDateMode = false;
        }
        updateToolStates();
        if (on) {
            modeHintIcon.setImageResource(R.drawable.ic_draw);
            modeHintText.setText("Tap to add text · tap existing text to edit");
            modeHintBar.setVisibility(View.VISIBLE);
            if (textStyleBar != null) textStyleBar.setVisibility(View.VISIBLE);
            refreshTextStyleBar();
            toolPalette.setVisibility(View.GONE);
        } else if (!isSignMode && !isDateMode) {
            modeHintBar.setVisibility(View.GONE);
            if (textStyleBar != null) textStyleBar.setVisibility(View.GONE);
            showEditingDock();
        } else {
            if (textStyleBar != null) textStyleBar.setVisibility(View.GONE);
        }
    }

    void setSignMode(boolean on) {
        isSignMode = on;
        if (on) {
            isTextMode = false;
            isDateMode = false;
        }
        updateToolStates();
        if (on) {
            modeHintIcon.setImageResource(R.drawable.ic_signature);
            modeHintText.setText("Tap to place · drag to move · ✓ to confirm");
            modeHintBar.setVisibility(View.VISIBLE);
            if (textStyleBar != null) textStyleBar.setVisibility(View.GONE);
            toolPalette.setVisibility(View.GONE);
        } else if (!isTextMode && !isDateMode) {
            modeHintBar.setVisibility(View.GONE);
            showEditingDock();
        }
    }

    void setDateMode(boolean on) {
        isDateMode = on;
        if (on) {
            isTextMode = false;
            isSignMode = false;
        }
        updateToolStates();
        if (on) {
            modeHintIcon.setImageResource(R.drawable.ic_calendar);
            modeHintText.setText("Tap a page to place a date stamp");
            modeHintBar.setVisibility(View.VISIBLE);
            if (textStyleBar != null) textStyleBar.setVisibility(View.GONE);
            toolPalette.setVisibility(View.GONE);
        } else if (!isTextMode && !isSignMode) {
            modeHintBar.setVisibility(View.GONE);
            showEditingDock();
        }
    }

    private void exitAllModes() {
        isTextMode = false;
        isSignMode = false;
        isDateMode = false;
        updateToolStates();
        modeHintBar.setVisibility(View.GONE);
        if (textStyleBar != null) textStyleBar.setVisibility(View.GONE);
        deselectAllSignatures();
        showEditingDock();
    }

    private void showEditingDock() {
        if (pdfRenderer != null) {
            toolPalette.setVisibility(View.VISIBLE);
        }
    }

    /** Empty state vs loaded-document chrome. */
    private void updateDocumentChrome(boolean loaded) {
        if (btnReplacePdf != null) {
            btnReplacePdf.setVisibility(loaded ? View.VISIBLE : View.GONE);
        }
        if (!isTextMode && !isSignMode && !isDateMode) {
            toolPalette.setVisibility(loaded ? View.VISIBLE : View.GONE);
        }
    }

    private void updateToolStates() {
        applyToolState(toolTextIconBg, toolTextIcon, toolTextLabel, isTextMode);
        applyToolState(toolSignIconBg, toolSignIcon, toolSignLabel, isSignMode);
        if (toolDateIconBg != null) {
            applyToolState(toolDateIconBg, toolDateIcon, toolDateLabel, isDateMode);
        }
    }

    private void applyToolState(MaterialCardView bg, ImageView icon, TextView label, boolean active) {
        if (active) {
            bg.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue));
            icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            if (label != null) label.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
        } else {
            bg.setCardBackgroundColor(Color.parseColor("#EEF2FF"));
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_blue)));
            if (label != null) label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    // ── Text style defaults ───────────────────────────────────────────────────

    private void setupTextStyleBar() {
        View sizeDown = findViewById(R.id.style_size_down);
        View sizeUp = findViewById(R.id.style_size_up);
        if (sizeDown != null) {
            sizeDown.setOnClickListener(v -> {
                currentTextSizeSp = stepTextSize(currentTextSizeSp, -1);
                refreshTextStyleBar();
            });
        }
        if (sizeUp != null) {
            sizeUp.setOnClickListener(v -> {
                currentTextSizeSp = stepTextSize(currentTextSizeSp, 1);
                refreshTextStyleBar();
            });
        }
        if (styleBoldBtn != null) {
            styleBoldBtn.setOnClickListener(v -> {
                currentTextBold = !currentTextBold;
                refreshTextStyleBar();
            });
        }
        refreshTextStyleBar();
    }

    private int stepTextSize(int currentSp, int direction) {
        int idx = 0;
        for (int i = 0; i < TEXT_SIZE_STEPS.length; i++) {
            if (TEXT_SIZE_STEPS[i] >= currentSp) {
                idx = i;
                break;
            }
            idx = i;
        }
        for (int i = 0; i < TEXT_SIZE_STEPS.length; i++) {
            if (TEXT_SIZE_STEPS[i] == currentSp) {
                idx = i;
                break;
            }
        }
        idx = Math.max(0, Math.min(TEXT_SIZE_STEPS.length - 1, idx + direction));
        return TEXT_SIZE_STEPS[idx];
    }

    private void refreshTextStyleBar() {
        if (styleSizeLabel != null) {
            styleSizeLabel.setText(String.valueOf(currentTextSizeSp));
        }
        if (styleBoldBtn != null && styleBoldLabel != null) {
            if (currentTextBold) {
                styleBoldBtn.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue));
                styleBoldLabel.setTextColor(Color.WHITE);
            } else {
                styleBoldBtn.setCardBackgroundColor(Color.parseColor("#EEF2FF"));
                styleBoldLabel.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
            }
        }
        if (styleColorRow != null) {
            styleColorRow.removeAllViews();
            float d = getResources().getDisplayMetrics().density;
            int size = Math.round(28 * d);
            int gap = Math.round(8 * d);
            for (int col : TEXT_COLORS) {
                View dot = new View(this);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(col);
                if (col == currentTextColor) {
                    circle.setStroke(Math.round(2.5f * d), ContextCompat.getColor(this, R.color.primary_blue));
                }
                dot.setBackground(circle);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd(gap);
                styleColorRow.addView(dot, lp);
                final int chosen = col;
                dot.setOnClickListener(v -> {
                    currentTextColor = chosen;
                    refreshTextStyleBar();
                });
            }
        }
    }

    private int spToPx(int sp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
    }

    private int pxToNearestSp(int px) {
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;
        float sp = px / Math.max(0.01f, density * fontScale);
        int nearest = TEXT_SIZE_STEPS[0];
        float best = Math.abs(sp - nearest);
        for (int step : TEXT_SIZE_STEPS) {
            float diff = Math.abs(sp - step);
            if (diff < best) {
                best = diff;
                nearest = step;
            }
        }
        return nearest;
    }

    // ── Text annotation ───────────────────────────────────────────────────────

    void onPageTapped(int pageIndex, FrameLayout overlay, float tapX, float tapY) {
        startLiveTextEditor(pageIndex, overlay, tapX, tapY, null);
    }

    void onDatePageTapped(int pageIndex, FrameLayout overlay, float tapX, float tapY) {
        showDateStampDialog(pageIndex, overlay, tapX, tapY);
    }

    private String formatStampDate(int formatIndex) {
        int idx = Math.max(0, Math.min(DATE_FORMAT_PATTERNS.length - 1, formatIndex));
        return new SimpleDateFormat(DATE_FORMAT_PATTERNS[idx], Locale.getDefault())
                .format(stampCalendar.getTime());
    }

    private void showDateStampDialog(int pageIndex, FrameLayout overlay, float tapX, float tapY) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_date_stamp, null);
        TextView selectedLabel = dialogView.findViewById(R.id.date_selected_label);
        View pickerCard = dialogView.findViewById(R.id.date_picker_card);
        RadioGroup formatGroup = dialogView.findViewById(R.id.date_format_group);
        MaterialButton btnPlace = dialogView.findViewById(R.id.date_btn_place);

        Runnable refreshPreviews = () -> {
            selectedLabel.setText(new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
                    .format(stampCalendar.getTime()));
            for (int i = 0; i < formatGroup.getChildCount(); i++) {
                View child = formatGroup.getChildAt(i);
                if (child instanceof RadioButton) {
                    RadioButton rb = (RadioButton) child;
                    int idx = (Integer) rb.getTag();
                    rb.setText(formatStampDate(idx));
                }
            }
        };

        formatGroup.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < DATE_FORMAT_PATTERNS.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setTag(i);
            rb.setText(formatStampDate(i));
            rb.setTextSize(15);
            rb.setPadding(Math.round(4 * d), Math.round(10 * d), Math.round(4 * d), Math.round(10 * d));
            rb.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            formatGroup.addView(rb, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == selectedDateFormatIndex) rb.setChecked(true);
        }

        refreshPreviews.run();

        pickerCard.setOnClickListener(v -> new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    stampCalendar.set(Calendar.YEAR, year);
                    stampCalendar.set(Calendar.MONTH, month);
                    stampCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    refreshPreviews.run();
                },
                stampCalendar.get(Calendar.YEAR),
                stampCalendar.get(Calendar.MONTH),
                stampCalendar.get(Calendar.DAY_OF_MONTH)
        ).show());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add date")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create();

        btnPlace.setOnClickListener(v -> {
            int checkedId = formatGroup.getCheckedRadioButtonId();
            View checked = formatGroup.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof Integer) {
                selectedDateFormatIndex = (Integer) checked.getTag();
            }
            String dateText = formatStampDate(selectedDateFormatIndex);
            placeDateAnnotation(pageIndex, overlay, tapX, tapY, dateText);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void placeDateAnnotation(int pageIndex, FrameLayout overlay,
                                     float tapX, float tapY, String dateText) {
        int sizePx = spToPx(currentTextSizeSp);
        float xF = tapX / (float) Math.max(1, overlay.getWidth());
        float yF = (tapY + sizePx) / (float) Math.max(1, overlay.getHeight());
        List<TextAnnotation> list = annotations.get(pageIndex);
        if (list != null) {
            list.add(new TextAnnotation(
                    pageIndex, xF, yF, dateText, sizePx, currentTextColor, currentTextBold));
        }
        hasAnnotations = true;
        btnSave.setEnabled(true);
        if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
        Toast.makeText(this, "Date placed", Toast.LENGTH_SHORT).show();
    }

    private void startLiveTextEditor(int pageIndex, FrameLayout overlay,
                                     float tapX, float tapY, TextAnnotation existing) {
        detachLiveEditImeAvoidance();
        for (int i = overlay.getChildCount() - 1; i >= 0; i--) {
            if ("live_edit".equals(overlay.getChildAt(i).getTag())) overlay.removeViewAt(i);
        }

        float d = getResources().getDisplayMetrics().density;
        int dp4 = Math.round(4 * d);
        int dp6 = Math.round(6 * d);
        int dp8 = Math.round(8 * d);
        int dp12 = Math.round(12 * d);
        int dp200 = Math.round(200 * d);
        int blue = ContextCompat.getColor(this, R.color.primary_blue);
        int red = ContextCompat.getColor(this, R.color.accent_red);

        final int[] liveColor = {existing != null ? existing.color : currentTextColor};
        final int[] liveSizeSp = {existing != null ? pxToNearestSp(existing.textSize) : currentTextSizeSp};
        final boolean[] liveBold = {existing != null && existing.bold};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setMinimumWidth(dp200);
        box.setTag("live_edit");
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(0xF7FFFFFF);
        boxBg.setCornerRadius(Math.round(12 * d));
        boxBg.setStroke(Math.round(1.5f * d), blue);
        box.setBackground(boxBg);
        box.setPadding(dp12, dp12, dp12, dp12);
        box.setElevation(8 * d);

        EditText et = new EditText(this);
        et.setBackground(null);
        et.setPadding(dp4, dp4, dp4, dp4);
        et.setTextColor(liveColor[0]);
        // Keep edit tools at the real annotation size (no UI magnification)
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, liveSizeSp[0]);
        et.setTypeface(null, liveBold[0] ? Typeface.BOLD : Typeface.NORMAL);
        et.setHint("Type here…");
        et.setHintTextColor(0x66888888);
        et.setSingleLine(false);
        et.setMaxLines(6);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (existing != null) et.setText(existing.text);
        box.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleRow.setGravity(Gravity.CENTER_VERTICAL);
        styleRow.setPadding(0, dp4, 0, dp4);

        TextView sizeDown = makeStyleChip(this, "A−", blue);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText(String.valueOf(liveSizeSp[0]));
        sizeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        sizeLabel.setTextSize(12);
        sizeLabel.setTypeface(null, Typeface.BOLD);
        sizeLabel.setPadding(dp6, 0, dp6, 0);
        TextView sizeUp = makeStyleChip(this, "A+", blue);
        TextView boldChip = makeStyleChip(this, "B", blue);

        styleRow.addView(sizeDown);
        styleRow.addView(sizeLabel);
        styleRow.addView(sizeUp);
        styleRow.addView(boldChip);

        for (int col : TEXT_COLORS) {
            View dot = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(col);
            if (col == liveColor[0]) circle.setStroke(Math.round(2 * d), blue);
            dot.setBackground(circle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(22 * d), Math.round(22 * d));
            lp.setMarginStart(dp6);
            styleRow.addView(dot, lp);
            final int chosen = col;
            dot.setOnClickListener(v -> {
                liveColor[0] = chosen;
                et.setTextColor(chosen);
                currentTextColor = chosen;
            });
        }
        box.addView(styleRow);

        sizeDown.setOnClickListener(v -> {
            liveSizeSp[0] = stepTextSize(liveSizeSp[0], -1);
            sizeLabel.setText(String.valueOf(liveSizeSp[0]));
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, liveSizeSp[0]);
            currentTextSizeSp = liveSizeSp[0];
            refreshTextStyleBar();
        });
        sizeUp.setOnClickListener(v -> {
            liveSizeSp[0] = stepTextSize(liveSizeSp[0], 1);
            sizeLabel.setText(String.valueOf(liveSizeSp[0]));
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, liveSizeSp[0]);
            currentTextSizeSp = liveSizeSp[0];
            refreshTextStyleBar();
        });
        boldChip.setOnClickListener(v -> {
            liveBold[0] = !liveBold[0];
            et.setTypeface(null, liveBold[0] ? Typeface.BOLD : Typeface.NORMAL);
            boldChip.setAlpha(liveBold[0] ? 1f : 0.55f);
            currentTextBold = liveBold[0];
            refreshTextStyleBar();
        });
        boldChip.setAlpha(liveBold[0] ? 1f : 0.55f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView acceptTv = new TextView(this);
        acceptTv.setText(existing != null ? "✓ Update" : "✓ Accept");
        acceptTv.setTextColor(blue);
        acceptTv.setTextSize(13);
        acceptTv.setTypeface(null, Typeface.BOLD);
        acceptTv.setGravity(Gravity.CENTER);
        acceptTv.setPadding(dp8, dp8, dp8, dp8);
        acceptTv.setClickable(true);
        acceptTv.setFocusable(true);
        row.addView(acceptTv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView discardTv = new TextView(this);
        discardTv.setText("✕ Cancel");
        discardTv.setTextColor(red);
        discardTv.setTextSize(13);
        discardTv.setGravity(Gravity.CENTER);
        discardTv.setPadding(dp8, dp8, dp8, dp8);
        discardTv.setClickable(true);
        discardTv.setFocusable(true);
        row.addView(discardTv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (existing != null) {
            flp.leftMargin = (int) (existing.xFraction * overlay.getWidth());
            flp.topMargin = Math.max(0, (int) (existing.yFraction * overlay.getHeight() - existing.textSize));
        } else {
            flp.leftMargin = Math.max(0, (int) tapX - dp200 / 2);
            flp.topMargin = Math.max(0, (int) tapY - dp8);
        }
        overlay.addView(box, flp);
        box.post(() -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) box.getLayoutParams();
            p.leftMargin = Math.max(0, Math.min(p.leftMargin, overlay.getWidth() - box.getWidth() - 8));
            p.topMargin = Math.max(0, Math.min(p.topMargin, overlay.getHeight() - box.getHeight() - 8));
            box.setLayoutParams(p);
            attachLiveEditImeAvoidance(box, overlay);
        });

        if (existing != null) {
            if (selectedAnnotationView != null && selectedAnnotationView.getTag() == existing) {
                selectedAnnotationView.setVisibility(View.INVISIBLE);
            }
        }

        box.bringToFront();
        // Keep page scrolling available while editing (don't lock the RecyclerView)

        et.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);

        final boolean[] done = {false};

        Runnable commit = () -> {
            if (done[0]) return;
            done[0] = true;
            String text = et.getText().toString().trim();
            detachLiveEditImeAvoidance();
            overlay.removeView(box);
            if (imm != null) imm.hideSoftInputFromWindow(overlay.getWindowToken(), 0);

            if (text.isEmpty()) {
                if (existing != null && selectedAnnotationView != null) {
                    selectedAnnotationView.setVisibility(View.VISIBLE);
                }
                return;
            }

            int sizePx = spToPx(liveSizeSp[0]);
            currentTextColor = liveColor[0];
            currentTextSizeSp = liveSizeSp[0];
            currentTextBold = liveBold[0];
            refreshTextStyleBar();

            if (existing != null) {
                existing.text = text;
                existing.color = liveColor[0];
                existing.textSize = sizePx;
                existing.bold = liveBold[0];
                hasAnnotations = true;
                btnSave.setEnabled(true);
                if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
            } else {
                float xF = tapX / (float) Math.max(1, overlay.getWidth());
                float yF = (tapY + sizePx) / (float) Math.max(1, overlay.getHeight());
                List<TextAnnotation> list = annotations.get(pageIndex);
                if (list != null) {
                    list.add(new TextAnnotation(pageIndex, xF, yF, text, sizePx, liveColor[0], liveBold[0]));
                }
                hasAnnotations = true;
                btnSave.setEnabled(true);
                if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
            }
        };

        acceptTv.setOnClickListener(v -> commit.run());
        discardTv.setOnClickListener(v -> {
            done[0] = true;
            detachLiveEditImeAvoidance();
            overlay.removeView(box);
            if (imm != null) imm.hideSoftInputFromWindow(overlay.getWindowToken(), 0);
            if (existing != null && selectedAnnotationView != null) {
                selectedAnnotationView.setVisibility(View.VISIBLE);
            }
        });
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commit.run();
                return true;
            }
            return false;
        });
    }

    // ── Keep live text editor above the keyboard ──────────────────────────────

    private void attachLiveEditImeAvoidance(View box, FrameLayout overlay) {
        detachLiveEditImeAvoidance();
        activeLiveEditBox = box;
        activeLiveEditOverlay = overlay;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        liveEditBaseTopMargin = lp.topMargin;
        liveEditKeyboardLift = 0;

        ViewCompat.setOnApplyWindowInsetsListener(box, (v, insets) -> {
            // Defer so layout has settled with the new IME height
            mainHandler.removeCallbacks(liveEditImeRelayout);
            mainHandler.post(liveEditImeRelayout);
            // Also re-check after the keyboard animation finishes
            mainHandler.postDelayed(liveEditImeRelayout, 120);
            mainHandler.postDelayed(liveEditImeRelayout, 280);
            return insets;
        });
        ViewCompat.requestApplyInsets(box);
        // Soft keyboard may already be animating in after requestFocus
        mainHandler.postDelayed(liveEditImeRelayout, 80);
        mainHandler.postDelayed(liveEditImeRelayout, 220);
        mainHandler.postDelayed(liveEditImeRelayout, 400);
    }

    private void detachLiveEditImeAvoidance() {
        mainHandler.removeCallbacks(liveEditImeRelayout);
        if (activeLiveEditBox != null) {
            ViewCompat.setOnApplyWindowInsetsListener(activeLiveEditBox, null);
        }
        activeLiveEditBox = null;
        activeLiveEditOverlay = null;
        liveEditKeyboardLift = 0;
        liveEditBaseTopMargin = 0;
        applyImeRecyclerPadding(0);
    }

    private void applyImeRecyclerPadding(int imeBottom) {
        if (pagesRecycler == null) return;
        if (pagesRecyclerBasePadBottom < 0) {
            pagesRecyclerBasePadBottom = pagesRecycler.getPaddingBottom();
        }
        int target = pagesRecyclerBasePadBottom + Math.max(0, imeBottom);
        if (pagesRecycler.getPaddingBottom() == target) return;
        pagesRecycler.setClipToPadding(false);
        pagesRecycler.setPadding(
                pagesRecycler.getPaddingLeft(),
                pagesRecycler.getPaddingTop(),
                pagesRecycler.getPaddingRight(),
                target);
    }

    private void relayoutLiveEditorForIme() {
        View box = activeLiveEditBox;
        FrameLayout overlay = activeLiveEditOverlay;
        if (box == null || overlay == null || box.getParent() == null) return;

        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(box);
        int imeBottom = 0;
        if (rootInsets != null) {
            Insets ime = rootInsets.getInsets(WindowInsetsCompat.Type.ime());
            imeBottom = ime.bottom;
        }

        // Extra bottom space so the user can scroll pages above the keyboard
        applyImeRecyclerPadding(imeBottom);

        int gap = Math.round(16 * getResources().getDisplayMetrics().density);
        int[] boxLoc = new int[2];
        box.getLocationOnScreen(boxLoc);
        int boxBottom = boxLoc[1] + box.getHeight();

        View decor = getWindow().getDecorView();
        int[] decorLoc = new int[2];
        decor.getLocationOnScreen(decorLoc);
        int visibleBottom = decorLoc[1] + decor.getHeight() - imeBottom;

        if (imeBottom <= 0) {
            if (liveEditKeyboardLift != 0) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
                lp.topMargin = liveEditBaseTopMargin;
                box.setLayoutParams(lp);
                liveEditKeyboardLift = 0;
            }
            return;
        }

        int overlap = boxBottom + gap - visibleBottom;
        if (overlap <= 0) return;

        if (pagesRecycler != null) {
            pagesRecycler.smoothScrollBy(0, overlap);
        }

        final int expectedIme = imeBottom;
        box.postDelayed(() -> {
            if (activeLiveEditBox != box || box.getParent() == null) return;

            WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(box);
            int ime = expectedIme;
            if (wi != null) {
                ime = wi.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            }
            if (ime <= 0) return;

            int[] loc = new int[2];
            box.getLocationOnScreen(loc);
            int bottom = loc[1] + box.getHeight();
            int[] dLoc = new int[2];
            decor.getLocationOnScreen(dLoc);
            int visBottom = dLoc[1] + decor.getHeight() - ime;
            int still = bottom + gap - visBottom;
            if (still <= 0) return;

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
            liveEditKeyboardLift += still;
            lp.topMargin = Math.max(0, liveEditBaseTopMargin - liveEditKeyboardLift);
            lp.leftMargin = Math.max(0, Math.min(lp.leftMargin,
                    Math.max(0, overlay.getWidth() - box.getWidth() - 8)));
            box.setLayoutParams(lp);
        }, 260);
    }

    /** Magnify small on-document text while dragging so placement is easier. */
    private static float dragMagnificationFor(int sizePx, float density) {
        float sp = sizePx / Math.max(0.01f, density);
        if (sp <= 6) return 2.8f;
        if (sp <= 8) return 2.5f;
        if (sp <= 10) return 2.2f;
        if (sp <= 12) return 2.0f;
        if (sp <= 14) return 1.75f;
        if (sp <= 16) return 1.55f;
        if (sp <= 18) return 1.4f;
        if (sp <= 22) return 1.25f;
        return 1.12f;
    }

    private static TextView makeStyleChip(android.content.Context ctx, String label, int color) {
        TextView tv = new TextView(ctx);
        float d = ctx.getResources().getDisplayMetrics().density;
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(Math.round(10 * d), Math.round(6 * d), Math.round(10 * d), Math.round(6 * d));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private void markDirty() {
        hasAnnotations = true;
        btnSave.setEnabled(true);
    }

    // ── Annotation overlay helpers ────────────────────────────────────────────

    void clearAnnotationViews(FrameLayout overlay) {
        if (activeLiveEditOverlay == overlay) {
            detachLiveEditImeAvoidance();
        }
        selectedAnnotationView = null;
        for (int i = overlay.getChildCount() - 1; i >= 0; i--) {
            Object tag = overlay.getChildAt(i).getTag();
            if (tag instanceof TextAnnotation || "live_edit".equals(tag)) {
                overlay.removeViewAt(i);
            }
        }
    }

    private void selectAnnotationView(View target) {
        if (selectedAnnotationView == target) return;
        deselectAnnotationView();
        selectedAnnotationView = target;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable border = new GradientDrawable();
        border.setShape(GradientDrawable.RECTANGLE);
        border.setColor(0x184C45D6);
        border.setStroke(Math.round(2f * d), ContextCompat.getColor(this, R.color.primary_blue));
        border.setCornerRadius(Math.round(6 * d));
        target.setBackground(border);
    }

    private void deselectAnnotationView() {
        if (selectedAnnotationView == null) return;
        selectedAnnotationView.setBackground(null);
        selectedAnnotationView.setScaleX(1f);
        selectedAnnotationView.setScaleY(1f);
        selectedAnnotationView = null;
    }

    void populateAnnotationViews(int pageIndex, FrameLayout overlay) {
        clearAnnotationViews(overlay);
        List<TextAnnotation> anns = annotations.get(pageIndex);
        if (anns == null || anns.isEmpty() || overlay.getWidth() <= 0) return;
        for (TextAnnotation ann : anns) addAnnotationView(pageIndex, overlay, ann);
    }

    private void addAnnotationView(int pageIndex, FrameLayout overlay, TextAnnotation ann) {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(14 * d);
        int minTouch = Math.round(48 * d);

        // Wrapper expands the hit target so small text is easy to grab & drag
        FrameLayout wrap = new FrameLayout(this);
        wrap.setTag(ann);
        wrap.setMinimumWidth(minTouch);
        wrap.setMinimumHeight(minTouch);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.setClipToPadding(false);
        wrap.setClickable(true);
        wrap.setFocusable(true);

        TextView tv = new TextView(this);
        tv.setText(ann.text);
        tv.setTextColor(ann.color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, ann.textSize);
        tv.setTypeface(null, ann.bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setIncludeFontPadding(false);
        tv.setBackground(null);
        tv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        wrap.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Offset by padding so the text baseline stays at the stored fraction
        lp.leftMargin = Math.max(0, (int) (ann.xFraction * overlay.getWidth()) - pad);
        lp.topMargin = Math.max(0, (int) (ann.yFraction * overlay.getHeight() - ann.textSize) - pad);
        overlay.addView(wrap, lp);

        setupAnnotationTouch(pageIndex, ann, wrap, tv, overlay, pad);
    }

    private void setupAnnotationTouch(int pageIndex, TextAnnotation ann,
                                      FrameLayout wrap, TextView tv,
                                      FrameLayout overlay, int pad) {
        final float[] lastRaw = {0, 0};
        final float[] downRaw = {0, 0};
        final boolean[] moved = {false};
        final boolean[] magnified = {false};
        final long[] downAt = {0};
        final int touchSlop = Math.max(6, ViewConfiguration.get(this).getScaledTouchSlop() / 2);
        final float density = getResources().getDisplayMetrics().density;
        final float dragScale = dragMagnificationFor(ann.textSize, density);

        Runnable applyDragMagnify = () -> {
            if (magnified[0]) return;
            magnified[0] = true;
            wrap.setPivotX(wrap.getWidth() / 2f);
            wrap.setPivotY(wrap.getHeight() / 2f);
            wrap.animate().scaleX(dragScale).scaleY(dragScale).setDuration(90).start();
        };
        Runnable clearDragMagnify = () -> {
            magnified[0] = false;
            wrap.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
        };

        wrap.setOnTouchListener((v, event) -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = lastRaw[0] = event.getRawX();
                    downRaw[1] = lastRaw[1] = event.getRawY();
                    moved[0] = false;
                    magnified[0] = false;
                    downAt[0] = System.currentTimeMillis();
                    selectAnnotationView(wrap);
                    wrap.bringToFront();
                    if (wrap.getParent() != null) {
                        wrap.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (pagesRecycler != null) {
                        pagesRecycler.requestDisallowInterceptTouchEvent(true);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - lastRaw[0];
                    float dy = event.getRawY() - lastRaw[1];
                    lastRaw[0] = event.getRawX();
                    lastRaw[1] = event.getRawY();

                    float totalDx = event.getRawX() - downRaw[0];
                    float totalDy = event.getRawY() - downRaw[1];
                    if (!moved[0] && (Math.abs(totalDx) > touchSlop || Math.abs(totalDy) > touchSlop)) {
                        moved[0] = true;
                        applyDragMagnify.run();
                    }
                    if (!moved[0]) return true;

                    if (wrap.getParent() != null) {
                        wrap.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (pagesRecycler != null) {
                        pagesRecycler.requestDisallowInterceptTouchEvent(true);
                    }

                    int maxLeft = Math.max(0, overlay.getWidth() - wrap.getWidth());
                    int maxTop = Math.max(0, overlay.getHeight() - wrap.getHeight());
                    lp.leftMargin = Math.max(0, Math.min(lp.leftMargin + Math.round(dx), maxLeft));
                    lp.topMargin = Math.max(0, Math.min(lp.topMargin + Math.round(dy), maxTop));
                    wrap.setLayoutParams(lp);

                    ann.xFraction = (lp.leftMargin + pad) / (float) Math.max(1, overlay.getWidth());
                    ann.yFraction = (lp.topMargin + pad + ann.textSize)
                            / (float) Math.max(1, overlay.getHeight());
                    markDirty();
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    clearDragMagnify.run();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved[0]) {
                        long held = System.currentTimeMillis() - downAt[0];
                        if (held > 450) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Delete text?")
                                    .setMessage(ann.text)
                                    .setPositiveButton("Delete", (d, w) ->
                                            deleteAnnotation(pageIndex, ann, wrap, overlay))
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            showAnnotationEditor(pageIndex, ann, wrap, tv, overlay);
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private void deleteAnnotation(int pageIndex, TextAnnotation ann, View wrap, FrameLayout overlay) {
        List<TextAnnotation> list = annotations.get(pageIndex);
        if (list != null) list.remove(ann);
        if (selectedAnnotationView == wrap) selectedAnnotationView = null;
        overlay.removeView(wrap);
        markDirty();
    }

    private void showAnnotationEditor(int pageIndex, TextAnnotation ann,
                                      View wrap, TextView tv, FrameLayout overlay) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_annotation_edit, null);
        EditText editText = dialogView.findViewById(R.id.ann_edit_text);
        TextView sizeLabel = dialogView.findViewById(R.id.ann_size_label);
        View sizeDown = dialogView.findViewById(R.id.ann_size_down);
        View sizeUp = dialogView.findViewById(R.id.ann_size_up);
        MaterialCardView boldBtn = dialogView.findViewById(R.id.ann_bold_btn);
        TextView boldLabel = dialogView.findViewById(R.id.ann_bold_label);
        LinearLayout colorRow = dialogView.findViewById(R.id.ann_color_row);
        MaterialButton btnDelete = dialogView.findViewById(R.id.ann_btn_delete);
        MaterialButton btnDuplicate = dialogView.findViewById(R.id.ann_btn_duplicate);
        MaterialButton btnApply = dialogView.findViewById(R.id.ann_btn_apply);

        final int[] editSizeSp = {pxToNearestSp(ann.textSize)};
        final int[] editColor = {ann.color};
        final boolean[] editBold = {ann.bold};

        editText.setText(ann.text);
        editText.setSelection(editText.getText().length());
        editText.setTextColor(editColor[0]);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, editSizeSp[0]);
        editText.setTypeface(null, editBold[0] ? Typeface.BOLD : Typeface.NORMAL);
        sizeLabel.setText(String.valueOf(editSizeSp[0]));

        Runnable refreshBold = () -> {
            if (editBold[0]) {
                boldBtn.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue));
                boldLabel.setTextColor(Color.WHITE);
            } else {
                boldBtn.setCardBackgroundColor(Color.parseColor("#EEF2FF"));
                boldLabel.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
            }
            editText.setTypeface(null, editBold[0] ? Typeface.BOLD : Typeface.NORMAL);
        };
        refreshBold.run();

        sizeDown.setOnClickListener(v -> {
            editSizeSp[0] = stepTextSize(editSizeSp[0], -1);
            sizeLabel.setText(String.valueOf(editSizeSp[0]));
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, editSizeSp[0]);
        });
        sizeUp.setOnClickListener(v -> {
            editSizeSp[0] = stepTextSize(editSizeSp[0], 1);
            sizeLabel.setText(String.valueOf(editSizeSp[0]));
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, editSizeSp[0]);
        });
        boldBtn.setOnClickListener(v -> {
            editBold[0] = !editBold[0];
            refreshBold.run();
        });

        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(32 * d);
        int gap = Math.round(8 * d);
        int blue = ContextCompat.getColor(this, R.color.primary_blue);
        colorRow.removeAllViews();
        for (int col : TEXT_COLORS) {
            addColorDot(colorRow, col, editColor, editText, size, gap, d, blue);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit text")
                .setView(dialogView)
                .setNeutralButton("Edit on page", (d2, w) -> startLiveTextEditor(
                        pageIndex, overlay,
                        ann.xFraction * overlay.getWidth(),
                        ann.yFraction * overlay.getHeight(),
                        ann))
                .create();

        btnDelete.setOnClickListener(v -> {
            deleteAnnotation(pageIndex, ann, wrap, overlay);
            dialog.dismiss();
        });
        btnDuplicate.setOnClickListener(v -> {
            List<TextAnnotation> list = annotations.get(pageIndex);
            if (list != null) {
                float offset = 0.04f;
                list.add(new TextAnnotation(
                        pageIndex,
                        Math.min(0.92f, ann.xFraction + offset),
                        Math.min(0.95f, ann.yFraction + offset),
                        ann.text,
                        ann.textSize,
                        ann.color,
                        ann.bold));
                markDirty();
                if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
            }
            dialog.dismiss();
        });
        btnApply.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Text can’t be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            ann.text = text;
            ann.color = editColor[0];
            ann.bold = editBold[0];
            ann.textSize = spToPx(editSizeSp[0]);
            currentTextColor = editColor[0];
            currentTextSizeSp = editSizeSp[0];
            currentTextBold = editBold[0];
            refreshTextStyleBar();
            markDirty();
            if (pageAdapter != null) pageAdapter.refreshPage(pageIndex);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addColorDot(LinearLayout row, int col, int[] editColor, EditText editText,
                             int size, int gap, float d, int blue) {
        View dot = new View(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(col);
        if (col == editColor[0]) circle.setStroke(Math.round(3 * d), blue);
        dot.setBackground(circle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(gap);
        row.addView(dot, lp);
        final int chosen = col;
        dot.setOnClickListener(v -> {
            editColor[0] = chosen;
            editText.setTextColor(chosen);
            row.removeAllViews();
            for (int c : TEXT_COLORS) {
                addColorDot(row, c, editColor, editText, size, gap, d, blue);
            }
        });
    }

    // ── Signature placement ───────────────────────────────────────────────────

    void onSignPageTapped(int pageIndex, FrameLayout sigContainer, float tapX, float tapY) {
        if (pendingSigBitmap != null) {
            placeSignatureOnPage(pageIndex, sigContainer, pendingSigBitmap, tapX, tapY);
            pendingSigBitmap = null;
            return;
        }
        showSignatureSelectorDialog(pageIndex, sigContainer, tapX, tapY);
    }

    private void showSignatureSelectorDialog(int pageIndex, FrameLayout sigContainer,
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
                placeSignatureOnPage(pageIndex, sigContainer, bmp, tapX, tapY);
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
            showDrawSignatureDialog(pageIndex, sigContainer, tapX, tapY);
        });

        cardCamera.setOnClickListener(v -> {
            dialog.dismiss();
            cameraSignatureLauncher.launch(new Intent(this, CaptureSignatureActivity.class));
        });

        dialog.show();
    }

    private void showDrawSignatureDialog(int pageIndex, FrameLayout sigContainer,
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
                placeSignatureOnPage(pageIndex, sigContainer, signatureView.getSignatureBitmap(), tapX, tapY);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please draw your signature first", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void deselectAllSignatures() {
        if (pagesRecycler == null) return;
        for (int i = 0; i < pagesRecycler.getChildCount(); i++) {
            View child = pagesRecycler.getChildAt(i);
            FrameLayout container = child.findViewById(R.id.signature_container);
            if (container == null) continue;
            for (int c = 0; c < container.getChildCount(); c++) {
                View v = container.getChildAt(c);
                if (v instanceof DraggableSignatureView) {
                    ((DraggableSignatureView) v).setEditing(false);
                }
            }
        }
    }

    private void placeSignatureOnPage(int pageIndex, FrameLayout container,
                                      Bitmap bitmap, float tapX, float tapY) {
        container.setVisibility(View.VISIBLE);
        deselectAllSignatures();

        DraggableSignatureView sigView = new DraggableSignatureView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        container.addView(sigView, lp);

        SignatureOverlay model = new SignatureOverlay(pageIndex, 0, 0, 0, 0, bitmap);
        List<SignatureOverlay> list = signatureOverlays.get(pageIndex);
        if (list == null) {
            list = new ArrayList<>();
            signatureOverlays.put(pageIndex, list);
        }
        list.add(model);
        sigView.setTag(model);

        wireSignatureView(pageIndex, container, sigView, model);

        sigView.post(() -> {
            int refW = Math.max(1, container.getWidth());
            int refH = Math.max(1, container.getHeight());
            float w = refW * 0.28f;
            float h = w * ((float) bitmap.getHeight() / Math.max(1, bitmap.getWidth()));
            float x = (tapX > 0) ? tapX - w / 2f : (refW - w) / 2f;
            float y = (tapY > 0) ? tapY - h / 2f : (refH - h) / 2f;
            x = Math.max(0, Math.min(x, refW - w));
            y = Math.max(0, Math.min(y, refH - h));
            sigView.setSignature(bitmap, x, y, w, h);
            syncSignatureModel(sigView, model);
            markDirty();
        });
    }

    private void wireSignatureView(int pageIndex, FrameLayout container,
                                   DraggableSignatureView sigView, SignatureOverlay model) {
        sigView.setOnSignatureSelectedListener(view -> {
            // Only one signature editing at a time
            for (int c = 0; c < container.getChildCount(); c++) {
                View v = container.getChildAt(c);
                if (v instanceof DraggableSignatureView && v != view) {
                    ((DraggableSignatureView) v).setEditing(false);
                }
            }
        });

        sigView.setOnSignatureChangedListener((x, y, w, h) -> {
            syncSignatureModel(sigView, model);
            markDirty();
        });

        sigView.setOnSignatureAcceptedListener(() -> {
            syncSignatureModel(sigView, model);
            markDirty();
        });

        sigView.setOnSignatureDeletedListener(() -> {
            List<SignatureOverlay> list = signatureOverlays.get(pageIndex);
            if (list != null) list.remove(model);
            container.removeView(sigView);
            markDirty();
        });

        sigView.setOnSignatureEdgeTransferListener((view, direction) ->
                transferSignatureAcrossPages(pageIndex, container, view, model, direction));
    }

    private void syncSignatureModel(DraggableSignatureView sigView, SignatureOverlay model) {
        float oW = Math.max(1, sigView.getWidth());
        float oH = Math.max(1, sigView.getHeight());
        model.xFraction = sigView.getSignatureX() / oW;
        model.yFraction = sigView.getSignatureY() / oH;
        model.widthFraction = sigView.getSignatureWidth() / oW;
        model.heightFraction = sigView.getSignatureHeight() / oH;
        model.pageIndex = /* keep current until transfer */ model.pageIndex;
    }

    private void transferSignatureAcrossPages(int fromPage, FrameLayout fromContainer,
                                              DraggableSignatureView view, SignatureOverlay model,
                                              int direction) {
        int toPage = fromPage + direction;
        if (pdfRenderer == null || toPage < 0 || toPage >= pdfRenderer.getPageCount()) {
            view.snapCenterOntoPage();
            view.setEditing(true);
            return;
        }

        syncSignatureModel(view, model);
        List<SignatureOverlay> fromList = signatureOverlays.get(fromPage);
        if (fromList != null) fromList.remove(model);

        // Place near the opposite edge of the destination page
        if (direction > 0) {
            model.yFraction = 0.02f;
        } else {
            model.yFraction = Math.max(0f, 1f - model.heightFraction - 0.02f);
        }
        model.pageIndex = toPage;

        List<SignatureOverlay> toList = signatureOverlays.get(toPage);
        if (toList == null) {
            toList = new ArrayList<>();
            signatureOverlays.put(toPage, toList);
        }
        toList.add(model);

        signatureToResumeEditing = model;
        fromContainer.removeView(view);
        markDirty();
        if (pageAdapter != null) {
            pageAdapter.refreshPage(fromPage);
            pageAdapter.refreshPage(toPage);
        }
        if (pagesRecycler != null) {
            pagesRecycler.smoothScrollToPosition(toPage);
        }
    }

    void populateSignatureViews(int pageIndex, FrameLayout container) {
        container.removeAllViews();
        List<SignatureOverlay> list = signatureOverlays.get(pageIndex);
        if (list == null || list.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        for (SignatureOverlay model : list) {
            DraggableSignatureView sigView = new DraggableSignatureView(this);
            container.addView(sigView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            sigView.setTag(model);
            wireSignatureView(pageIndex, container, sigView, model);
            final boolean resumeEditing = model == signatureToResumeEditing;
            if (resumeEditing) signatureToResumeEditing = null;
            sigView.post(() -> {
                int w = Math.max(1, container.getWidth());
                int h = Math.max(1, container.getHeight());
                float x = model.xFraction * w;
                float y = model.yFraction * h;
                float sw = model.widthFraction * w;
                float sh = model.heightFraction * h;
                sigView.setSignature(model.bitmap, x, y, sw, sh);
                // Restored signatures start without chrome unless just transferred
                sigView.setEditing(resumeEditing);
            });
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveAnnotatedPdf() {
        if (pdfRenderer == null) return;
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");
        btnSave.setIconResource(0);

        executor.execute(() -> {
            try {
                int pageCount  = pdfRenderer.getPageCount();
                int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;
                PdfDocument doc = new PdfDocument();

                for (int i = 0; i < pageCount; i++) {
                    int w = screenWidth;
                    float scale = w / pdfRenderer.getPageWidthPoints(i);
                    int h = Math.round(pdfRenderer.getPageHeightPoints(i) * scale);

                    Bitmap bmp = pdfRenderer.renderPage(i, scale);

                    List<TextAnnotation> anns = annotations.get(i);
                    List<SignatureOverlay> sigs = signatureOverlays.get(i);
                    boolean hasText = anns != null && !anns.isEmpty();
                    boolean hasSigs = sigs != null && !sigs.isEmpty();

                    if (hasText || hasSigs) {
                        Canvas canvas = new Canvas(bmp);
                        if (hasText) {
                            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                            for (TextAnnotation ann : anns) {
                                paint.setColor(ann.color);
                                paint.setTextSize(ann.textSize);
                                paint.setTypeface(ann.bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                                float x = ann.xFraction * w;
                                float y = ann.yFraction * h;
                                String[] lines = ann.text.split("\n", -1);
                                float lineHeight = paint.getFontSpacing();
                                // yFraction points at the first baseline
                                for (int li = 0; li < lines.length; li++) {
                                    canvas.drawText(lines[li], x, y + li * lineHeight, paint);
                                }
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
                if (finalPath != null) {
                    new HistoryManager(this).addToHistory(fileName, finalPath);
                }

                final String finalFileName = fileName;
                final double sizeMb = bytes.length / (1024.0 * 1024.0);
                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save PDF");
                    btnSave.setIconResource(R.drawable.ic_save);
                    showSuccessScreen(finalPath, finalFileName, sizeMb);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save PDF");
                    btnSave.setIconResource(R.drawable.ic_save);
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

    private void showSuccessScreen(String path, String fileName, double sizeMb) {
        lastSavedPdfPath = path;
        if (successFileName != null) {
            successFileName.setText(fileName);
        }
        if (successFileMeta != null) {
            successFileMeta.setText(String.format(Locale.US, "Saved just now • %.1f MB", sizeMb));
        }
        View mainContentRoot = findViewById(R.id.main_content_root);
        if (mainContentRoot != null) mainContentRoot.setVisibility(View.GONE);
        if (successState != null) successState.setVisibility(View.VISIBLE);
    }

    private void closePdfRenderer() {
        try {
            if (pdfRenderer != null) { pdfRenderer.close(); pdfRenderer = null; }
            if (parcelFileDescriptor != null) { parcelFileDescriptor.close(); parcelFileDescriptor = null; }
        } catch (IOException ignored) {}
    }

    @Override
    protected void onDestroy() {
        detachLiveEditImeAvoidance();
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
        boolean bold;

        TextAnnotation(int pi, float xF, float yF, String t, int sz, int col) {
            this(pi, xF, yF, t, sz, col, false);
        }

        TextAnnotation(int pi, float xF, float yF, String t, int sz, int col, boolean bold) {
            pageIndex = pi;
            xFraction = xF;
            yFraction = yF;
            text = t;
            textSize = sz;
            color = col;
            this.bold = bold;
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
            FrameLayout signatureContainer;
            int boundPos = -1;

            VH(@NonNull View v) {
                super(v);
                pageImage           = v.findViewById(R.id.page_image);
                progress            = v.findViewById(R.id.page_progress);
                pageNum             = v.findViewById(R.id.page_number);
                textOverlay         = v.findViewById(R.id.text_overlay);
                signatureContainer  = v.findViewById(R.id.signature_container);

                final float[] tap = {0f, 0f};
                final float[] down = {0f, 0f};
                final boolean[] moved = {false};
                final int touchSlop = ViewConfiguration.get(EditPdfActivity.this).getScaledTouchSlop();

                // Tap to place annotations, but let vertical drags scroll across pages
                textOverlay.setClickable(false);
                textOverlay.setFocusable(false);
                textOverlay.setOnTouchListener((vv, ev) -> {
                    if (!isTextMode && !isSignMode && !isDateMode) return false;
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            down[0] = tap[0] = ev.getX();
                            down[1] = tap[1] = ev.getY();
                            moved[0] = false;
                            if (vv.getParent() != null) {
                                vv.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (Math.abs(ev.getX() - down[0]) > touchSlop
                                    || Math.abs(ev.getY() - down[1]) > touchSlop) {
                                moved[0] = true;
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!moved[0] && boundPos >= 0) {
                                deselectAnnotationView();
                                if (isTextMode) {
                                    onPageTapped(boundPos, textOverlay, tap[0], tap[1]);
                                } else if (isDateMode) {
                                    onDatePageTapped(boundPos, textOverlay, tap[0], tap[1]);
                                } else if (isSignMode) {
                                    onSignPageTapped(boundPos, signatureContainer, tap[0], tap[1]);
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            return true;
                        default:
                            return false;
                    }
                });
            }

            void bind(int position) {
                boundPos = position;
                int total = getItemCount();
                pageNum.setText(String.format(
                        java.util.Locale.US, "PAGE %d OF %d", position + 1, Math.max(total, 1)));
                progress.setVisibility(View.VISIBLE);
                pageImage.setImageBitmap(null);

                clearAnnotationViews(textOverlay);
                if (signatureContainer != null) signatureContainer.removeAllViews();

                executor.execute(() -> {
                    Bitmap bmp = renderPage(position);
                    if (bmp == null) {
                        mainHandler.post(() -> progress.setVisibility(View.GONE));
                        return;
                    }

                    // Signatures stay as interactive overlays (not baked into preview)
                    final Bitmap finalBmp = bmp;
                    mainHandler.post(() -> {
                        if (getBindingAdapterPosition() == position) {
                            pageImage.setImageBitmap(finalBmp);
                            progress.setVisibility(View.GONE);
                            textOverlay.post(() -> {
                                if (getBindingAdapterPosition() == position) {
                                    populateAnnotationViews(position, textOverlay);
                                    if (signatureContainer != null) {
                                        populateSignatureViews(position, signatureContainer);
                                    }
                                }
                            });
                        } else {
                            finalBmp.recycle();
                        }
                    });
                });
            }

            private Bitmap renderPage(int index) {
                if (pdfRenderer == null) return null;
                try {
                    float density = getResources().getDisplayMetrics().density;
                    int sideInset = Math.round(40f * density);
                    int screenW = getResources().getDisplayMetrics().widthPixels - sideInset;
                    float scale = screenW / pdfRenderer.getPageWidthPoints(index);
                    return pdfRenderer.renderPage(index, scale);
                } catch (Exception e) { return null; }
            }
        }
    }
}
