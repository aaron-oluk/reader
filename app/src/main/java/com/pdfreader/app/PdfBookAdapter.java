package com.pdfreader.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class PdfBookAdapter extends RecyclerView.Adapter<PdfBookAdapter.ViewHolder> {

    private Context context;
    private List<PdfBook> pdfBooks;
    private ExecutorService executorService;
    private Handler mainHandler;

    public PdfBookAdapter(Context context, List<PdfBook> pdfBooks) {
        this.context = context;
        this.pdfBooks = pdfBooks;
        this.executorService = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PdfBook book = pdfBooks.get(position);
        holder.titleTextView.setText(book.getTitle());
        holder.sizeTextView.setText(book.getFileSize());

        // Reset thumbnail to default
        holder.thumbnailImageView.setImageResource(R.drawable.placeholder_book);
        holder.thumbnailImageView.setTag(position);

        // Load thumbnail in background
        executorService.execute(() -> {
            Bitmap thumbnail = PdfThumbnailGenerator.generateThumbnail(
                context, 
                book.getFilePath(), 
                200, 
                300
            );
            
            mainHandler.post(() -> {
                // Check if this view is still showing the same position
                if (holder.thumbnailImageView.getTag() != null && 
                    (int) holder.thumbnailImageView.getTag() == position && 
                    thumbnail != null) {
                    holder.thumbnailImageView.setImageBitmap(thumbnail);
                }
            });
        });

        holder.itemView.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).openPdfReader(book.getFilePath(), book.getTitle());
            } else {
                // Handle click from Fragment (HomeFragment)
                Intent intent = new Intent(context, PdfReaderActivity.class);
                intent.putExtra("PDF_PATH", book.getFilePath());
                intent.putExtra("PDF_TITLE", book.getTitle());
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pdfBooks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View itemView;
        TextView titleTextView;
        TextView sizeTextView;
        ImageView thumbnailImageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView.findViewById(R.id.cardView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            sizeTextView = itemView.findViewById(R.id.sizeTextView);
            thumbnailImageView = itemView.findViewById(R.id.thumbnailImageView);
        }
    }

    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
