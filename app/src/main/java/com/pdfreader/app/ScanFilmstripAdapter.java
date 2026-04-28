package com.pdfreader.app;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScanFilmstripAdapter extends RecyclerView.Adapter<ScanFilmstripAdapter.ThumbHolder> {

    public interface OnDeleteListener {
        void onDelete(int position);
    }

    private final List<String> paths;
    private OnDeleteListener deleteListener;

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
        h.thumb.setImageBitmap(BitmapFactory.decodeFile(paths.get(position)));
        h.number.setText(String.valueOf(position + 1));
        h.delete.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && deleteListener != null) {
                deleteListener.onDelete(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    static class ThumbHolder extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView number;
        View delete;

        ThumbHolder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.filmstrip_thumb);
            number = v.findViewById(R.id.filmstrip_number);
            delete = v.findViewById(R.id.filmstrip_delete);
        }
    }
}
