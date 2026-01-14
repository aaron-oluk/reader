package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
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
 * Memory-efficient PDF page adapter that only renders visible pages
 */
public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.PageViewHolder> {

    private static final String TAG = "PdfPageAdapter";
    
    private final Context context;
    private final PdfRenderer pdfRenderer;
    private final int pageCount;
    private final int screenWidth;
    private final Map<Integer, Bitmap> bitmapCache;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private static final int MAX_CACHE_SIZE = 5; // Only cache 5 pages

    public PdfPageAdapter(Context context, PdfRenderer pdfRenderer, int screenWidth) {
        this.context = context;
        this.pdfRenderer = pdfRenderer;
        this.pageCount = pdfRenderer.getPageCount();
        this.screenWidth = screenWidth - 32;
        this.bitmapCache = new HashMap<>();
        this.executor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "PdfPageAdapter created with " + pageCount + " pages");
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        View view = LayoutInflater.from(context).inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder called for position " + position);
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        Log.d(TAG, "getItemCount: " + pageCount);
        return pageCount;
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    public void cleanup() {
        executor.shutdown();
        for (Bitmap bitmap : bitmapCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        bitmapCache.clear();
    }

    class PageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView pageImageView;
        private final ProgressBar progressBar;
        private Bitmap currentBitmap;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageImageView = itemView.findViewById(R.id.page_image);
            progressBar = itemView.findViewById(R.id.page_progress);
        }

        public void bind(int position) {
            // Show loading
            progressBar.setVisibility(View.VISIBLE);
            pageImageView.setImageBitmap(null);
            pageImageView.setVisibility(View.VISIBLE);

            // Check cache first
            if (bitmapCache.containsKey(position)) {
                Bitmap cached = bitmapCache.get(position);
                if (cached != null && !cached.isRecycled()) {
                    Log.d(TAG, "Using cached bitmap for page " + position + ", size: " + cached.getWidth() + "x" + cached.getHeight());
                    pageImageView.setImageBitmap(cached);
                    currentBitmap = cached;
                    progressBar.setVisibility(View.GONE);
                    return;
                }
            }

            // Render in background
            executor.execute(() -> {
                try {
                    Log.d(TAG, "Rendering page " + position);
                    Bitmap bitmap = renderPage(position);
                    Log.d(TAG, "Page " + position + " rendered: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", isRecycled: " + bitmap.isRecycled());
                    mainHandler.post(() -> {
                        int adapterPosition = getBindingAdapterPosition();
                        Log.d(TAG, "Posting bitmap for position " + position + ", adapter position: " + adapterPosition);
                        if (adapterPosition == position && adapterPosition != RecyclerView.NO_POSITION) {
                            pageImageView.setImageBitmap(bitmap);
                            pageImageView.setVisibility(View.VISIBLE);
                            currentBitmap = bitmap;
                            progressBar.setVisibility(View.GONE);
                            Log.d(TAG, "Page " + position + " displayed successfully");
                            
                            // Add to cache and manage cache size
                            cacheBitmap(position, bitmap);
                        } else {
                            Log.d(TAG, "Page " + position + " scrolled away, recycling bitmap");
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

        private Bitmap renderPage(int pageIndex) {
            synchronized (pdfRenderer) {
                PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
                
                int pageWidth = page.getWidth();
                int pageHeight = page.getHeight();
                Log.d(TAG, "Page " + pageIndex + " dimensions: " + pageWidth + "x" + pageHeight);
                
                float scale = (float) screenWidth / pageWidth;
                int width = screenWidth;
                int height = (int) (pageHeight * scale);
                
                Log.d(TAG, "Creating bitmap: " + width + "x" + height + " with scale: " + scale);
                
                // Use ARGB_8888 for better compatibility (some ImageViews have issues with RGB_565)
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFFFFFFFF);
                
                Log.d(TAG, "Rendering page " + pageIndex + " to bitmap");
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                
                // Verify bitmap has content (check if it's not all white)
                int[] pixels = new int[width * Math.min(100, height)]; // Sample first 100 rows
                bitmap.getPixels(pixels, 0, width, 0, 0, width, Math.min(100, height));
                boolean hasContent = false;
                for (int pixel : pixels) {
                    if (pixel != 0xFFFFFFFF) { // Not white
                        hasContent = true;
                        break;
                    }
                }
                Log.d(TAG, "Page " + pageIndex + " rendered, hasContent: " + hasContent + ", bitmap size: " + bitmap.getByteCount() + " bytes");
                return bitmap;
            }
        }

        private void cacheBitmap(int position, Bitmap bitmap) {
            // Remove oldest entries if cache is full
            if (bitmapCache.size() >= MAX_CACHE_SIZE) {
                // Remove farthest page from current position
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
                // Don't recycle cached bitmaps, they might be reused
                if (!bitmapCache.containsValue(currentBitmap)) {
                    currentBitmap.recycle();
                }
                currentBitmap = null;
            }
        }
    }
}
