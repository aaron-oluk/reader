package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManagePdfPagesActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_PATH = "pdf_path";
    public static final String EXTRA_PDF_TITLE = "pdf_title";

    private RecyclerView pagesRecycler;
    private ProgressBar loadingIndicator;
    private TextView pageCountText;
    private TextView changesHint;
    private MaterialButton btnSave;

    private PdfBoxRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private String pdfPath;
    private String pdfTitle;
    private boolean modified = false;
    private int originalPageCount = 0;

    private PageAdapter adapter;
    private final List<Integer> pageOrder = new ArrayList<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowInsetsHelper.enableEdgeToEdge(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_pdf_pages);

        View appBar = findViewById(R.id.app_bar);
        if (appBar != null) {
            WindowInsetsHelper.applyAppBarInsets(appBar);
        }

        pagesRecycler = findViewById(R.id.pages_recycler);
        loadingIndicator = findViewById(R.id.loading_indicator);
        pageCountText = findViewById(R.id.page_count_text);
        changesHint = findViewById(R.id.changes_hint);
        btnSave = findViewById(R.id.btn_save);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> savePdf());

        pdfPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        pdfTitle = getIntent().getStringExtra(EXTRA_PDF_TITLE);
        if (pdfPath == null) {
            finish();
            return;
        }

        updateSubtitle(0);
        updateChangesHint();
        loadPdf();
    }

    private void loadPdf() {
        executor.execute(() -> {
            try {
                File file = new File(pdfPath);
                if (!file.exists() && pdfPath.startsWith("content://")) {
                    // Copy content URI into cache for PdfBoxRenderer
                    File cache = new File(getCacheDir(), "manage_temp.pdf");
                    try (java.io.InputStream in = getContentResolver().openInputStream(Uri.parse(pdfPath));
                         FileOutputStream out = new FileOutputStream(cache)) {
                        if (in == null) throw new IllegalStateException("Cannot open document");
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }
                    file = cache;
                }
                parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfBoxRenderer(this, parcelFileDescriptor);
                int count = pdfRenderer.getPageCount();
                originalPageCount = count;
                pageOrder.clear();
                for (int i = 0; i < count; i++) pageOrder.add(i);

                mainHandler.post(() -> {
                    updateSubtitle(count);
                    setupRecycler();
                    loadingIndicator.setVisibility(View.GONE);
                    pagesRecycler.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Could not open PDF", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void setupRecycler() {
        adapter = new PageAdapter(pageOrder);
        pagesRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        pagesRecycler.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN |
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos = to.getBindingAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false;
                Collections.swap(pageOrder, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                // Refresh badges so PAGE numbers stay in display order
                adapter.notifyItemChanged(fromPos);
                adapter.notifyItemChanged(toPos);
                markModified();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.animate().scaleX(1.04f).scaleY(1.04f).alpha(0.92f).setDuration(120).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(rv, viewHolder);
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                // Refresh all badges after drop so numbering is correct
                adapter.notifyDataSetChanged();
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(pagesRecycler);
    }

    void deletePage(int adapterPosition) {
        if (pageOrder.size() <= 1) {
            Toast.makeText(this, "Cannot delete the only page", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete page")
                .setMessage("Remove page " + (adapterPosition + 1) + " from this document?")
                .setPositiveButton("Delete", (d, w) -> {
                    pageOrder.remove(adapterPosition);
                    adapter.notifyItemRemoved(adapterPosition);
                    adapter.notifyItemRangeChanged(0, pageOrder.size());
                    updateSubtitle(pageOrder.size());
                    markModified();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markModified() {
        modified = true;
        btnSave.setEnabled(true);
        updateChangesHint();
    }

    private void updateSubtitle(int count) {
        String pages = count + (count == 1 ? " page" : " pages");
        if (pdfTitle != null && !pdfTitle.isEmpty()) {
            pageCountText.setText(pdfTitle + "  ·  " + pages);
        } else {
            pageCountText.setText(pages);
        }
    }

    private void updateChangesHint() {
        if (changesHint == null) return;
        if (!modified) {
            changesHint.setText("No changes yet");
            return;
        }
        int removed = Math.max(0, originalPageCount - pageOrder.size());
        if (removed > 0) {
            changesHint.setText(removed + (removed == 1 ? " page removed" : " pages removed")
                    + " · Ready to save");
        } else {
            changesHint.setText("Pages reordered · Ready to save");
        }
    }

    private void savePdf() {
        if (pdfRenderer == null) return;
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");
        btnSave.setIconResource(0);

        executor.execute(() -> {
            try {
                int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;
                PdfDocument doc = new PdfDocument();

                for (int docPageNum = 0; docPageNum < pageOrder.size(); docPageNum++) {
                    int originalIndex = pageOrder.get(docPageNum);
                    float pageWidthPts = pdfRenderer.getPageWidthPoints(originalIndex);
                    float pageHeightPts = pdfRenderer.getPageHeightPoints(originalIndex);
                    float scale = screenWidth / pageWidthPts;
                    int w = screenWidth;
                    int h = Math.round(pageHeightPts * scale);
                    Bitmap bmp = pdfRenderer.renderPage(originalIndex, scale);

                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(w, h, docPageNum + 1).create();
                    PdfDocument.Page docPage = doc.startPage(info);
                    docPage.getCanvas().drawBitmap(bmp, 0, 0, new Paint());
                    doc.finishPage(docPage);
                    bmp.recycle();
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.writeTo(baos);
                doc.close();
                byte[] bytes = baos.toByteArray();

                String fileName = "pages_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
                FileManager fm = new FileManager(this);
                String savedPath = fm.savePdf(bytes, fileName, FileManager.CATEGORY_SIGNED);
                if (savedPath == null) {
                    File fallback = new File(getFilesDir(), fileName);
                    try (FileOutputStream fos = new FileOutputStream(fallback)) {
                        fos.write(bytes);
                    }
                    savedPath = fallback.getAbsolutePath();
                }
                final String finalPath = savedPath;

                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Pages");
                    btnSave.setIconResource(R.drawable.ic_save);
                    new AlertDialog.Builder(this)
                            .setTitle("Pages saved")
                            .setMessage("PDF saved with " + pageOrder.size()
                                    + (pageOrder.size() == 1 ? " page" : " pages") + ". Share it?")
                            .setPositiveButton("Share", (d, w) -> sharePdf(finalPath))
                            .setNegativeButton("Done", null)
                            .show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Pages");
                    btnSave.setIconResource(R.drawable.ic_save);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void sharePdf(String path) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", new File(path));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share PDF via"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (parcelFileDescriptor != null) parcelFileDescriptor.close();
        } catch (Exception ignored) {}
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {

        private final List<Integer> order;

        PageAdapter(List<Integer> order) {
            this.order = order;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_manage_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            h.bind(order.get(position), position);
        }

        @Override
        public int getItemCount() {
            return order.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            ProgressBar progress;
            TextView pageNum;
            ImageView btnDelete;

            VH(@NonNull View v) {
                super(v);
                thumbnail = v.findViewById(R.id.page_thumbnail);
                progress = v.findViewById(R.id.page_progress);
                pageNum = v.findViewById(R.id.page_number);
                btnDelete = v.findViewById(R.id.btn_delete_page);
                btnDelete.setOnClickListener(vv -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) deletePage(pos);
                });
            }

            void bind(int originalIndex, int displayPosition) {
                pageNum.setText(String.format(Locale.US, "PAGE %d", displayPosition + 1));
                progress.setVisibility(View.VISIBLE);
                thumbnail.setImageBitmap(null);

                executor.execute(() -> {
                    Bitmap bmp = renderThumbnail(originalIndex);
                    mainHandler.post(() -> {
                        if (getBindingAdapterPosition() == displayPosition) {
                            thumbnail.setImageBitmap(bmp);
                            progress.setVisibility(View.GONE);
                        } else if (bmp != null) {
                            bmp.recycle();
                        }
                    });
                });
            }

            private Bitmap renderThumbnail(int index) {
                if (pdfRenderer == null) return null;
                try {
                    int thumbW = 400;
                    float scale = thumbW / pdfRenderer.getPageWidthPoints(index);
                    return pdfRenderer.renderPage(index, scale);
                } catch (Exception e) {
                    return null;
                }
            }
        }
    }
}
