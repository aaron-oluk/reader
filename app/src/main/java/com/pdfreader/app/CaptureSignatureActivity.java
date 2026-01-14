package com.pdfreader.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureSignatureActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    public static final String EXTRA_CAPTURED_IMAGE_PATH = "captured_image_path";

    private PreviewView cameraPreview;
    private ImageView btnClose;
    private ImageView btnFlash;
    private FrameLayout captureButton;
    private FrameLayout galleryButton;
    private FrameLayout processingOverlay;
    private MaterialCardView tipsCard;

    private ImageCapture imageCapture;
    private Camera camera;
    private boolean isFlashOn = false;
    private ExecutorService cameraExecutor;
    private ActivityResultLauncher<Intent> reviewLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cropLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_signature);

        // Register activity result launcher for review screen
        reviewLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Pass the result back to the calling activity
                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                });

        // Register gallery picker launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            processGalleryImage(imageUri);
                        }
                    }
                });

        // Register crop launcher
        cropLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String croppedPath = result.getData().getStringExtra(CropSignatureActivity.EXTRA_CROPPED_IMAGE_PATH);
                        if (croppedPath != null) {
                            // Navigate to review activity with cropped image
                            Intent intent = new Intent(this, ReviewSignatureActivity.class);
                            intent.putExtra(EXTRA_CAPTURED_IMAGE_PATH, croppedPath);
                            reviewLauncher.launch(intent);
                        }
                    }
                });

        cameraPreview = findViewById(R.id.camera_preview);
        btnClose = findViewById(R.id.btn_close);
        btnFlash = findViewById(R.id.btn_flash);
        captureButton = findViewById(R.id.capture_button);
        galleryButton = findViewById(R.id.gallery_button);
        processingOverlay = findViewById(R.id.processing_overlay);
        tipsCard = findViewById(R.id.tips_card);

        cameraExecutor = Executors.newSingleThreadExecutor();

        btnClose.setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleFlash());
        captureButton.setOnClickListener(v -> captureImage());
        galleryButton.setOnClickListener(v -> openGallery());
        
        // Make tips card tappable to dismiss
        tipsCard.setOnClickListener(v -> tipsCard.setVisibility(View.GONE));
        
        // Auto-hide tips after 5 seconds
        tipsCard.postDelayed(() -> {
            if (tipsCard != null) {
                tipsCard.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> tipsCard.setVisibility(View.GONE))
                    .start();
            }
        }, 5000);

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);
            btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        }
    }

    private void captureImage() {
        if (imageCapture == null) return;

        processingOverlay.setVisibility(View.VISIBLE);

        File photoFile = new File(getCacheDir(), "signature_capture_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        runOnUiThread(() -> {
                            processingOverlay.setVisibility(View.GONE);

                            // Navigate to crop activity first
                            Intent intent = new Intent(CaptureSignatureActivity.this,
                                    CropSignatureActivity.class);
                            intent.putExtra(CropSignatureActivity.EXTRA_IMAGE_PATH, photoFile.getAbsolutePath());
                            cropLauncher.launch(intent);
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            processingOverlay.setVisibility(View.GONE);
                            Toast.makeText(CaptureSignatureActivity.this,
                                    "Error capturing image: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    @SuppressWarnings("deprecation")
    private void processGalleryImage(Uri imageUri) {
        processingOverlay.setVisibility(View.VISIBLE);

        cameraExecutor.execute(() -> {
            try {
                // Load bitmap using modern API
                Bitmap bitmap;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.Source source = 
                        android.graphics.ImageDecoder.createSource(getContentResolver(), imageUri);
                    bitmap = android.graphics.ImageDecoder.decodeBitmap(source);
                } else {
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                }
                
                if (bitmap == null) {
                    runOnUiThread(() -> {
                        processingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Save to temp file
                File photoFile = new File(getCacheDir(), "signature_gallery_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(photoFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();
                bitmap.recycle();

                runOnUiThread(() -> {
                    processingOverlay.setVisibility(View.GONE);
                    
                    // Navigate to crop activity
                    Intent intent = new Intent(this, CropSignatureActivity.class);
                    intent.putExtra(CropSignatureActivity.EXTRA_IMAGE_PATH, photoFile.getAbsolutePath());
                    cropLauncher.launch(intent);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    processingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading image: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
