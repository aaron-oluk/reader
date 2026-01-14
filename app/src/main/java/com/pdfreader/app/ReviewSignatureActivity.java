package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.pdfreader.app.views.ZoomableImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReviewSignatureActivity extends AppCompatActivity {

    public static final String EXTRA_SAVED_SIGNATURE_PATH = "saved_signature_path";

    private ImageView originalImage;
    private ZoomableImageView processedImage;
    private Slider sensitivitySlider;
    private SeekBar zoomSlider;
    private ImageView btnZoomIn;
    private ImageView btnZoomOut;
    private ImageView btnResetZoom;
    private TextView zoomLevelText;
    private MaterialButton btnRetake;
    private MaterialButton btnSave;
    private ImageView btnBack;
    private FrameLayout processingOverlay;
    private TextView processingText;

    private String capturedImagePath;
    private Bitmap originalBitmap;
    private Bitmap processedBitmap;
    private SignatureManager signatureManager;
    private ExecutorService executorService;

    private int currentSensitivity = 50;
    private float[] savedViewState = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_signature);

        originalImage = findViewById(R.id.original_image);
        processedImage = findViewById(R.id.processed_image);
        sensitivitySlider = findViewById(R.id.sensitivity_slider);
        zoomSlider = findViewById(R.id.zoom_slider);
        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);
        btnResetZoom = findViewById(R.id.btn_reset_zoom);
        zoomLevelText = findViewById(R.id.zoom_level_text);
        btnRetake = findViewById(R.id.btn_retake);
        btnSave = findViewById(R.id.btn_save);
        btnBack = findViewById(R.id.btn_back);
        processingOverlay = findViewById(R.id.processing_overlay);
        processingText = findViewById(R.id.processing_text);

        signatureManager = new SignatureManager(this);
        executorService = Executors.newSingleThreadExecutor();

        // Get the captured image path
        capturedImagePath = getIntent().getStringExtra(CaptureSignatureActivity.EXTRA_CAPTURED_IMAGE_PATH);
        if (capturedImagePath == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load and process the image
        loadAndProcessImage();

        // Setup UI listeners
        btnBack.setOnClickListener(v -> finish());

        btnRetake.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnSave.setOnClickListener(v -> saveSignature());

        sensitivitySlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                currentSensitivity = (int) value;
                reprocessImage();
            }
        });

        // Setup zoom controls
        btnZoomIn.setOnClickListener(v -> {
            float currentZoom = processedImage.getZoomLevel();
            processedImage.setZoomLevel(currentZoom + 0.5f);
            updateZoomSlider();
        });

        btnZoomOut.setOnClickListener(v -> {
            float currentZoom = processedImage.getZoomLevel();
            processedImage.setZoomLevel(currentZoom - 0.5f);
            updateZoomSlider();
        });

        btnResetZoom.setOnClickListener(v -> {
            processedImage.resetZoom();
            updateZoomSlider();
        });

        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Progress 0-100 maps to zoom 1.0-5.0
                    float zoom = 1.0f + (progress / 25.0f);
                    processedImage.setZoomLevel(zoom);
                    
                    // Update zoom level text
                    int zoomPercent = (int) (zoom * 100);
                    zoomLevelText.setText(zoomPercent + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateZoomSlider() {
        float zoom = processedImage.getZoomLevel();
        // Zoom 1.0-5.0 maps to progress 0-100
        int progress = (int) ((zoom - 1.0f) * 25.0f);
        zoomSlider.setProgress(Math.max(0, Math.min(100, progress)));
        
        // Update zoom level text
        int zoomPercent = (int) (zoom * 100);
        zoomLevelText.setText(zoomPercent + "%");
    }

    private void loadAndProcessImage() {
        showProcessing("Loading image...");

        executorService.execute(() -> {
            try {
                // First, check the image dimensions
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(capturedImagePath, options);

                // Calculate appropriate sample size
                int maxDimension = 2048; // Max width or height
                int sampleSize = 1;
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    final int heightRatio = Math.round((float) options.outHeight / (float) maxDimension);
                    final int widthRatio = Math.round((float) options.outWidth / (float) maxDimension);
                    sampleSize = Math.min(heightRatio, widthRatio);
                }

                // Load the bitmap with appropriate sampling
                options.inJustDecodeBounds = false;
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                originalBitmap = BitmapFactory.decodeFile(capturedImagePath, options);

                if (originalBitmap == null) {
                    runOnUiThread(() -> {
                        hideProcessing();
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // Process the signature
                runOnUiThread(() -> {
                    processingText.setText("Processing signature...");
                });

                processedBitmap = SignatureProcessor.processSignature(originalBitmap, currentSensitivity);

                if (processedBitmap == null) {
                    runOnUiThread(() -> {
                        hideProcessing();
                        Toast.makeText(this, "Failed to process signature", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                runOnUiThread(() -> {
                    hideProcessing();
                    originalImage.setImageBitmap(originalBitmap);
                    processedImage.setImageBitmap(processedBitmap);
                    
                    // Ensure the image is properly fitted after a brief delay
                    processedImage.postDelayed(() -> {
                        processedImage.resetZoom();
                        updateZoomSlider();
                    }, 100);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideProcessing();
                    Toast.makeText(this, "Error loading image: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void reprocessImage() {
        if (originalBitmap == null || originalBitmap.isRecycled()) return;

        // Save current zoom and pan state
        savedViewState = processedImage.getViewState();

        showProcessing("Adjusting...");

        executorService.execute(() -> {
            try {
                // Create new processed bitmap
                Bitmap newProcessedBitmap = SignatureProcessor.processSignature(originalBitmap, currentSensitivity);

                if (newProcessedBitmap == null) {
                    runOnUiThread(() -> {
                        hideProcessing();
                        Toast.makeText(this, "Failed to process signature", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Store old bitmap reference
                final Bitmap oldBitmap = processedBitmap;

                // Update to new bitmap
                processedBitmap = newProcessedBitmap;

                runOnUiThread(() -> {
                    hideProcessing();
                    processedImage.setImageBitmap(processedBitmap);
                    
                    // Restore previous zoom and pan state, or fit to screen if none saved
                    processedImage.postDelayed(() -> {
                        if (savedViewState != null) {
                            processedImage.restoreViewState(savedViewState);
                        } else {
                            processedImage.resetZoom();
                        }
                        updateZoomSlider();
                    }, 50);

                    // Recycle old bitmap after UI has been updated
                    processedImage.post(() -> {
                        if (oldBitmap != null && !oldBitmap.isRecycled() && oldBitmap != originalBitmap) {
                            oldBitmap.recycle();
                        }
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideProcessing();
                    Toast.makeText(this, "Error processing signature", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveSignature() {
        if (processedBitmap == null) {
            Toast.makeText(this, "No signature to save", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show name input dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Signature");

        final EditText input = new EditText(this);
        input.setHint("Signature name (optional)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            performSave(name.isEmpty() ? null : name);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performSave(String name) {
        if (processedBitmap == null || processedBitmap.isRecycled()) {
            Toast.makeText(this, "No signature to save", Toast.LENGTH_SHORT).show();
            return;
        }

        showProcessing("Saving signature...");

        executorService.execute(() -> {
            try {
                // Scale signature to a reasonable size
                Bitmap scaledSignature = SignatureProcessor.scaleSignature(processedBitmap, 800, 400);
                
                if (scaledSignature == null) {
                    runOnUiThread(() -> {
                        hideProcessing();
                        Toast.makeText(this, "Failed to scale signature", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                String savedPath = signatureManager.saveSignature(scaledSignature, name);

                // Clean up scaled bitmap if it's different from processed
                if (scaledSignature != processedBitmap && !scaledSignature.isRecycled()) {
                    scaledSignature.recycle();
                }

                runOnUiThread(() -> {
                    hideProcessing();

                    if (savedPath != null) {
                        Toast.makeText(this, "Signature saved", Toast.LENGTH_SHORT).show();

                        // Return the saved path to the calling activity
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(EXTRA_SAVED_SIGNATURE_PATH, savedPath);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to save signature", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideProcessing();
                    Toast.makeText(this, "Error saving signature: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showProcessing(String message) {
        if (processingText != null) {
            processingText.setText(message);
        }
        if (processingOverlay != null) {
            processingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideProcessing() {
        if (processingOverlay != null) {
            processingOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (executorService != null) {
            executorService.shutdown();
        }

        // Clean up captured image file
        if (capturedImagePath != null) {
            File capturedFile = new File(capturedImagePath);
            if (capturedFile.exists()) {
                capturedFile.delete();
            }
        }

        // Recycle bitmaps
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
        if (processedBitmap != null && !processedBitmap.isRecycled() && processedBitmap != originalBitmap) {
            processedBitmap.recycle();
        }
    }
}
