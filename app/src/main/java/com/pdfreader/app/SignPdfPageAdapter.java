package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memory-efficient PDF page adapter for signing with draggable signature placement
 */
public class SignPdfPageAdapter extends RecyclerView.Adapter<SignPdfPageAdapter.PageViewHolder> {

    private static final String TAG = "SignPdfPageAdapter";

    private final Context context;
    private final PdfRenderer pdfRenderer;
    private final int pageCount;
    private final int screenWidth;
    private final Map<Integer, Bitmap> bitmapCache;
    private final Map<Integer, SignaturePosition> signaturePositions;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final OnPageClickListener onPageClickListener;
    private Bitmap currentSignatureBitmap;
    private static final int MAX_CACHE_SIZE = 3;

    // Store signature position for each page
    public static class SignaturePosition {
        public float x, y, width, height;
        public Bitmap bitmap;

        public SignaturePosition(Bitmap bitmap, float x, float y, float width, float height) {
            this.bitmap = bitmap;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public interface OnPageClickListener {
        void onPageClick(int pageIndex, float tapX, float tapY);
    }

    public interface OnSignatureRemovedListener {
        void onSignatureRemoved(int pageIndex);
    }

    private OnSignatureRemovedListener signatureRemovedListener;

    public void setOnSignatureRemovedListener(OnSignatureRemovedListener listener) {
        this.signatureRemovedListener = listener;
    }

    public SignPdfPageAdapter(Context context, PdfRenderer pdfRenderer, int screenWidth,
                             OnPageClickListener listener) {
        this.context = context;
        this.pdfRenderer = pdfRenderer;
        this.pageCount = pdfRenderer.getPageCount();
        this.screenWidth = screenWidth - 32;
        this.bitmapCache = new HashMap<>();
        this.signaturePositions = new HashMap<>();
        this.executor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.onPageClickListener = listener;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sign_pdf_page, parent, false);
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

    /**
     * Set signature for a specific page, centred on the user's tap position.
     * tapX/tapY are coordinates relative to the page card view.
     */
    public void setSignature(Bitmap signatureBitmap, int pageIndex, float tapX, float tapY) {
        currentSignatureBitmap = signatureBitmap;
        if (signatureBitmap != null) {
            // Store tap position; rendered position is calculated in setupSignatureOverlay
            signaturePositions.put(pageIndex, new SignaturePosition(signatureBitmap, tapX, tapY, -1, -1));
        }
        notifyItemChanged(pageIndex);
    }

    /**
     * Get signature position for a specific page
     */
    public SignaturePosition getSignaturePosition(int pageIndex) {
        return signaturePositions.get(pageIndex);
    }

    /**
     * Check if a page has a signature
     */
    public boolean hasSignature(int pageIndex) {
        return signaturePositions.containsKey(pageIndex);
    }

    /**
     * Remove signature from a page
     */
    public void removeSignature(int pageIndex) {
        signaturePositions.remove(pageIndex);
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
        signaturePositions.clear();
    }

    class PageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView pageImageView;
        private final ProgressBar progressBar;
        private final DraggableSignatureView signatureOverlay;
        private final TextView pageNumber;
        private Bitmap currentBitmap;
        private int currentPosition = -1;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            pageImageView = itemView.findViewById(R.id.page_image);
            progressBar = itemView.findViewById(R.id.page_progress);
            signatureOverlay = itemView.findViewById(R.id.signature_overlay);
            pageNumber = itemView.findViewById(R.id.page_number);

            // Record tap position on ACTION_DOWN so onClick can pass the exact coordinates
            final float[] tapPosition = {0f, 0f};
            itemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    tapPosition[0] = event.getX();
                    tapPosition[1] = event.getY();
                }
                return false; // let normal click handling proceed
            });

            // Handle tap on page to place signature at the tapped position
            itemView.setOnClickListener(v -> {
                if (onPageClickListener != null && !signatureOverlay.hasSignature()) {
                    onPageClickListener.onPageClick(
                            getBindingAdapterPosition(), tapPosition[0], tapPosition[1]);
                }
            });

            // Listen for signature position changes
            signatureOverlay.setOnSignatureChangedListener((x, y, width, height) -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && signaturePositions.containsKey(pos)) {
                    SignaturePosition sigPos = signaturePositions.get(pos);
                    if (sigPos != null) {
                        sigPos.x = x;
                        sigPos.y = y;
                        sigPos.width = width;
                        sigPos.height = height;
                    }
                }
            });

            // Listen for deletion via the red ✕ handle
            signatureOverlay.setOnSignatureDeletedListener(() -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    removeSignature(pos);
                    if (signatureRemovedListener != null) {
                        signatureRemovedListener.onSignatureRemoved(pos);
                    }
                }
            });
        }

        public void bind(int position) {
            currentPosition = position;

            // Show page number
            if (pageNumber != null) {
                pageNumber.setText(String.valueOf(position + 1));
            }

            // Show loading
            progressBar.setVisibility(View.VISIBLE);
            pageImageView.setImageBitmap(null);
            signatureOverlay.setVisibility(View.GONE);

            // Render in background
            executor.execute(() -> {
                try {
                    Bitmap bitmap = renderPage(position);
                    mainHandler.post(() -> {
                        if (getBindingAdapterPosition() == position) {
                            pageImageView.setImageBitmap(bitmap);
                            currentBitmap = bitmap;
                            progressBar.setVisibility(View.GONE);

                            // Setup signature overlay if there's a signature for this page
                            setupSignatureOverlay(position, bitmap);

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

        private void setupSignatureOverlay(int position, Bitmap pageBitmap) {
            SignaturePosition sigPos = signaturePositions.get(position);
            if (sigPos != null && sigPos.bitmap != null) {
                signatureOverlay.setVisibility(View.VISIBLE);

                // Set height to match the page image
                ViewGroup.LayoutParams params = signatureOverlay.getLayoutParams();
                params.height = pageBitmap.getHeight();
                signatureOverlay.setLayoutParams(params);

                signatureOverlay.post(() -> {
                    if (sigPos.width < 0 || sigPos.height < 0) {
                        // First placement: compute size and centre on the tap position
                        float w = signatureOverlay.getWidth() * 0.3f;
                        float h = w * ((float) sigPos.bitmap.getHeight() / sigPos.bitmap.getWidth());

                        float x = sigPos.x - w / 2f;
                        float y = sigPos.y - h / 2f;

                        // Clamp to overlay bounds
                        x = Math.max(0, Math.min(x, signatureOverlay.getWidth() - w));
                        y = Math.max(0, Math.min(y, signatureOverlay.getHeight() - h));

                        signatureOverlay.setSignature(sigPos.bitmap, x, y, w, h);
                        sigPos.x = x;
                        sigPos.y = y;
                        sigPos.width = w;
                        sigPos.height = h;
                    } else {
                        // Restore previously saved position (e.g. after scroll/rebind)
                        signatureOverlay.setSignature(sigPos.bitmap, sigPos.x, sigPos.y, sigPos.width, sigPos.height);
                    }
                });
            } else {
                signatureOverlay.setVisibility(View.GONE);
                signatureOverlay.clearSignature();
            }
        }

        private Bitmap renderPage(int pageIndex) {
            synchronized (pdfRenderer) {
                PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);

                float scale = (float) screenWidth / page.getWidth();
                int width = screenWidth;
                int height = (int) (page.getHeight() * scale);

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFFFFFFFF);

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

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
            signatureOverlay.clearSignature();
        }
    }
}
