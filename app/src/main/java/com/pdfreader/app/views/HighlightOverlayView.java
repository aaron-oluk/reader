package com.pdfreader.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.pdfreader.app.NotesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay view for highlighting and selecting lines/areas on PDF pages
 */
public class HighlightOverlayView extends View {

    private static final float HIGHLIGHT_ALPHA = 0.3f;
    private static final float LINE_HEIGHT_RATIO = 0.04f; // Approximate line height (4% of page height)
    
    private Paint highlightPaint;
    private Paint selectionPaint;
    private List<Highlight> highlights;
    private Highlight selectedHighlight;
    private OnHighlightListener listener;
    private int pageNumber;
    private float pageHeight;
    private float pageWidth;
    private Highlight currentSelection;

    public interface OnHighlightListener {
        void onLineSelected(int page, float yPosition, float x, float y, float width, float height);
        void onHighlightTapped(Highlight highlight);
    }

    public static class Highlight {
        public String id;
        public int page;
        public float x; // Normalized (0.0 to 1.0) or absolute pixels
        public float y; // Normalized (0.0 to 1.0) or absolute pixels
        public float width; // Normalized (0.0 to 1.0) or absolute pixels
        public float height; // Normalized (0.0 to 1.0) or absolute pixels
        public String note;
        public int color;
        public boolean normalized; // Whether coordinates are normalized

        public Highlight(String id, int page, float x, float y, float width, float height, String note) {
            this.id = id;
            this.page = page;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.note = note;
            this.color = Color.YELLOW; // Default highlight color
            this.normalized = false; // Assume absolute pixels by default
        }

        public Highlight(String id, int page, float x, float y, float width, float height, String note, boolean normalized) {
            this.id = id;
            this.page = page;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.note = note;
            this.color = Color.YELLOW;
            this.normalized = normalized;
        }

        public RectF getRect(float viewWidth, float viewHeight) {
            if (normalized) {
                // Convert normalized to pixel coordinates
                return new RectF(
                    x * viewWidth,
                    y * viewHeight,
                    (x + width) * viewWidth,
                    (y + height) * viewHeight
                );
            } else {
                // Use absolute coordinates
                return new RectF(x, y, x + width, y + height);
            }
        }

        public boolean contains(float x, float y, float viewWidth, float viewHeight) {
            return getRect(viewWidth, viewHeight).contains(x, y);
        }
    }

    public HighlightOverlayView(Context context) {
        super(context);
        init();
    }

    public HighlightOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HighlightOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        highlights = new ArrayList<>();
        
        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.YELLOW);
        highlightPaint.setAlpha((int) (255 * HIGHLIGHT_ALPHA));
        highlightPaint.setStyle(Paint.Style.FILL);

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(Color.BLUE);
        selectionPaint.setAlpha((int) (255 * 0.2f));
        selectionPaint.setStyle(Paint.Style.FILL);
        
        setClickable(true);
    }

    public void setPageInfo(int pageNumber, float pageWidth, float pageHeight) {
        this.pageNumber = pageNumber;
        // Store actual page dimensions for coordinate calculations
        this.pageWidth = pageWidth > 0 ? pageWidth : getWidth();
        this.pageHeight = pageHeight > 0 ? pageHeight : getHeight();
        invalidate();
    }

    public void setHighlights(List<Highlight> highlights) {
        this.highlights = highlights != null ? new ArrayList<>(highlights) : new ArrayList<>();
        invalidate();
    }

    public void addHighlight(Highlight highlight) {
        if (highlight != null) {
            highlights.add(highlight);
            invalidate();
        }
    }

    public void removeHighlight(String highlightId) {
        highlights.removeIf(h -> h.id.equals(highlightId));
        invalidate();
    }

    public void setOnHighlightListener(OnHighlightListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Match parent size (FrameLayout) - will be constrained by ImageView
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        
        // If height is unspecified, use wrap_content behavior
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            // Use page height if available, otherwise use a default
            height = pageHeight > 0 ? (int) pageHeight : height;
        }
        
        setMeasuredDimension(width, height);
        
        // Update page dimensions from measured size
        if (pageWidth <= 0 && width > 0) {
            pageWidth = width;
        }
        if (pageHeight <= 0 && height > 0) {
            pageHeight = height;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        
        if (viewWidth <= 0 || viewHeight <= 0) return;
        
        // Draw all highlights
        for (Highlight highlight : highlights) {
            if (highlight.page == pageNumber) {
                highlightPaint.setColor(highlight.color);
                canvas.drawRect(highlight.getRect(viewWidth, viewHeight), highlightPaint);
            }
        }
        
        // Draw current selection (if any)
        if (currentSelection != null) {
            canvas.drawRect(currentSelection.getRect(viewWidth, viewHeight), selectionPaint);
        }
    }

    private long downTime;
    private float downX, downY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null || pageHeight <= 0) return super.onTouchEvent(event);

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis();
                downX = x;
                downY = y;
                // Check if tapping on existing highlight
                selectedHighlight = findHighlightAt(x, y);
                if (selectedHighlight != null) {
                    // User tapped on existing highlight
                    if (listener != null) {
                        listener.onHighlightTapped(selectedHighlight);
                    }
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                long pressDuration = System.currentTimeMillis() - downTime;
                float viewWidth = getWidth();
                float viewHeight = getHeight();
                
                // Long press (500ms) to select line
                if (selectedHighlight == null && pressDuration > 500 && viewHeight > 0) {
                    // User long-pressed to select a line
                    float yPosition = downY / viewHeight; // Normalized position (0.0 to 1.0)
                    float lineHeight = viewHeight * LINE_HEIGHT_RATIO;
                    float lineY = downY - lineHeight / 2;
                    
                    // Normalize coordinates for storage (0.0 to 1.0)
                    float normalizedX = 0.0f;
                    float normalizedY = lineY / viewHeight;
                    float normalizedWidth = 1.0f;
                    float normalizedHeight = LINE_HEIGHT_RATIO;
                    
                    if (listener != null) {
                        // Pass both normalized and absolute coordinates
                        listener.onLineSelected(pageNumber, yPosition, 
                                normalizedX, normalizedY, normalizedWidth, normalizedHeight);
                    }
                } else if (selectedHighlight == null && viewHeight > 0) {
                    // Short tap - show selection preview briefly
                    float lineHeight = viewHeight * LINE_HEIGHT_RATIO;
                    currentSelection = new Highlight("temp", pageNumber, 0, downY - lineHeight / 2, 
                            viewWidth, lineHeight, null);
                    invalidate();
                    postDelayed(() -> {
                        currentSelection = null;
                        invalidate();
                    }, 300);
                }
                selectedHighlight = null;
                return true;

            case MotionEvent.ACTION_CANCEL:
                currentSelection = null;
                selectedHighlight = null;
                invalidate();
                break;
        }

        return true;
    }

    private Highlight findHighlightAt(float x, float y) {
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        for (Highlight highlight : highlights) {
            if (highlight.page == pageNumber && highlight.contains(x, y, viewWidth, viewHeight)) {
                return highlight;
            }
        }
        return null;
    }

    public List<Highlight> getHighlightsForPage(int page) {
        List<Highlight> pageHighlights = new ArrayList<>();
        for (Highlight highlight : highlights) {
            if (highlight.page == page) {
                pageHighlights.add(highlight);
            }
        }
        return pageHighlights;
    }
}
