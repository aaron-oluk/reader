package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SignPdfActivity extends AppCompatActivity {

    private RecyclerView pagesRecyclerView;
    private Button btnSelectPdf;
    private Button btnAddSignature;
    private Button btnSave;
    private View emptyStateContainer;
    private ScrollView scrollView;

    private String pdfPath;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private List<Boolean> pagesWithSignature;
    private SignPdfPageAdapter signPdfPageAdapter;
    private Bitmap signatureBitmap;
    private int selectedPageIndex = -1;
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private ActivityResultLauncher<Intent> cameraSignatureLauncher;
    private SignatureManager signatureManager;
    private ExecutorService executorService;

    // UI elements for preview
    private MaterialCardView signaturePreviewCard;
    private ImageView signaturePreviewImage;
    private LinearLayout statusInfo;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_pdf);
        
        // Register activity result launcher for PDF picker
        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            pdfPath = uri.toString();
                            loadPdf(uri);
                        }
                    }
                });

        // Register activity result launcher for camera signature capture
        cameraSignatureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String savedPath = result.getData().getStringExtra(
                                ReviewSignatureActivity.EXTRA_SAVED_SIGNATURE_PATH);
                        if (savedPath != null) {
                            Bitmap signature = signatureManager.loadSignature(savedPath);
                            if (signature != null) {
                                signatureBitmap = signature;
                                updateSignaturePreview();
                                Toast.makeText(this, "Signature captured and saved", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.sign_document);
        }

        pagesRecyclerView = findViewById(R.id.pagesRecycler);
        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        btnAddSignature = findViewById(R.id.btnAddSignature);
        btnSave = findViewById(R.id.btnSave);
        scrollView = findViewById(R.id.scrollView);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        
        Button btnSelectPdfEmpty = findViewById(R.id.btnSelectPdfEmpty);
        if (btnSelectPdfEmpty != null) {
            btnSelectPdfEmpty.setOnClickListener(v -> openFilePicker());
        }

        // Initialize signature preview UI elements
        signaturePreviewCard = findViewById(R.id.signaturePreviewCard);
        signaturePreviewImage = findViewById(R.id.signaturePreviewImage);
        statusInfo = findViewById(R.id.statusInfo);
        statusText = findViewById(R.id.statusText);

        pagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pagesWithSignature = new ArrayList<>();
        signatureManager = new SignatureManager(this);
        executorService = Executors.newFixedThreadPool(2);

        btnSelectPdf.setOnClickListener(v -> openFilePicker());
        btnAddSignature.setOnClickListener(v -> showSignatureDialog());
        btnSave.setOnClickListener(v -> savePdf());

        btnAddSignature.setEnabled(false);
        btnSave.setEnabled(false);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPickerLauncher.launch(intent);
    }

    private void loadPdf(Uri uri) {
        executorService.execute(() -> {
            try {
                // Copy to cache in background
                File cacheFile = new File(getCacheDir(), "sign_temp.pdf");
                InputStream inputStream = getContentResolver().openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(cacheFile);
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();

                parcelFileDescriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(parcelFileDescriptor);

                int pageCount = pdfRenderer.getPageCount();
                pagesWithSignature.clear();
                for (int i = 0; i < pageCount; i++) {
                    pagesWithSignature.add(false);
                }

                runOnUiThread(() -> {
                    // Hide empty state, show PDF viewer
                    if (emptyStateContainer != null) {
                        emptyStateContainer.setVisibility(View.GONE);
                    }
                    if (scrollView != null) {
                        scrollView.setVisibility(View.VISIBLE);
                    }

                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    signPdfPageAdapter = new SignPdfPageAdapter(
                            SignPdfActivity.this, 
                            pdfRenderer, 
                            screenWidth, 
                            (pageIndex) -> {
                                if (signatureBitmap != null) {
                                    placeSignatureOnPage(pageIndex);
                                }
                            }
                    );
                    pagesRecyclerView.setAdapter(signPdfPageAdapter);

                    btnAddSignature.setEnabled(true);
                    Toast.makeText(this, "PDF loaded. Add your signature.", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            }
        });
    }

    private void showSignatureDialog() {
        // First show selector dialog with saved signatures
        showSignatureSelectorDialog();
    }
    
    private void showSignatureSelectorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature_selector, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.signatures_recycler);
        View cardDrawSignature = dialogView.findViewById(R.id.card_draw_signature);
        View cardCameraSignature = dialogView.findViewById(R.id.card_camera_signature);
        View emptyStateContainer = dialogView.findViewById(R.id.empty_state_container);
        
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        // Load saved signatures
        List<String> savedSignatures = signatureManager.getSavedSignatures();
        
        if (savedSignatures.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateContainer.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateContainer.setVisibility(View.GONE);
        }
        
        SignatureAdapter adapter = new SignatureAdapter(savedSignatures, signatureManager);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        adapter.setOnSignatureClickListener(filePath -> {
            Bitmap signature = signatureManager.loadSignature(filePath);
            if (signature != null) {
                signatureBitmap = signature;
                Toast.makeText(this, "Signature selected", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        
        adapter.setOnSignatureDeleteListener(filePath -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Signature")
                    .setMessage("Are you sure you want to delete this signature?")
                    .setPositiveButton("Delete", (d, which) -> {
                        if (signatureManager.deleteSignature(filePath)) {
                            // Refresh list
                            List<String> updated = signatureManager.getSavedSignatures();
                            adapter.updateSignatures(updated);
                            
                            // Update empty state visibility
                            if (updated.isEmpty()) {
                                recyclerView.setVisibility(View.GONE);
                                emptyStateContainer.setVisibility(View.VISIBLE);
                            }
                            
                            Toast.makeText(this, "Signature deleted", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        
        // Handle draw signature option
        cardDrawSignature.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateSignatureDialog();
        });
        
        // Handle camera signature option
        cardCameraSignature.setOnClickListener(v -> {
            dialog.dismiss();
            openCameraSignatureCapture();
        });
        
        dialog.show();
    }

    private void openCameraSignatureCapture() {
        Intent intent = new Intent(this, CaptureSignatureActivity.class);
        cameraSignatureLauncher.launch(intent);
    }

    private void updateSignaturePreview() {
        if (signatureBitmap != null && signaturePreviewCard != null) {
            signaturePreviewCard.setVisibility(View.VISIBLE);
            signaturePreviewImage.setImageBitmap(signatureBitmap);

            if (statusInfo != null) {
                statusInfo.setVisibility(View.VISIBLE);
            }
            if (statusText != null) {
                statusText.setText("Signature ready to place");
            }
        }
    }

    private void showCreateSignatureDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature, null);
        SignatureView signatureView = dialogView.findViewById(R.id.signatureView);
        Button btnClear = dialogView.findViewById(R.id.btnClear);
        Button btnSaveSignature = dialogView.findViewById(R.id.btnSaveSignature);
        Button btnDone = dialogView.findViewById(R.id.btnDone);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnClear.setOnClickListener(v -> signatureView.clear());
        
        btnSaveSignature.setOnClickListener(v -> {
            if (signatureView.hasSignature()) {
                showSaveSignatureNameDialog(signatureView.getSignatureBitmap(), dialog);
            } else {
                Toast.makeText(this, "Please draw your signature first", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnDone.setOnClickListener(v -> {
            if (signatureView.hasSignature()) {
                signatureBitmap = signatureView.getSignatureBitmap();
                Toast.makeText(this, R.string.add_signature, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please draw your signature", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
    
    private void showSaveSignatureNameDialog(Bitmap signatureBitmap, androidx.appcompat.app.AlertDialog parentDialog) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Signature");
        
        final EditText input = new EditText(this);
        input.setHint("Signature name (optional)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            String savedPath = signatureManager.saveSignature(signatureBitmap, name.isEmpty() ? null : name);
            if (savedPath != null) {
                Toast.makeText(this, "Signature saved", Toast.LENGTH_SHORT).show();
                // Also set as current signature
                this.signatureBitmap = signatureBitmap;
                parentDialog.dismiss();
            } else {
                Toast.makeText(this, "Failed to save signature", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void placeSignatureOnPage(int pageIndex) {
        if (signatureBitmap == null || signPdfPageAdapter == null) return;

        pagesWithSignature.set(pageIndex, true);
        signPdfPageAdapter.setSignature(signatureBitmap, pageIndex);
        btnSave.setEnabled(true);

        Toast.makeText(this, "Signature added to page " + (pageIndex + 1), Toast.LENGTH_SHORT).show();
    }

    private void savePdf() {
        if (pdfRenderer == null || signPdfPageAdapter == null) return;

        executorService.execute(() -> {
            try {
                PdfDocument document = new PdfDocument();
                int screenWidth = getResources().getDisplayMetrics().widthPixels - 32;

                for (int i = 0; i < pdfRenderer.getPageCount(); i++) {
                    // Render page with signature if it has one
                    synchronized (pdfRenderer) {
                        PdfRenderer.Page page = pdfRenderer.openPage(i);
                        
                        float scale = (float) screenWidth / page.getWidth();
                        int width = screenWidth;
                        int height = (int) (page.getHeight() * scale);
                        
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(0xFFFFFFFF);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        
                        // Add signature if present at the user-defined position
                        if (signPdfPageAdapter.hasSignature(i)) {
                            SignPdfPageAdapter.SignaturePosition sigPos = signPdfPageAdapter.getSignaturePosition(i);
                            if (sigPos != null && sigPos.bitmap != null && !sigPos.bitmap.isRecycled()) {
                                Canvas canvas = new Canvas(bitmap);
                                // Use the position and size set by the user
                                int sigWidth = (int) sigPos.width;
                                int sigHeight = (int) sigPos.height;
                                int x = (int) sigPos.x;
                                int y = (int) sigPos.y;

                                if (sigWidth > 0 && sigHeight > 0) {
                                    Bitmap scaledSignature = Bitmap.createScaledBitmap(sigPos.bitmap, sigWidth, sigHeight, true);
                                    canvas.drawBitmap(scaledSignature, x, y, null);
                                    scaledSignature.recycle();
                                }
                            }
                        }
                        
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                bitmap.getWidth(), bitmap.getHeight(), i + 1).create();
                        PdfDocument.Page docPage = document.startPage(pageInfo);
                        Canvas canvas = docPage.getCanvas();
                        canvas.drawBitmap(bitmap, 0, 0, null);
                        document.finishPage(docPage);
                        
                        bitmap.recycle();
                        page.close();
                    }
                }

                // Save to app directory
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File outputFile = new File(getExternalFilesDir(null), "signed_" + timestamp + ".pdf");

                FileOutputStream fos = new FileOutputStream(outputFile);
                document.writeTo(fos);
                document.close();
                fos.close();

                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.saved_to, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup
        if (signPdfPageAdapter != null) {
            signPdfPageAdapter.cleanup();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        try {
            if (pdfRenderer != null) {
                pdfRenderer.close();
            }
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Clean cache
        File cacheFile = new File(getCacheDir(), "sign_temp.pdf");
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
        
        // Recycle signature bitmap
        if (signatureBitmap != null && !signatureBitmap.isRecycled()) {
            signatureBitmap.recycle();
            signatureBitmap = null;
        }
    }
}
