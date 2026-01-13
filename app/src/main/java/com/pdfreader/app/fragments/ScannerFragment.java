package com.pdfreader.app.fragments;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.pdfreader.app.R;
import com.pdfreader.app.SignPdfActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class ScannerFragment extends Fragment {

    private PreviewView cameraPreview;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private boolean isFlashOn = false;

    private TextView scanQuoteTab;
    private TextView scanPageTab;
    private TextView signTab;
    private FrameLayout captureButton;
    private MaterialButton flashToggle;
    private MaterialButton closeScanner;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        initViews(view);
        setupClickListeners();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        return view;
    }

    private void initViews(View view) {
        cameraPreview = view.findViewById(R.id.camera_preview);
        scanQuoteTab = view.findViewById(R.id.scan_quote_tab);
        scanPageTab = view.findViewById(R.id.scan_page_tab);
        signTab = view.findViewById(R.id.sign_tab);
        captureButton = view.findViewById(R.id.capture_button);
        flashToggle = view.findViewById(R.id.flash_toggle);
        closeScanner = view.findViewById(R.id.close_scanner);
        
        // Ensure views are not null
        if (cameraPreview == null || flashToggle == null || closeScanner == null) {
            throw new IllegalStateException("Required views not found in layout");
        }
    }

    private void setupClickListeners() {
        captureButton.setOnClickListener(v -> captureImage());

        flashToggle.setOnClickListener(v -> toggleFlash());

        closeScanner.setOnClickListener(v -> {
            if (getActivity() != null) {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        signTab.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SignPdfActivity.class);
            startActivity(intent);
        });

        scanQuoteTab.setOnClickListener(v -> selectTab(scanQuoteTab));
        scanPageTab.setOnClickListener(v -> selectTab(scanPageTab));
    }

    private void selectTab(TextView selectedTab) {
        // Reset all tabs
        scanQuoteTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white));
        scanQuoteTab.setBackground(null);
        scanPageTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white));
        scanPageTab.setBackground(null);
        signTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_white));
        signTab.setBackground(null);

        // Highlight selected
        selectedTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
        selectedTab.setBackgroundResource(R.drawable.tab_selected_modern);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        // Ensure view is attached before starting camera
        if (cameraPreview == null || !isAdded()) {
            return;
        }
        
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                if (!isAdded() || cameraPreview == null) {
                    return; // Fragment detached or view destroyed
                }
                cameraProvider = cameraProviderFuture.get();
                if (cameraProvider != null) {
                    bindCameraUseCases();
                } else {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Camera provider is null", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (ExecutionException e) {
                if (isAdded()) {
                    String errorMsg = "Error starting camera: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } catch (InterruptedException e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Camera initialization interrupted", Toast.LENGTH_SHORT).show();
                }
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Unexpected error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || cameraPreview == null || !isAdded()) {
            return;
        }

        try {
            // Preview
            Preview preview = new Preview.Builder()
                    .build();
            preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

            // Image capture
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build();

            // Select back camera, fallback to front if back is not available
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            try {
                // Test if back camera is available
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    // Fallback to front camera if back camera is not available
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                }
            } catch (Exception e) {
                // If check fails, try front camera as fallback
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            }

            // Unbind all use cases before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    cameraSelector,
                    preview,
                    imageCapture
            );

        } catch (Exception e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Failed to bind camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    }

    private void toggleFlash() {
        isFlashOn = !isFlashOn;
        if (imageCapture != null) {
            imageCapture.setFlashMode(isFlashOn ?
                    ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
        }
        Toast.makeText(getContext(), isFlashOn ? "Flash On" : "Flash Off", Toast.LENGTH_SHORT).show();
    }

    private void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(getContext(), "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "SCAN_" + timestamp + ".jpg";

        File photoFile = new File(requireContext().getCacheDir(), fileName);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        // Convert to PDF
                        convertToPdf(photoFile, timestamp);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(getContext(), "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void convertToPdf(File imageFile, String timestamp) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            if (bitmap == null) {
                Toast.makeText(getContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
                return;
            }

            PdfDocument document = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    bitmap.getWidth(), bitmap.getHeight(), 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            page.getCanvas().drawBitmap(bitmap, 0, 0, null);
            document.finishPage(page);

            // Save to Downloads
            String pdfFileName = "Scanned_" + timestamp + ".pdf";

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = requireContext().getContentResolver().insert(
                    MediaStore.Files.getContentUri("external"), values);

            if (uri != null) {
                OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    document.writeTo(outputStream);
                    outputStream.close();
                }
            }

            document.close();
            bitmap.recycle();
            imageFile.delete();

            Toast.makeText(getContext(), "PDF saved to Downloads: " + pdfFileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error creating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
