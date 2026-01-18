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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private String pdfPath;
    private com.pdfreader.app.views.HighlightOverlayView.OnHighlightListener highlightListener;

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

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public void setOnHighlightListener(com.pdfreader.app.views.HighlightOverlayView.OnHighlightListener listener) {
        this.highlightListener = listener;
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
        private final com.pdfreader.app.views.HighlightOverlayView highlightOverlay;
        private Bitmap currentBitmap;
        private String pdfPath;
        private NotesManager notesManager;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageImageView = itemView.findViewById(R.id.page_image);
            progressBar = itemView.findViewById(R.id.page_progress);
            highlightOverlay = itemView.findViewById(R.id.highlight_overlay);
        }

        public void setPdfPath(String pdfPath) {
            this.pdfPath = pdfPath;
            if (notesManager == null && context != null) {
                notesManager = new NotesManager(context);
            }
        }

        public void setOnHighlightListener(com.pdfreader.app.views.HighlightOverlayView.OnHighlightListener listener) {
            if (highlightOverlay != null) {
                highlightOverlay.setOnHighlightListener(listener);
            }
        }

        public void bind(int position) {
            // Show loading
            progressBar.setVisibility(View.VISIBLE);
            pageImageView.setImageBitmap(null);
            pageImageView.setVisibility(View.VISIBLE);

            // Setup highlight overlay
            if (highlightOverlay != null && pdfPath != null) {
                setPdfPath(pdfPath);
                if (highlightListener != null) {
                    setOnHighlightListener(highlightListener);
                }
                loadHighlightsForPage(position);
            }

            // Check cache first
            if (bitmapCache.containsKey(position)) {
                Bitmap cached = bitmapCache.get(position);
                if (cached != null && !cached.isRecycled()) {
                    Log.d(TAG, "Using cached bitmap for page " + position + ", size: " + cached.getWidth() + "x" + cached.getHeight());
                    pageImageView.setImageBitmap(cached);
                    currentBitmap = cached;
                    progressBar.setVisibility(View.GONE);
                    updateOverlaySize(cached);
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
                            
                            // Update overlay size and load highlights
                            updateOverlaySize(bitmap);
                            loadHighlightsForPage(position);
                            
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

        private void updateOverlaySize(Bitmap bitmap) {
            if (highlightOverlay != null && pageImageView != null) {
                // Wait for image view to be laid out and measure
                pageImageView.post(() -> {
                    pageImageView.post(() -> {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            // Get actual displayed size of image
                            int imageWidth = pageImageView.getWidth();
                            int imageHeight = pageImageView.getHeight();
                            
                            // If image view hasn't measured yet, use bitmap size
                            if (imageWidth <= 0 || imageHeight <= 0) {
                                imageWidth = pageImageView.getMeasuredWidth();
                                imageHeight = pageImageView.getMeasuredHeight();
                            }
                            
                            // Fallback to bitmap size if still not available
                            if (imageWidth <= 0 && bitmap != null) {
                                imageWidth = bitmap.getWidth();
                            }
                            if (imageHeight <= 0 && bitmap != null) {
                                imageHeight = bitmap.getHeight();
                            }
                            
                            if (imageWidth > 0 && imageHeight > 0) {
                                highlightOverlay.setPageInfo(position, imageWidth, imageHeight);
                                // Request layout to ensure overlay matches image size
                                highlightOverlay.requestLayout();
                            }
                        }
                    });
                });
            }
        }

        private void loadHighlightsForPage(int pageIndex) {
            if (highlightOverlay != null && notesManager != null && pdfPath != null) {
                List<NotesManager.Note> notes = notesManager.getNotesForPage(pdfPath, pageIndex);
                List<com.pdfreader.app.views.HighlightOverlayView.Highlight> highlights = new ArrayList<>();
                
                for (NotesManager.Note note : notes) {
                    if (note.isHighlight) {
                        // Create highlight with normalized coordinates
                        com.pdfreader.app.views.HighlightOverlayView.Highlight highlight = 
                            new com.pdfreader.app.views.HighlightOverlayView.Highlight(
                                note.id, note.page, note.x, note.y, note.width, note.height, note.text, true
                            );
                        highlights.add(highlight);
                    }
                }
                
                highlightOverlay.setHighlights(highlights);
            }
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
