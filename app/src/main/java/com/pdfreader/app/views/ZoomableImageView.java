package com.pdfreader.app.views;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView that supports pinch-to-zoom and pan gestures
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;

    private Matrix matrix;
    private float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;

    private PointF last = new PointF();
    private int mode = NONE;

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    private float viewWidth, viewHeight;
    private float saveScale = 1f;
    private float bmWidth, bmHeight;
    private float minScale = 1f;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        super.setClickable(true);
        matrix = new Matrix();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(matrix);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            bmWidth = drawable.getIntrinsicWidth();
            bmHeight = drawable.getIntrinsicHeight();
            // Ensure proper initial display
            post(() -> {
                if (viewWidth > 0 && viewHeight > 0 && bmWidth > 0 && bmHeight > 0) {
                    fitToScreen();
                }
            });
        }
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        if (bitmap != null) {
            bmWidth = bitmap.getWidth();
            bmHeight = bitmap.getHeight();
            // Ensure proper initial display - wait for layout
            if (viewWidth > 0 && viewHeight > 0) {
                fitToScreen();
            } else {
                // View not measured yet, wait for layout
                post(() -> {
                    if (viewWidth > 0 && viewHeight > 0 && bmWidth > 0 && bmHeight > 0) {
                        fitToScreen();
                    }
                });
            }
        }
    }

    public void resetZoom() {
        fitToScreen();
    }
    
    private void fitToScreen() {
        if (bmWidth <= 0 || bmHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        
        // Calculate scale to fit image in view
        float scaleX = viewWidth / bmWidth;
        float scaleY = viewHeight / bmHeight;
        float fitScale = Math.min(scaleX, scaleY);
        
        saveScale = fitScale;
        minScale = fitScale;
        
        // Calculate scaled dimensions
        float scaledWidth = bmWidth * fitScale;
        float scaledHeight = bmHeight * fitScale;
        
        // Center the image
        float translateX = (viewWidth - scaledWidth) / 2f;
        float translateY = (viewHeight - scaledHeight) / 2f;
        
        matrix.reset();
        matrix.postScale(fitScale, fitScale);
        matrix.postTranslate(translateX, translateY);
        setImageMatrix(matrix);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (bmWidth > 0 && bmHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            float scaleX = viewWidth / bmWidth;
            float scaleY = viewHeight / bmHeight;
            minScale = Math.min(scaleX, scaleY);

            if (saveScale < minScale) {
                fitToScreen();
            }
        }
    }

    private void fixTranslation() {
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float scaledImageWidth = bmWidth * saveScale;
        float scaledImageHeight = bmHeight * saveScale;

        float fixTransX = getFixTranslation(transX, viewWidth, scaledImageWidth);
        float fixTransY = getFixTranslation(transY, viewHeight, scaledImageHeight);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTranslation(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            // Content is smaller than view - center it
            minTrans = (viewSize - contentSize) / 2f;
            maxTrans = minTrans;
        } else {
            // Content is larger than view - allow panning
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans) {
            return minTrans - trans;
        }
        if (trans > maxTrans) {
            return maxTrans - trans;
        }
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        matrix.getValues(matrixValues);
        float x = matrixValues[Matrix.MTRANS_X];
        float y = matrixValues[Matrix.MTRANS_Y];
        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                last.set(event.getX(), event.getY());
                mode = DRAG;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                last.set(event.getX(), event.getY());
                mode = ZOOM;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float deltaX = curr.x - last.x;
                    float deltaY = curr.y - last.y;
                    float scaledWidth = bmWidth * saveScale;
                    float scaledHeight = bmHeight * saveScale;

                    // Don't allow panning if image is smaller than view
                    if (scaledWidth < viewWidth) {
                        deltaX = 0;
                    }
                    if (scaledHeight < viewHeight) {
                        deltaY = 0;
                    }

                    matrix.postTranslate(deltaX, deltaY);
                    fixTranslation();
                    last.set(curr.x, curr.y);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }

        setImageMatrix(matrix);
        invalidate();
        return true;
    }

    public void setZoomLevel(float zoomLevel) {
        if (bmWidth <= 0 || bmHeight <= 0) return;
        
        float newScale = Math.max(minScale, Math.min(MAX_SCALE, zoomLevel * minScale));
        float scaleFactor = newScale / saveScale;
        
        saveScale = newScale;
        matrix.postScale(scaleFactor, scaleFactor, viewWidth / 2, viewHeight / 2);
        fixTranslation();
        setImageMatrix(matrix);
        invalidate();
    }

    public float getZoomLevel() {
        if (minScale == 0) return 1f;
        return saveScale / minScale;
    }
    
    /**
     * Get current view state (zoom and pan) to restore later
     */
    public float[] getViewState() {
        matrix.getValues(matrixValues);
        return new float[] {
            saveScale,
            matrixValues[Matrix.MTRANS_X],
            matrixValues[Matrix.MTRANS_Y]
        };
    }
    
    /**
     * Restore a previously saved view state (zoom and pan)
     */
    public void restoreViewState(float[] state) {
        if (state == null || state.length < 3) return;
        if (bmWidth <= 0 || bmHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;
        
        float savedScale = state[0];
        float savedTransX = state[1];
        float savedTransY = state[2];
        
        // Constrain scale
        float maxAllowedScale = minScale * MAX_SCALE;
        saveScale = Math.max(minScale, Math.min(maxAllowedScale, savedScale));
        
        matrix.reset();
        matrix.postScale(saveScale, saveScale);
        matrix.postTranslate(savedTransX, savedTransY);
        fixTranslation();
        setImageMatrix(matrix);
        invalidate();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = saveScale * scaleFactor;
            
            // Constrain to min/max scale
            float maxAllowedScale = minScale * MAX_SCALE;
            if (newScale < minScale) {
                scaleFactor = minScale / saveScale;
            } else if (newScale > maxAllowedScale) {
                scaleFactor = maxAllowedScale / saveScale;
            }

            saveScale *= scaleFactor;
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            fixTranslation();
            return true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;

        if (getDrawable() != null && bmWidth > 0 && bmHeight > 0 && w > 0 && h > 0) {
            // Delay slightly to ensure the view is fully laid out
            post(() -> fitToScreen());
        }
    }
}
