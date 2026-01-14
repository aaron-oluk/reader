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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReviewSignatureActivity extends AppCompatActivity {

    public static final String EXTRA_SAVED_SIGNATURE_PATH = "saved_signature_path";

    private ImageView originalImage;
    private ImageView processedImage;
    private Slider sensitivitySlider;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_signature);

        originalImage = findViewById(R.id.original_image);
        processedImage = findViewById(R.id.processed_image);
        sensitivitySlider = findViewById(R.id.sensitivity_slider);
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
    }

    private void loadAndProcessImage() {
        showProcessing("Loading image...");

        executorService.execute(() -> {
            // Load original bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Scale down to save memory
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

            runOnUiThread(() -> {
                hideProcessing();
                originalImage.setImageBitmap(originalBitmap);
                processedImage.setImageBitmap(processedBitmap);
            });
        });
    }

    private void reprocessImage() {
        if (originalBitmap == null) return;

        showProcessing("Adjusting...");

        executorService.execute(() -> {
            // Recycle old processed bitmap
            if (processedBitmap != null && processedBitmap != originalBitmap) {
                processedBitmap.recycle();
            }

            processedBitmap = SignatureProcessor.processSignature(originalBitmap, currentSensitivity);

            runOnUiThread(() -> {
                hideProcessing();
                processedImage.setImageBitmap(processedBitmap);
            });
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
        showProcessing("Saving signature...");

        executorService.execute(() -> {
            // Scale signature to a reasonable size
            Bitmap scaledSignature = SignatureProcessor.scaleSignature(processedBitmap, 800, 400);
            String savedPath = signatureManager.saveSignature(scaledSignature, name);

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
