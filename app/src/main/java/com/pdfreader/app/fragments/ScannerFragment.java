package com.pdfreader.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
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
    private ExecutorService analysisExecutor;

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

        // Ensure views are not null
        if (cameraPreview == null || flashToggle == null || closeScanner == null) {
            throw new IllegalStateException("Required views not found in layout");
        }

        cameraPreview.setImplementationMode(ImplementationMode.COMPATIBLE);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);

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
            Intent intent = new Intent(getActivity(), EditPdfActivity.class);
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

        // Snapshot corners at the moment of capture (analyzer keeps updating on background thread)
        final float[] captureCorners = (documentDetected && lastDocCorners != null)
                ? lastDocCorners.clone() : null;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "SCAN_" + timestamp + ".jpg";

        File photoFile = new File(requireContext().getCacheDir(), fileName);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        new Thread(() -> {
                            Bitmap page = cropToDocument(photoFile, captureCorners);
                            if (page != null) page.recycle();
                            mainHandler.post(() -> {
                                capturedImages.add(photoFile);
                                capturedPaths.add(photoFile.getAbsolutePath());
                                int idx = capturedPaths.size() - 1;
                                filmstripAdapter.notifyItemInserted(idx);
                                filmstripRecycler.scrollToPosition(idx);
                                updateCapturedImagesUI();
                            });
                        }).start();
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
    private Bitmap cropToDocument(File file, float[] previewCorners) {
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

            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            src = applyExifRotation(src, orientation);

            float[] corners = DocumentAnalyzer.detect(src);
            if (corners == null) corners = previewCorners;

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
            improveContrast(page);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                page.compress(Bitmap.CompressFormat.JPEG, 95, fos);
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

    private static void improveContrast(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int[] hist = new int[256];
        int samples = 0;
        int step = Math.max(1, px.length / 24000);
        for (int i = 0; i < px.length; i += step) {
            hist[lumaOf(px[i])]++;
            samples++;
        }
        int p2 = histogramPercentile(hist, samples, 0.02f);
        int p98 = histogramPercentile(hist, samples, 0.98f);
        int range = p98 - p2;
        if (range < 40 || range > 170) return;

        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int y = lumaOf(c);
            int ny = clampChannel((y - p2) * 255 / range);
            int delta = ny - y;
            int r = clampChannel(((c >> 16) & 0xFF) + delta);
            int g = clampChannel(((c >> 8) & 0xFF) + delta);
            int b = clampChannel((c & 0xFF) + delta);
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        src.setPixels(px, 0, w, 0, 0, w, h);
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

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Bitmap applyExifRotation(Bitmap bmp, int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  m.setRotate(90);  break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(270); break;
            default: return bmp;
        }
        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        bmp.recycle();
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
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
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
