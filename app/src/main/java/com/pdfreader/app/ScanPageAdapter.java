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

public class ScanPageAdapter extends RecyclerView.Adapter<ScanPageAdapter.PageViewHolder> {

    public interface OnPageDeleteListener {
        void onDelete(int position);
    }

    private final List<String> imagePaths;
    private OnPageDeleteListener deleteListener;

    public ScanPageAdapter(List<String> imagePaths) {
        this.imagePaths = imagePaths;
    }

    public void setOnPageDeleteListener(OnPageDeleteListener listener) {
        this.deleteListener = listener;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        String path = imagePaths.get(position);
        holder.pageNumber.setText(String.valueOf(position + 1));
        holder.thumbnail.setImageBitmap(BitmapFactory.decodeFile(path));
        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && deleteListener != null) {
                deleteListener.onDelete(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView pageNumber;
        View btnDelete;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.page_thumbnail);
            pageNumber = itemView.findViewById(R.id.page_number);
            btnDelete = itemView.findViewById(R.id.btn_delete_page);
        }
    }
}
