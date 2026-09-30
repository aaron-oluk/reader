package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScanFilmstripAdapter extends RecyclerView.Adapter<ScanFilmstripAdapter.ThumbHolder> {

    public interface OnDeleteListener {
        void onDelete(int position);
    }

    private final List<String> paths;
    private final Set<String> processing = new HashSet<>();
    private OnDeleteListener deleteListener;

    public void setProcessing(String path, boolean busy) {
        if (busy) processing.add(path);
        else processing.remove(path);
    }

    public ScanFilmstripAdapter(List<String> paths) {
        this.paths = paths;
    }

    public void setOnDeleteListener(OnDeleteListener l) {
        this.deleteListener = l;
    }

    @NonNull
    @Override
    public ThumbHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_filmstrip, parent, false);
        return new ThumbHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ThumbHolder h, int position) {
        String path = paths.get(position);
        h.thumb.setImageBitmap(decodeThumb(path));
        h.progress.setVisibility(processing.contains(path) ? View.VISIBLE : View.GONE);
        h.number.setText(String.valueOf(position + 1));
        h.delete.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && deleteListener != null) {
                deleteListener.onDelete(pos);
            }
        });
    }

    private static Bitmap decodeThumb(String path) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 8;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        if (bitmap == null) return null;
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) bitmap.recycle();
                bitmap = rotated;
            }
        } catch (Exception ignored) {
        }
        return bitmap;
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    static class ThumbHolder extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView number;
        View delete;
        ProgressBar progress;

        ThumbHolder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.filmstrip_thumb);
            number = v.findViewById(R.id.filmstrip_number);
            delete = v.findViewById(R.id.filmstrip_delete);
            progress = v.findViewById(R.id.filmstrip_progress);
        }
    }
}
