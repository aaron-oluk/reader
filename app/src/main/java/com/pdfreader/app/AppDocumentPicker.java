package com.pdfreader.app;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lists PDFs already stored in the app so a screen can open one without the system importer. */
public final class AppDocumentPicker {

    public interface Listener {
        void onDocumentPicked(String path);
    }

    private AppDocumentPicker() {}

    /** Fills a list with documents already in the app. Returns the thumbnail loader to shut down. */
    public static ExecutorService bind(AppCompatActivity activity, RecyclerView recycler,
                                       View empty, Listener listener) {
        List<Doc> documents = collect(activity);
        if (documents.isEmpty()) {
            recycler.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            recycler.setAdapter(null);
            return null;
        }
        ExecutorService thumbs = Executors.newSingleThreadExecutor();
        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setAdapter(new Adapter(activity, documents, thumbs, listener::onDocumentPicked));
        return thumbs;
    }

    private static List<Doc> collect(AppCompatActivity activity) {
        LinkedHashMap<String, Doc> unique = new LinkedHashMap<>();
        for (PdfBook book : new HistoryManager(activity).getHistory()) {
            String path = book.getFilePath();
            if (!isSelectable(path)) continue;
            String title = book.getTitle();
            if (title == null || title.trim().isEmpty()) title = titleFromPath(path);
            unique.put(path, new Doc(title, path, folderLabel(path)));
        }
        for (File file : new FileManager(activity).listSavedPdfs()) {
            String path = file.getAbsolutePath();
            if (unique.containsKey(path)) continue;
            unique.put(path, new Doc(stripPdf(file.getName()), path, folderLabel(path)));
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean isSelectable(String path) {
        if (path == null || path.isEmpty()) return false;
        String lower = path.toLowerCase(Locale.US);
        if (lower.endsWith(".epub")) return false;
        if (path.startsWith("content:")) return true;
        return lower.endsWith(".pdf") && new File(path).isFile();
    }

    private static String folderLabel(String path) {
        if (path.startsWith("content:")) return "Recent";
        File parent = new File(path).getParentFile();
        if (parent == null) return "In this app";
        String name = parent.getName();
        if (FileManager.CATEGORY_SCANNED.equals(name)
                || FileManager.CATEGORY_CONVERTED.equals(name)
                || FileManager.CATEGORY_SIGNED.equals(name)
                || FileManager.CATEGORY_MERGED.equals(name)) {
            return name;
        }
        return "Library";
    }

    private static String titleFromPath(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
        }
        return stripPdf(name);
    }

    private static String stripPdf(String name) {
        if (name.toLowerCase(Locale.US).endsWith(".pdf")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static final class Doc {
        final String title;
        final String path;
        final String folder;

        Doc(String title, String path, String folder) {
            this.title = title;
            this.path = path;
            this.folder = folder;
        }
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final AppCompatActivity activity;
        private final List<Doc> documents;
        private final ExecutorService thumbs;
        private final java.util.function.Consumer<String> onPick;

        Adapter(AppCompatActivity activity, List<Doc> documents, ExecutorService thumbs,
                java.util.function.Consumer<String> onPick) {
            this.activity = activity;
            this.documents = documents;
            this.thumbs = thumbs;
            this.onPick = onPick;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_document, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Doc doc = documents.get(position);
            holder.title.setText(doc.title);
            holder.folder.setText(doc.folder);
            holder.thumb.setImageResource(R.drawable.ic_pdf);
            holder.thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
            holder.thumb.setPadding(pad, pad, pad, pad);
            holder.thumb.setColorFilter(activity.getColor(R.color.primary_blue));
            holder.thumb.setTag(doc.path);
            holder.itemView.setOnClickListener(v -> onPick.accept(doc.path));
            thumbs.execute(() -> {
                Bitmap thumb = PdfThumbnailGenerator.generateThumbnail(activity, doc.path, 144, 192);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || !doc.path.equals(holder.thumb.getTag())) {
                        if (thumb != null) thumb.recycle();
                        return;
                    }
                    if (thumb != null) {
                        holder.thumb.setImageBitmap(thumb);
                        holder.thumb.clearColorFilter();
                        holder.thumb.setPadding(0, 0, 0, 0);
                        holder.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return documents.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title;
            final TextView folder;

            Holder(View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.document_thumb);
                title = itemView.findViewById(R.id.document_title);
                folder = itemView.findViewById(R.id.document_folder);
            }
        }
    }
}
