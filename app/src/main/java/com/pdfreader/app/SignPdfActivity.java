package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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

    private static final int PICK_PDF_REQUEST = 100;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_pdf);

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
        startActivityForResult(intent, PICK_PDF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                pdfPath = uri.toString();
                loadPdf(uri);
            }
        }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature, null);
        SignatureView signatureView = dialogView.findViewById(R.id.signatureView);
        Button btnClear = dialogView.findViewById(R.id.btnClear);
        Button btnDone = dialogView.findViewById(R.id.btnDone);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnClear.setOnClickListener(v -> signatureView.clear());
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
