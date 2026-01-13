package com.pdfreader.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PdfBookAdapter extends RecyclerView.Adapter<PdfBookAdapter.ViewHolder> {

    private Context context;
    private List<PdfBook> pdfBooks;

    public PdfBookAdapter(Context context, List<PdfBook> pdfBooks) {
        this.context = context;
        this.pdfBooks = pdfBooks;
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

        holder.cardView.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                ((MainActivity) context).openPdfReader(book.getFilePath(), book.getTitle());
            }
        });
    }

    @Override
    public int getItemCount() {
        return pdfBooks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView titleTextView;
        TextView sizeTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            sizeTextView = itemView.findViewById(R.id.sizeTextView);
        }
    }
}
