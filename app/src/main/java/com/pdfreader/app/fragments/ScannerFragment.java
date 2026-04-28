package com.pdfreader.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.view.PreviewView.ImplementationMode;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.pdfreader.app.R;
import com.pdfreader.app.ScanFilmstripAdapter;
import com.pdfreader.app.ScanReviewActivity;
import com.pdfreader.app.SignPdfActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class ScannerFragment extends Fragment {

    public static final String MODE_PAGE = "page";
    public static final String MODE_QUOTE = "quote";
    private static final String ARG_MODE = "scan_mode";

    public static ScannerFragment newInstance(String mode) {
        ScannerFragment f = new ScannerFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ARG_MODE, mode);
        f.setArguments(args);
        return f;
    }

    private PreviewView cameraPreview;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private boolean isFlashOn = false;

    private TextView scanQuoteTab;
    private TextView scanPageTab;
    private TextView signTab;
    private TextView scannerTitle;
    private View modeTabsContainer;
    private FrameLayout captureButton;
    private View flashToggle;
    private View closeScanner;
    private MaterialButton savePdfButton;
    private TextView capturedCountText;
    private View capturedImagesInfo;
    private ImageView flashIcon;
    private RecyclerView filmstripRecycler;
    private ScanFilmstripAdapter filmstripAdapter;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> reviewLauncher;

    // Store captured images
    private List<File> capturedImages = new ArrayList<>();
    private List<String> capturedPaths = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                    }
                });

        reviewLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == ScanReviewActivity.RESULT_ADD_MORE) {
                        // User tapped "Add Page" — stay on scanner, keep existing captures
                    } else if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        // Successfully saved — clear everything
                        capturedImages.clear();
                        capturedPaths.clear();
                        updateCapturedImagesUI();
                    }
                    // RESULT_CANCELED = user went back, keep captures
                });
        
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        initViews(view);
        setupClickListeners();
        updateCapturedImagesUI();

        // Each scan screen is independent — hide tabs, show only the correct mode
        String mode = getArguments() != null ? getArguments().getString(ARG_MODE, MODE_PAGE) : MODE_PAGE;
        if (modeTabsContainer != null) {
            modeTabsContainer.setVisibility(View.GONE);
        }
        if (scannerTitle != null) {
            scannerTitle.setText(MODE_QUOTE.equals(mode) ? "Scan Quote" : "Scan Document");
        }

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
        scannerTitle = view.findViewById(R.id.scanner_title);
        modeTabsContainer = view.findViewById(R.id.mode_tabs_container);
        captureButton = view.findViewById(R.id.capture_button);
        flashToggle = view.findViewById(R.id.flash_toggle);
        closeScanner = view.findViewById(R.id.close_scanner);
        savePdfButton = view.findViewById(R.id.save_pdf_button);
        capturedCountText = view.findViewById(R.id.captured_count_text);
        capturedImagesInfo = view.findViewById(R.id.captured_images_info);
        flashIcon = view.findViewById(R.id.flash_icon);
        filmstripRecycler = view.findViewById(R.id.filmstrip_recycler);

        // Ensure views are not null
        if (cameraPreview == null || flashToggle == null || closeScanner == null) {
            throw new IllegalStateException("Required views not found in layout");
        }

        cameraPreview.setImplementationMode(ImplementationMode.COMPATIBLE);

        // Filmstrip setup
        filmstripAdapter = new ScanFilmstripAdapter(capturedPaths);
        filmstripAdapter.setOnDeleteListener(position -> {
            capturedImages.remove(position);
            capturedPaths.remove(position);
            filmstripAdapter.notifyItemRemoved(position);
            filmstripAdapter.notifyItemRangeChanged(position, capturedPaths.size());
            updateCapturedImagesUI();
        });
        filmstripRecycler.setLayoutManager(
            new androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        filmstripRecycler.setAdapter(filmstripAdapter);
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
        
        savePdfButton.setOnClickListener(v -> openReviewScreen());
    }

    private void selectTab(TextView selectedTab) {
        // Reset all tabs
        scanQuoteTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        scanQuoteTab.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
        scanQuoteTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        scanPageTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        scanPageTab.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
        scanPageTab.setTypeface(null, android.graphics.Typeface.NORMAL);
        signTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        signTab.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
        signTab.setTypeface(null, android.graphics.Typeface.NORMAL);

        // Highlight selected
        selectedTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
        selectedTab.setTypeface(null, android.graphics.Typeface.BOLD);
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
            
            // Ensure PreviewView is visible
            if (cameraPreview != null) {
                cameraPreview.setVisibility(View.VISIBLE);
            }

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
        // Update flash icon tint
        if (flashIcon != null) {
            flashIcon.setColorFilter(ContextCompat.getColor(requireContext(),
                    isFlashOn ? R.color.accent_orange : R.color.text_white));
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
                        capturedImages.add(photoFile);
                        capturedPaths.add(photoFile.getAbsolutePath());
                        int idx = capturedPaths.size() - 1;
                        filmstripAdapter.notifyItemInserted(idx);
                        filmstripRecycler.scrollToPosition(idx);
                        updateCapturedImagesUI();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(getContext(), "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void updateCapturedImagesUI() {
        if (capturedCountText == null || savePdfButton == null || capturedImagesInfo == null) return;
        int count = capturedImages.size();
        if (count > 0) {
            capturedCountText.setText(count + (count == 1 ? " page captured" : " pages captured"));
            capturedImagesInfo.setVisibility(View.VISIBLE);
            savePdfButton.setEnabled(true);
        } else {
            capturedImagesInfo.setVisibility(View.GONE);
            savePdfButton.setEnabled(false);
        }
    }
    
    private void openReviewScreen() {
        if (capturedPaths.isEmpty()) {
            Toast.makeText(getContext(), "No pages to review", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), ScanReviewActivity.class);
        intent.putStringArrayListExtra(ScanReviewActivity.EXTRA_IMAGE_PATHS, new ArrayList<>(capturedPaths));
        reviewLauncher.launch(intent);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        for (File imageFile : capturedImages) {
            if (imageFile.exists()) imageFile.delete();
        }
        capturedImages.clear();
        capturedPaths.clear();
    }
}
