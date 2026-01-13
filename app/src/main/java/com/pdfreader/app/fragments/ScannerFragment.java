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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
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

    private static final int CAMERA_PERMISSION_CODE = 100;

    private PreviewView cameraPreview;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private boolean isFlashOn = false;

    private TextView scanQuoteTab;
    private TextView scanPageTab;
    private TextView signTab;
    private FrameLayout captureButton;
    private ImageView flashToggle;
    private ImageView closeScanner;

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
    }

    private void setupClickListeners() {
        captureButton.setOnClickListener(v -> captureImage());

        flashToggle.setOnClickListener(v -> toggleFlash());

        closeScanner.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
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
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(getContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(getContext(), "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        // Image capture
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();

        // Select back camera
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
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
            Toast.makeText(getContext(), "Failed to bind camera", Toast.LENGTH_SHORT).show();
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
