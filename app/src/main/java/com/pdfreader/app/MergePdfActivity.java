package com.pdfreader.app;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MergePdfActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MaterialButton btnSelectPdfs;
    private MaterialButton btnSelectImages;
    private MaterialButton btnMerge;
    private ProgressBar progressBar;
    private TextView statusText;
    private View emptyState;

    private final List<FileEntry> selectedFiles = new ArrayList<>();
    private FileListAdapter adapter;

    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    static class FileEntry {
        Uri uri;
        String displayName;
        boolean isPdf;

        FileEntry(Uri uri, String name, boolean isPdf) {
            this.uri = uri;
            this.displayName = name;
            this.isPdf = isPdf;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merge_pdf);

        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null)
                        addFiles(result.getData(), true);
                });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null)
                        addFiles(result.getData(), false);
                });

        recyclerView = findViewById(R.id.recyclerView);
        btnSelectPdfs = findViewById(R.id.btnSelectPdfs);
        btnSelectImages = findViewById(R.id.btnSelectImages);
        btnMerge = findViewById(R.id.btnMerge);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        emptyState = findViewById(R.id.empty_state);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileListAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnSelectPdfs.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/pdf");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pdfPickerLauncher.launch(intent);
        });

        btnSelectImages.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            imagePickerLauncher.launch(intent);
        });

        btnMerge.setOnClickListener(v -> mergeFiles());
    }

    private void addFiles(Intent data, boolean isPdf) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            ClipData cd = data.getClipData();
            for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        for (Uri uri : uris) {
            String name = getDisplayName(uri);
            selectedFiles.add(new FileEntry(uri, name, isPdf));
        }

        adapter.notifyDataSetChanged();
        updateListVisibility();
    }

    private void updateListVisibility() {
        boolean hasFiles = !selectedFiles.isEmpty();
        emptyState.setVisibility(hasFiles ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(hasFiles ? View.VISIBLE : View.GONE);
        btnMerge.setEnabled(selectedFiles.size() >= 2);
        statusText.setText(selectedFiles.size() + " file" + (selectedFiles.size() == 1 ? "" : "s") + " added");
    }

    private String getDisplayName(Uri uri) {
        String name = "File";
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex("_display_name");
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private void mergeFiles() {
        if (selectedFiles.size() < 2) {
            Toast.makeText(this, "Add at least 2 files", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        btnMerge.setEnabled(false);
        statusText.setText("Merging…");

        new Thread(() -> {
            try {
                PdfDocument merged = new PdfDocument();
                int pageNum = 1;

                for (FileEntry entry : selectedFiles) {
                    if (entry.isPdf) {
                        pageNum = appendPdf(merged, entry.uri, pageNum);
                    } else {
                        pageNum = appendImage(merged, entry.uri, pageNum);
                    }
                }

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "merged_" + ts + ".pdf";

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                merged.writeTo(baos);
                merged.close();
                byte[] bytes = baos.toByteArray();

                FileManager fm = new FileManager(this);
                String saved = fm.savePdf(bytes, fileName, FileManager.CATEGORY_MERGED);
                if (saved == null) {
                    File fallback = new File(getFilesDir(), fileName);
                    try (FileOutputStream fos = new FileOutputStream(fallback)) { fos.write(bytes); }
                    saved = fallback.getAbsolutePath();
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnMerge.setEnabled(true);
                    statusText.setText("Merged successfully — " + selectedFiles.size() + " files");
                    Toast.makeText(this, "Saved as " + fileName, Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnMerge.setEnabled(selectedFiles.size() >= 2);
                    statusText.setText("Merge failed: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    private int appendPdf(PdfDocument merged, Uri uri, int pageNum) throws Exception {
        File cache = new File(getCacheDir(), "merge_pdf_" + pageNum + ".pdf");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(cache)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            for (int i = 0; i < renderer.getPageCount(); i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                Bitmap bmp = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                page.close();

                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(bmp.getWidth(), bmp.getHeight(), pageNum++).create();
                PdfDocument.Page p = merged.startPage(info);
                p.getCanvas().drawBitmap(bmp, 0, 0, null);
                merged.finishPage(p);
                bmp.recycle();
            }
        }
        cache.delete();
        return pageNum;
    }

    private int appendImage(PdfDocument merged, Uri uri, int pageNum) throws Exception {
        Bitmap src;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            src = BitmapFactory.decodeStream(in);
        }
        if (src == null) return pageNum;

        // Scale to A4 at ~150 dpi: 1240 x 1754
        Bitmap bmp = scaleBitmap(src, 1240, 1754);
        src.recycle();

        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(bmp.getWidth(), bmp.getHeight(), pageNum++).create();
        PdfDocument.Page p = merged.startPage(info);
        Canvas c = p.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawBitmap(bmp, 0, 0, null);
        merged.finishPage(p);
        bmp.recycle();
        return pageNum;
    }

    private Bitmap scaleBitmap(Bitmap src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        Bitmap scaled = Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
        src.recycle();
        return scaled;
    }

    // ── Simple file list adapter ───────────────────────────────────────────────

    class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_merge_file, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            FileEntry entry = selectedFiles.get(position);
            h.name.setText(entry.displayName);
            h.type.setText(entry.isPdf ? "PDF" : "Image");
            h.icon.setImageResource(entry.isPdf ? R.drawable.ic_pdf : R.drawable.ic_image);
            h.remove.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    selectedFiles.remove(pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, selectedFiles.size());
                    updateListVisibility();
                }
            });
        }

        @Override public int getItemCount() { return selectedFiles.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, type;
            ImageView icon;
            View remove;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.file_name);
                type = v.findViewById(R.id.file_type);
                icon = v.findViewById(R.id.file_icon);
                remove = v.findViewById(R.id.btn_remove);
            }
        }
    }
}
