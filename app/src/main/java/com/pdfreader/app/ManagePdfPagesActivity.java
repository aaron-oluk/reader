package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

    private RecyclerView pagesRecycler;
    private ProgressBar loadingIndicator;
    private TextView pageCountText;
    private MaterialButton btnSave;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private String pdfPath;
    private boolean modified = false;

    private PageAdapter adapter;
    private final List<Integer> pageOrder = new ArrayList<>(); // original indices in current display order

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_pdf_pages);

        pagesRecycler  = findViewById(R.id.pages_recycler);
        loadingIndicator = findViewById(R.id.loading_indicator);
        pageCountText  = findViewById(R.id.page_count_text);
        btnSave        = findViewById(R.id.btn_save);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> savePdf());

        pdfPath = getIntent().getStringExtra(EXTRA_PDF_PATH);
        if (pdfPath == null) { finish(); return; }

        loadPdf();
    }

    private void loadPdf() {
        executor.execute(() -> {
            try {
                File file = new File(pdfPath);
                parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(parcelFileDescriptor);
                int count = pdfRenderer.getPageCount();
                for (int i = 0; i < count; i++) pageOrder.add(i);

                mainHandler.post(() -> {
                    pageCountText.setText(count + (count == 1 ? " page" : " pages"));
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
                int toPos   = to.getBindingAdapterPosition();
                Collections.swap(pageOrder, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                markModified();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.setAlpha(0.8f);
                    viewHolder.itemView.setScaleX(1.05f);
                    viewHolder.itemView.setScaleY(1.05f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(rv, viewHolder);
                viewHolder.itemView.setAlpha(1f);
                viewHolder.itemView.setScaleX(1f);
                viewHolder.itemView.setScaleY(1f);
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
                .setTitle("Delete Page")
                .setMessage("Delete page " + (adapterPosition + 1) + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    pageOrder.remove(adapterPosition);
                    adapter.notifyItemRemoved(adapterPosition);
                    adapter.notifyItemRangeChanged(adapterPosition, pageOrder.size());
                    pageCountText.setText(pageOrder.size() + (pageOrder.size() == 1 ? " page" : " pages"));
                    markModified();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            btnSave.setEnabled(true);
        }
    }

    private void savePdf() {
        if (pdfRenderer == null) return;
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        executor.execute(() -> {
            try {
                int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;
                PdfDocument doc = new PdfDocument();

                for (int docPageNum = 0; docPageNum < pageOrder.size(); docPageNum++) {
                    int originalIndex = pageOrder.get(docPageNum);
                    synchronized (pdfRenderer) {
                        PdfRenderer.Page page = pdfRenderer.openPage(originalIndex);
                        int w = screenWidth;
                        int h = (int) ((float) page.getHeight() / page.getWidth() * w);
                        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        page.close();

                        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(w, h, docPageNum + 1).create();
                        PdfDocument.Page docPage = doc.startPage(info);
                        docPage.getCanvas().drawBitmap(bmp, 0, 0, new Paint());
                        doc.finishPage(docPage);
                        bmp.recycle();
                    }
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
                    try (FileOutputStream fos = new FileOutputStream(fallback)) { fos.write(bytes); }
                    savedPath = fallback.getAbsolutePath();
                }
                final String finalPath = savedPath;

                mainHandler.post(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                    new AlertDialog.Builder(this)
                            .setTitle("Saved")
                            .setMessage("PDF saved with " + pageOrder.size() + " pages. Share it?")
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

        PageAdapter(List<Integer> order) { this.order = order; }

        @NonNull @Override
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
        public int getItemCount() { return order.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            ProgressBar progress;
            TextView pageNum;
            ImageView btnDelete;

            VH(@NonNull View v) {
                super(v);
                thumbnail = v.findViewById(R.id.page_thumbnail);
                progress  = v.findViewById(R.id.page_progress);
                pageNum   = v.findViewById(R.id.page_number);
                btnDelete = v.findViewById(R.id.btn_delete_page);
                btnDelete.setOnClickListener(vv -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) deletePage(pos);
                });
            }

            void bind(int originalIndex, int displayPosition) {
                pageNum.setText(String.valueOf(displayPosition + 1));
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
                    synchronized (pdfRenderer) {
                        PdfRenderer.Page page = pdfRenderer.openPage(index);
                        int thumbW = 400;
                        int thumbH = (int) ((float) page.getHeight() / page.getWidth() * thumbW);
                        Bitmap bmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888);
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
