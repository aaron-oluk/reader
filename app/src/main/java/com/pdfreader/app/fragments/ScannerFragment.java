package com.pdfreader.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import androidx.exifinterface.media.ExifInterface;
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
import com.pdfreader.app.EditPdfActivity;
import com.pdfreader.app.WindowInsetsHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;

import com.pdfreader.app.DocumentAnalyzer;
import com.pdfreader.app.DocumentDetectorView;

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

    private DocumentDetectorView detectorView;
    private TextView instructionText;
    private TextView modeAuto;
    private TextView modeManual;
    private boolean manualMode;
    private ExecutorService analysisExecutor;
    private ExecutorService scanProcessor;
    private int processingCount;
    private boolean openReviewWhenReady;
    private final Object captureLock = new Object();

    // Last corners detected by DocumentAnalyzer, used to crop captured images
    private float[] lastDocCorners = null;
    private boolean documentDetected = false;

    // Store captured images
    private List<File> capturedImages = new ArrayList<>();
    private List<String> capturedPaths = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        analysisExecutor = Executors.newSingleThreadExecutor();
        scanProcessor = Executors.newSingleThreadExecutor();

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
        View topBar = view.findViewById(R.id.top_bar);
        if (topBar != null) {
            WindowInsetsHelper.applyStatusBarPadding(topBar);
        }
        View bottomControls = view.findViewById(R.id.bottom_controls);
        if (bottomControls != null) {
            WindowInsetsHelper.applyNavigationBarPadding(bottomControls);
        }
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
        detectorView = view.findViewById(R.id.document_detector_view);
        instructionText = view.findViewById(R.id.instruction_text);
        modeAuto = view.findViewById(R.id.mode_auto);
        modeManual = view.findViewById(R.id.mode_manual);

        // Ensure views are not null
        if (cameraPreview == null || flashToggle == null || closeScanner == null) {
            throw new IllegalStateException("Required views not found in layout");
        }

        cameraPreview.setImplementationMode(ImplementationMode.COMPATIBLE);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        // Filmstrip setup
        filmstripAdapter = new ScanFilmstripAdapter(capturedPaths);
        filmstripAdapter.setOnDeleteListener(position -> {
            File removed = capturedImages.remove(position);
            String removedPath;
            synchronized (captureLock) {
                removedPath = capturedPaths.remove(position);
            }
            if (removed != null && removed.exists()) removed.delete();
            if (removedPath != null) filmstripAdapter.setProcessing(removedPath, false);
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
            Intent intent = new Intent(getActivity(), EditPdfActivity.class);
            startActivity(intent);
        });

        scanQuoteTab.setOnClickListener(v -> selectTab(scanQuoteTab));
        scanPageTab.setOnClickListener(v -> selectTab(scanPageTab));
        
        savePdfButton.setOnClickListener(v -> openReviewScreen());

        if (modeAuto != null) modeAuto.setOnClickListener(v -> setManualMode(false));
        if (modeManual != null) modeManual.setOnClickListener(v -> setManualMode(true));
        if (detectorView != null) {
            detectorView.setOnCornersChangedListener(corners -> {
                lastDocCorners = corners.clone();
                documentDetected = true;
            });
        }
        updateModeChips();
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

    private int viewportBindAttempts;

    private void bindCameraUseCases() {
        if (cameraProvider == null || cameraPreview == null || !isAdded()) {
            return;
        }

        try {
            // Preview, capture, and analysis share one crop so the outline matches the photo.
            int rotation = cameraPreview.getDisplay() != null
                    ? cameraPreview.getDisplay().getRotation()
                    : Surface.ROTATION_0;

            Preview preview = new Preview.Builder()
                    .setTargetRotation(rotation)
                    .build();
            preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(95)
                    .setTargetRotation(rotation)
                    .build();

            if (analysisExecutor == null || analysisExecutor.isShutdown()) {
                analysisExecutor = Executors.newSingleThreadExecutor();
            }

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageRotationEnabled(true)
                    .setTargetRotation(rotation)
                    .build();
            imageAnalysis.setAnalyzer(analysisExecutor,
                    new DocumentAnalyzer((corners, detected) -> {
                        if (manualMode) return;
                        lastDocCorners = corners;
                        documentDetected = detected;
                        if (detectorView != null) detectorView.setCorners(corners, detected);
                        if (instructionText != null) {
                            instructionText.setText(detected
                                    ? "Document detected — tap to capture"
                                    : "Align the edges of the page");
                        }
                    }, new Handler(Looper.getMainLooper())));

            // Select back camera, fallback to front if back is not available
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            try {
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                }
            } catch (Exception e) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            }

            cameraProvider.unbindAll();

            ViewPort viewPort = cameraPreview.getViewPort();
            if (viewPort == null && viewportBindAttempts < 8) {
                viewportBindAttempts++;
                cameraPreview.post(this::bindCameraUseCases);
                return;
            }
            viewportBindAttempts = 0;
            if (viewPort != null) {
                UseCaseGroup group = new UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                        .addUseCase(imageCapture)
                        .addUseCase(imageAnalysis)
                        .build();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, group);
            } else {
                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview, imageCapture, imageAnalysis);
            }
            
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

    private void setManualMode(boolean manual) {
        manualMode = manual;
        if (detectorView != null) {
            detectorView.setInteractive(false);
            detectorView.setVisibility(manual ? View.GONE : View.VISIBLE);
        }
        if (instructionText != null) {
            instructionText.setText(manual
                    ? "Fit the page in the frame, then scan"
                    : "Align the edges of the page");
        }
        updateModeChips();
    }

    private void updateModeChips() {
        styleModeChip(modeAuto, !manualMode);
        styleModeChip(modeManual, manualMode);
    }

    private void styleModeChip(TextView chip, boolean selected) {
        if (chip == null || !isAdded()) return;
        if (selected) {
            chip.setBackgroundResource(R.drawable.tab_selected_modern);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
        } else {
            chip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            chip.setTextColor(0xB3FFFFFF);
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

        // Manual keeps the framed photo. Auto uses the outline only as a fallback
        // if detection on the saved bitmap finds nothing.
        final boolean manual = manualMode;
        final float[] captureCorners = (!manual && documentDetected && lastDocCorners != null)
                ? lastDocCorners.clone() : null;

        final int displayRotation = cameraPreview.getDisplay() != null
                ? cameraPreview.getDisplay().getRotation()
                : Surface.ROTATION_0;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "SCAN_" + timestamp + ".jpg";

        File photoFile = new File(requireContext().getCacheDir(), fileName);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        if (!isAdded() || filmstripAdapter == null) return;
                        final String path = photoFile.getAbsolutePath();
                        capturedImages.add(photoFile);
                        synchronized (captureLock) {
                            capturedPaths.add(path);
                        }
                        filmstripAdapter.setProcessing(path, true);
                        int idx = capturedPaths.size() - 1;
                        filmstripAdapter.notifyItemInserted(idx);
                        filmstripRecycler.scrollToPosition(idx);
                        updateCapturedImagesUI();
                        processingCount++;

                        ExecutorService processor = scanProcessor;
                        if (processor == null || processor.isShutdown()) {
                            finishPageProcessing(path);
                            return;
                        }
                        processor.execute(() -> {
                            boolean kept;
                            synchronized (captureLock) {
                                kept = capturedPaths.contains(path);
                            }
                            Bitmap page = kept
                                    ? cropToDocument(photoFile, captureCorners, displayRotation, manual)
                                    : null;
                            if (page != null) page.recycle();
                            synchronized (captureLock) {
                                kept = capturedPaths.contains(path);
                            }
                            if (!kept && photoFile.exists()) photoFile.delete();
                            new Handler(Looper.getMainLooper()).post(() -> finishPageProcessing(path));
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(getContext(), "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Straightens the captured photo to the page itself. Edges are measured on
     * this bitmap, not the live preview, so the saved scan has no surrounding
     * background and no added border.
     */
    private Bitmap cropToDocument(File file, float[] previewCorners, int displayRotation, boolean manualCorners) {
        Bitmap src = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
            while (longEdge / sample > 2500) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            src = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (src == null) return null;

            int exifOrientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                exifOrientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (Exception ignored) {
            }
            src = orientToCapture(src, exifOrientation, displayRotation);

            float[] corners = null;
            if (!manualCorners) {
                corners = DocumentAnalyzer.detect(src);
                if (corners == null) corners = previewCorners;
            }

            Bitmap page = src;
            if (corners != null && !fillsFrame(corners)) {
                Bitmap warped = warpToPage(src, corners);
                if (warped != null) {
                    src.recycle();
                    src = null;
                    page = warped;
                }
            }
            Bitmap trimmed = trimDarkEdges(page);
            if (trimmed != page) {
                page.recycle();
                page = trimmed;
            }
            preprocessDocument(page);

            // Write beside the original so the filmstrip can keep showing the
            // shot that was just taken until this page is ready.
            File ready = new File(file.getParentFile(), file.getName() + ".ready");
            try (FileOutputStream fos = new FileOutputStream(ready)) {
                page.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            }
            try {
                ExifInterface saved = new ExifInterface(ready.getAbsolutePath());
                saved.setAttribute(ExifInterface.TAG_ORIENTATION,
                        String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                saved.saveAttributes();
            } catch (Exception ignored) {
            }
            if (file.exists() && !file.delete()) {
                ready.delete();
                return page;
            }
            if (!ready.renameTo(file)) {
                ready.delete();
            }
            return page;
        } catch (Exception e) {
            e.printStackTrace();
            if (src != null) src.recycle();
            return null;
        }
    }

    private static boolean fillsFrame(float[] corners) {
        float minX = 1f, minY = 1f, maxX = 0f, maxY = 0f;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, corners[i * 2]);
            maxX = Math.max(maxX, corners[i * 2]);
            minY = Math.min(minY, corners[i * 2 + 1]);
            maxY = Math.max(maxY, corners[i * 2 + 1]);
        }
        return minX <= 0.015f && minY <= 0.015f && maxX >= 0.985f && maxY >= 0.985f;
    }

    private static Bitmap warpToPage(Bitmap src, float[] corners) {
        int imgW = src.getWidth();
        int imgH = src.getHeight();
        float tlX = corners[0] * imgW, tlY = corners[1] * imgH;
        float trX = corners[2] * imgW, trY = corners[3] * imgH;
        float brX = corners[4] * imgW, brY = corners[5] * imgH;
        float blX = corners[6] * imgW, blY = corners[7] * imgH;

        float topW = dist(tlX, tlY, trX, trY);
        float botW = dist(blX, blY, brX, brY);
        float leftH = dist(tlX, tlY, blX, blY);
        float rightH = dist(trX, trY, brX, brY);
        int outW = Math.round((topW + botW) / 2f);
        int outH = Math.round((leftH + rightH) / 2f);
        if (outW < 10 || outH < 10) return null;

        int maxEdge = 2500;
        float scale = Math.min(1f, maxEdge / (float) Math.max(outW, outH));
        outW = Math.max(1, Math.round(outW * scale));
        outH = Math.max(1, Math.round(outH * scale));

        float[] srcPts = {tlX, tlY, trX, trY, brX, brY, blX, blY};
        float[] dstPts = {0, 0, outW, 0, outW, outH, 0, outH};
        Matrix matrix = new Matrix();
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) return null;

        Bitmap output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(src, matrix, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return output;
    }

    /** Drops the black fringe the perspective warp leaves outside the page. */
    private static Bitmap trimDarkEdges(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int maxTrimX = Math.max(1, w / 25);
        int maxTrimY = Math.max(1, h / 25);
        int top = 0;
        int bottom = h - 1;
        int left = 0;
        int right = w - 1;
        while (top < bottom && top < maxTrimY && rowMostlyDark(px, w, top)) top++;
        while (bottom > top && (h - 1 - bottom) < maxTrimY && rowMostlyDark(px, w, bottom)) bottom--;
        while (left < right && left < maxTrimX && colMostlyDark(px, w, left, top, bottom)) left++;
        while (right > left && (w - 1 - right) < maxTrimX && colMostlyDark(px, w, right, top, bottom)) right--;
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return src;
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1);
    }

    private static boolean rowMostlyDark(int[] px, int w, int y) {
        int dark = 0;
        int row = y * w;
        for (int x = 0; x < w; x++) {
            if (lumaOf(px[row + x]) < 22) dark++;
        }
        return dark > w * 0.92f;
    }

    private static boolean colMostlyDark(int[] px, int w, int x, int top, int bottom) {
        int dark = 0;
        int n = bottom - top + 1;
        for (int y = top; y <= bottom; y++) {
            if (lumaOf(px[y * w + x]) < 22) dark++;
        }
        return dark > n * 0.92f;
    }

    /**
     * A light cleanup only: a little shadow lift, a small paper-color correction,
     * a gentle contrast nudge, and a light edge crisp. Stronger passes wash the page out.
     */
    private static void preprocessDocument(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        flattenIllumination(px, w, h);
        neutralizePaper(px);
        stretchInkAndPaper(px);
        sharpenText(px, w, h);
        src.setPixels(px, 0, w, 0, 0, w, h);
    }

    /** Divides out a coarse lighting map so a shadow across the page does not hide text. */
    private static void flattenIllumination(int[] px, int w, int h) {
        int gw = Math.max(8, w / 32);
        int gh = Math.max(8, h / 32);
        long[] sum = new long[gw * gh];
        int[] count = new int[gw * gh];
        for (int y = 0; y < h; y++) {
            int gy = Math.min(gh - 1, y * gh / h);
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int gx = Math.min(gw - 1, x * gw / w);
                int cell = gy * gw + gx;
                sum[cell] += lumaOf(px[row + x]);
                count[cell]++;
            }
        }
        float[] grid = new float[gw * gh];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = count[i] == 0 ? 180f : sum[i] / (float) count[i];
        }
        float[] smooth = blurGrid(grid, gw, gh);
        float paper = percentile(smooth, 0.72f);
        paper = Math.max(paper, 40f);

        for (int y = 0; y < h; y++) {
            float gy = (y + 0.5f) * gh / h - 0.5f;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float gx = (x + 0.5f) * gw / w - 0.5f;
                float illum = Math.max(18f, sampleGrid(smooth, gw, gh, gx, gy));
                float gain = clampFloat(paper / illum, 0.90f, 1.28f);
                float applied = 1f + (gain - 1f) * 0.45f;
                px[row + x] = scaleRgb(px[row + x], applied);
            }
        }
    }

    /** Shifts the brightest region toward neutral white without washing out colored ink. */
    private static void neutralizePaper(int[] px) {
        int[] hist = lumaHistogram(px, Math.max(1, px.length / 20000));
        int samples = 0;
        for (int bin : hist) samples += bin;
        int paperCut = histogramPercentile(hist, samples, 0.82f);
        long rSum = 0, gSum = 0, bSum = 0, n = 0;
        int step = Math.max(1, px.length / 20000);
        for (int i = 0; i < px.length; i += step) {
            int c = px[i];
            if (lumaOf(c) < paperCut) continue;
            rSum += (c >> 16) & 0xFF;
            gSum += (c >> 8) & 0xFF;
            bSum += c & 0xFF;
            n++;
        }
        if (n < 8) return;
        float r = rSum / (float) n;
        float g = gSum / (float) n;
        float b = bSum / (float) n;
        float peak = Math.max(r, Math.max(g, b));
        if (peak < 8f) return;
        float gainR = 1f + (clampFloat(peak / r, 0.94f, 1.08f) - 1f) * 0.5f;
        float gainG = 1f + (clampFloat(peak / g, 0.94f, 1.08f) - 1f) * 0.5f;
        float gainB = 1f + (clampFloat(peak / b, 0.94f, 1.08f) - 1f) * 0.5f;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int nr = clampChannel(Math.round(((c >> 16) & 0xFF) * gainR));
            int ng = clampChannel(Math.round(((c >> 8) & 0xFF) * gainG));
            int nb = clampChannel(Math.round((c & 0xFF) * gainB));
            px[i] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
        }
    }

    /** Maps the page background toward white and the ink toward black. */
    private static void stretchInkAndPaper(int[] px) {
        int step = Math.max(1, px.length / 24000);
        int[] hist = lumaHistogram(px, step);
        int samples = 0;
        for (int i = 0; i < px.length; i += step) samples++;
        int black = histogramPercentile(hist, samples, 0.06f);
        int white = histogramPercentile(hist, samples, 0.92f);
        int range = white - black;
        if (range < 24) return;
        float strength = range > 160 ? 0.16f : 0.32f;

        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int y = lumaOf(c);
            float t = clampFloat((y - black) / (float) range, 0f, 1f);
            int curved = Math.round(t * 255f);
            int ny = clampChannel(Math.round(y + (curved - y) * strength));
            int delta = ny - y;
            int nr = clampChannel(((c >> 16) & 0xFF) + delta);
            int ng = clampChannel(((c >> 8) & 0xFF) + delta);
            int nb = clampChannel((c & 0xFF) + delta);
            px[i] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
        }
    }

    /** Adds a light unsharp mask so letter edges read more cleanly. */
    private static void sharpenText(int[] px, int w, int h) {
        int n = w * h;
        int[] luma = new int[n];
        for (int i = 0; i < n; i++) luma[i] = lumaOf(px[i]);
        int[] blurred = boxBlur(luma, w, h);
        for (int i = 0; i < n; i++) {
            int delta = Math.round((luma[i] - blurred[i]) * 0.36f);
            if (delta == 0) continue;
            int c = px[i];
            int nr = clampChannel(((c >> 16) & 0xFF) + delta);
            int ng = clampChannel(((c >> 8) & 0xFF) + delta);
            int nb = clampChannel((c & 0xFF) + delta);
            px[i] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
        }
    }

    private static int[] boxBlur(int[] src, int w, int h) {
        int[] horizontal = new int[src.length];
        int[] out = new int[src.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - 1);
                int x1 = Math.min(w - 1, x + 1);
                horizontal[row + x] = (src[row + x0] + src[row + x] + src[row + x1]) / 3;
            }
        }
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - 1);
            int y1 = Math.min(h - 1, y + 1);
            for (int x = 0; x < w; x++) {
                out[y * w + x] = (horizontal[y0 * w + x] + horizontal[y * w + x] + horizontal[y1 * w + x]) / 3;
            }
        }
        return out;
    }

    private static float[] blurGrid(float[] grid, int w, int h) {
        float[] out = new float[grid.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0f;
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        s += grid[yy * w + xx];
                        n++;
                    }
                }
                out[y * w + x] = s / n;
            }
        }
        return out;
    }

    private static float sampleGrid(float[] grid, int w, int h, float x, float y) {
        int x0 = clamp((int) Math.floor(x), 0, w - 1);
        int y0 = clamp((int) Math.floor(y), 0, h - 1);
        int x1 = Math.min(w - 1, x0 + 1);
        int y1 = Math.min(h - 1, y0 + 1);
        float tx = clampFloat(x - x0, 0f, 1f);
        float ty = clampFloat(y - y0, 0f, 1f);
        float top = grid[y0 * w + x0] * (1f - tx) + grid[y0 * w + x1] * tx;
        float bot = grid[y1 * w + x0] * (1f - tx) + grid[y1 * w + x1] * tx;
        return top * (1f - ty) + bot * ty;
    }

    private static int[] lumaHistogram(int[] px, int step) {
        int[] hist = new int[256];
        for (int i = 0; i < px.length; i += step) {
            hist[lumaOf(px[i])]++;
        }
        return hist;
    }

    private static float percentile(float[] values, float p) {
        float[] copy = java.util.Arrays.copyOf(values, values.length);
        java.util.Arrays.sort(copy);
        int index = Math.min(copy.length - 1, Math.max(0, Math.round(p * (copy.length - 1))));
        return copy[index];
    }

    private static int scaleRgb(int color, float gain) {
        int r = clampChannel(Math.round(((color >> 16) & 0xFF) * gain));
        int g = clampChannel(Math.round(((color >> 8) & 0xFF) * gain));
        int b = clampChannel(Math.round((color & 0xFF) * gain));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int histogramPercentile(int[] hist, int samples, float p) {
        int target = Math.round(samples * p);
        int seen = 0;
        for (int i = 0; i < hist.length; i++) {
            seen += hist[i];
            if (seen >= target) return i;
        }
        return 255;
    }

    private static int lumaOf(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 54 + g * 183 + b * 19) >> 8;
    }

    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampFloat(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Keeps the scan in the orientation of the capture. A portrait shot stays
     * portrait; a contradictory rotation tag is ignored instead of turning the page.
     */
    private static Bitmap orientToCapture(Bitmap src, int exifOrientation, int displayRotation) {
        boolean wantPortrait = displayRotation == Surface.ROTATION_0
                || displayRotation == Surface.ROTATION_180;
        boolean srcPortrait = src.getHeight() >= src.getWidth();
        int degrees = exifDegrees(exifOrientation);
        boolean turnsSideways = degrees == 90 || degrees == 270;
        boolean exifPortrait = turnsSideways ? !srcPortrait : srcPortrait;

        if (degrees != 0 && exifPortrait == wantPortrait) {
            return rotateBitmap(src, degrees);
        }
        if (srcPortrait == wantPortrait) {
            return src;
        }
        int turn = wantPortrait
                ? (displayRotation == Surface.ROTATION_180 ? 270 : 90)
                : (displayRotation == Surface.ROTATION_270 ? 270 : 90);
        return rotateBitmap(src, turn);
    }

    private static int exifDegrees(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
            case ExifInterface.ORIENTATION_TRANSPOSE:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
            case ExifInterface.ORIENTATION_TRANSVERSE:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap rotateBitmap(Bitmap bmp, int degrees) {
        if (degrees == 0) return bmp;
        Matrix m = new Matrix();
        m.setRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        if (rotated != bmp) bmp.recycle();
        return rotated;
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
    
    private void finishPageProcessing(String path) {
        if (filmstripAdapter != null) {
            filmstripAdapter.setProcessing(path, false);
            int index;
            synchronized (captureLock) {
                index = capturedPaths.indexOf(path);
            }
            if (index >= 0) filmstripAdapter.notifyItemChanged(index);
        }
        processingCount = Math.max(0, processingCount - 1);
        if (openReviewWhenReady && processingCount == 0) {
            openReviewWhenReady = false;
            openReviewScreen();
        }
    }

    private void openReviewScreen() {
        if (capturedPaths.isEmpty()) {
            Toast.makeText(getContext(), "No pages to review", Toast.LENGTH_SHORT).show();
            return;
        }
        if (processingCount > 0) {
            openReviewWhenReady = true;
            Toast.makeText(getContext(), "Finishing this page, then opening the preview",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> paths;
        synchronized (captureLock) {
            paths = new ArrayList<>(capturedPaths);
        }
        Intent intent = new Intent(requireContext(), ScanReviewActivity.class);
        intent.putStringArrayListExtra(ScanReviewActivity.EXTRA_IMAGE_PATHS, paths);
        reviewLauncher.launch(intent);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scanProcessor != null) {
            scanProcessor.shutdownNow();
            scanProcessor = null;
        }
        for (File imageFile : capturedImages) {
            if (imageFile.exists()) imageFile.delete();
        }
        capturedImages.clear();
        capturedPaths.clear();
    }
}
