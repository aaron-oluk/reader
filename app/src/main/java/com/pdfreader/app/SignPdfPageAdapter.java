package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memory-efficient PDF page adapter for signing with signature placement
 */
public class SignPdfPageAdapter extends RecyclerView.Adapter<SignPdfPageAdapter.PageViewHolder> {

    private static final String TAG = "SignPdfPageAdapter";
    
    private final Context context;
    private final PdfRenderer pdfRenderer;
    private final int pageCount;
    private final int screenWidth;
    private final Map<Integer, Bitmap> bitmapCache;
    private final Map<Integer, Bitmap> signatureCache;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final OnPageClickListener onPageClickListener;
    private Bitmap currentSignatureBitmap;
    private static final int MAX_CACHE_SIZE = 3; // Cache fewer pages for signing

    public interface OnPageClickListener {
        void onPageClick(int pageIndex);
    }

    public SignPdfPageAdapter(Context context, PdfRenderer pdfRenderer, int screenWidth, 
                             OnPageClickListener listener) {
        this.context = context;
        this.pdfRenderer = pdfRenderer;
        this.pageCount = pdfRenderer.getPageCount();
        this.screenWidth = screenWidth - 32;
        this.bitmapCache = new HashMap<>();
        this.signatureCache = new HashMap<>();
        this.executor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.onPageClickListener = listener;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return pageCount;
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    public void setSignature(Bitmap signatureBitmap, int pageIndex) {
        currentSignatureBitmap = signatureBitmap;
        if (signatureBitmap != null) {
            signatureCache.put(pageIndex, signatureBitmap);
        }
        notifyItemChanged(pageIndex);
    }

    public void cleanup() {
        executor.shutdown();
        for (Bitmap bitmap : bitmapCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        bitmapCache.clear();
        signatureCache.clear();
    }

    class PageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView pageImageView;
        private final ProgressBar progressBar;
        private Bitmap currentBitmap;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageImageView = itemView.findViewById(R.id.page_image);
            progressBar = itemView.findViewById(R.id.page_progress);
            
            itemView.setOnClickListener(v -> {
                if (onPageClickListener != null) {
                    onPageClickListener.onPageClick(getBindingAdapterPosition());
                }
            });
        }

        public void bind(int position) {
            // Show loading
            progressBar.setVisibility(View.VISIBLE);
            pageImageView.setImageBitmap(null);

            // Render in background
            executor.execute(() -> {
                try {
                    Bitmap bitmap = renderPageWithSignature(position);
                    mainHandler.post(() -> {
                        if (getBindingAdapterPosition() == position) {
                            pageImageView.setImageBitmap(bitmap);
                            currentBitmap = bitmap;
                            progressBar.setVisibility(View.GONE);
                            
                            // Add to cache
                            cacheBitmap(position, bitmap);
                        } else {
                            // Page was scrolled away, recycle immediately
                            if (!bitmap.isRecycled()) {
                                bitmap.recycle();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error rendering page " + position, e);
                    mainHandler.post(() -> progressBar.setVisibility(View.GONE));
                }
            });
        }

        private Bitmap renderPageWithSignature(int pageIndex) {
            synchronized (pdfRenderer) {
                PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
                
                float scale = (float) screenWidth / page.getWidth();
                int width = screenWidth;
                int height = (int) (page.getHeight() * scale);
                
                // Use ARGB_8888 for signature support (needs alpha channel)
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFFFFFFFF);
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                
                // Add signature if present for this page
                if (signatureCache.containsKey(pageIndex)) {
                    Bitmap signature = signatureCache.get(pageIndex);
                    if (signature != null && !signature.isRecycled()) {
                        Canvas canvas = new Canvas(bitmap);
                        
                        // Scale signature to reasonable size
                        int sigWidth = bitmap.getWidth() / 4;
                        int sigHeight = (int) ((float) signature.getHeight() / signature.getWidth() * sigWidth);
                        Bitmap scaledSignature = Bitmap.createScaledBitmap(signature, sigWidth, sigHeight, true);
                        
                        // Place at bottom right
                        int x = bitmap.getWidth() - sigWidth - 50;
                        int y = bitmap.getHeight() - sigHeight - 100;
                        
                        canvas.drawBitmap(scaledSignature, x, y, null);
                        scaledSignature.recycle();
                    }
                }
                
                return bitmap;
            }
        }

        private void cacheBitmap(int position, Bitmap bitmap) {
            // Remove oldest entries if cache is full
            if (bitmapCache.size() >= MAX_CACHE_SIZE) {
                int farthestKey = -1;
                int maxDistance = 0;
                for (int key : bitmapCache.keySet()) {
                    int distance = Math.abs(key - position);
                    if (distance > maxDistance) {
                        maxDistance = distance;
                        farthestKey = key;
                    }
                }
                if (farthestKey != -1) {
                    Bitmap removed = bitmapCache.remove(farthestKey);
                    if (removed != null && !removed.isRecycled()) {
                        removed.recycle();
                    }
                }
            }
            bitmapCache.put(position, bitmap);
        }

        public void recycle() {
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                if (!bitmapCache.containsValue(currentBitmap)) {
                    currentBitmap.recycle();
                }
                currentBitmap = null;
            }
        }
    }
}
