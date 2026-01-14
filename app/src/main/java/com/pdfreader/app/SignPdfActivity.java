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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private LinearLayout pagesContainer;
    private Button btnSelectPdf;
    private Button btnAddSignature;
    private Button btnSave;
    private ScrollView scrollView;

    private String pdfPath;
    private List<Bitmap> pageBitmaps;
    private List<ImageView> pageViews;
    private Bitmap signatureBitmap;
    private int selectedPageIndex = -1;
    private float signatureX = 0;
    private float signatureY = 0;
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private SignatureManager signatureManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_pdf);
        
        // Register activity result launcher
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

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.sign_document);
        }

        pagesContainer = findViewById(R.id.pagesContainer);
        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        btnAddSignature = findViewById(R.id.btnAddSignature);
        btnSave = findViewById(R.id.btnSave);
        scrollView = findViewById(R.id.scrollView);

        pageBitmaps = new ArrayList<>();
        pageViews = new ArrayList<>();
        signatureManager = new SignatureManager(this);

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
        try {
            // Copy to cache
            File cacheFile = new File(getCacheDir(), "sign_temp.pdf");
            InputStream inputStream = getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();

            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(pfd);

            pageBitmaps.clear();
            pageViews.clear();
            pagesContainer.removeAllViews();

            int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;

            for (int i = 0; i < renderer.getPageCount(); i++) {
                PdfRenderer.Page page = renderer.openPage(i);

                float scale = (float) screenWidth / page.getWidth();
                int width = screenWidth;
                int height = (int) (page.getHeight() * scale);

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFFFFFFFF);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                // Make bitmap mutable for drawing signature
                Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                pageBitmaps.add(mutableBitmap);

                ImageView imageView = new ImageView(this);
                imageView.setImageBitmap(mutableBitmap);
                imageView.setAdjustViewBounds(true);

                final int pageIndex = i;
                imageView.setOnClickListener(v -> {
                    if (signatureBitmap != null) {
                        placeSignatureOnPage(pageIndex, v);
                    }
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 0, 16);
                imageView.setLayoutParams(params);

                pagesContainer.addView(imageView);
                pageViews.add(imageView);
            }

            renderer.close();
            pfd.close();

            btnAddSignature.setEnabled(true);
            Toast.makeText(this, "PDF loaded. Add your signature.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void showSignatureDialog() {
        // First show selector dialog with saved signatures
        showSignatureSelectorDialog();
    }
    
    private void showSignatureSelectorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature_selector, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.signatures_recycler);
        Button btnCreateNew = dialogView.findViewById(R.id.btn_create_new);
        
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        // Load saved signatures
        List<String> savedSignatures = signatureManager.getSavedSignatures();
        TextView emptyState = dialogView.findViewById(R.id.empty_state_text);
        
        if (savedSignatures.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
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
                                emptyState.setVisibility(View.VISIBLE);
                            }
                            
                            Toast.makeText(this, "Signature deleted", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        
        btnCreateNew.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateSignatureDialog();
        });
        
        dialog.show();
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

    private void placeSignatureOnPage(int pageIndex, View view) {
        if (signatureBitmap == null || pageIndex >= pageBitmaps.size()) return;

        Bitmap pageBitmap = pageBitmaps.get(pageIndex);
        Canvas canvas = new Canvas(pageBitmap);

        // Scale signature to reasonable size
        int sigWidth = pageBitmap.getWidth() / 4;
        int sigHeight = (int) ((float) signatureBitmap.getHeight() / signatureBitmap.getWidth() * sigWidth);
        Bitmap scaledSignature = Bitmap.createScaledBitmap(signatureBitmap, sigWidth, sigHeight, true);

        // Place at bottom right of page
        int x = pageBitmap.getWidth() - sigWidth - 50;
        int y = pageBitmap.getHeight() - sigHeight - 100;

        canvas.drawBitmap(scaledSignature, x, y, null);

        pageViews.get(pageIndex).setImageBitmap(pageBitmap);
        btnSave.setEnabled(true);

        Toast.makeText(this, "Signature added to page " + (pageIndex + 1), Toast.LENGTH_SHORT).show();
    }

    private void savePdf() {
        try {
            PdfDocument document = new PdfDocument();

            for (int i = 0; i < pageBitmaps.size(); i++) {
                Bitmap bitmap = pageBitmaps.get(i);
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        bitmap.getWidth(), bitmap.getHeight(), i + 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawBitmap(bitmap, 0, 0, null);
                document.finishPage(page);
            }

            // Save to Downloads
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File outputFile = new File(getExternalFilesDir(null), "signed_" + timestamp + ".pdf");

            FileOutputStream fos = new FileOutputStream(outputFile);
            document.writeTo(fos);
            document.close();
            fos.close();

            Toast.makeText(this, getString(R.string.saved_to, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
