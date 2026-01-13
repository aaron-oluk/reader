package com.pdfreader.app;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImageToPdfActivity extends AppCompatActivity {

    private static final int PICK_IMAGES_REQUEST = 100;
    private LinearLayout imagesContainer;
    private Button btnSelectImages;
    private Button btnCreatePdf;
    private ProgressBar progressBar;
    private TextView statusText;
    private List<Uri> selectedImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_to_pdf);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.image_to_pdf);
        }

        imagesContainer = findViewById(R.id.imagesContainer);
        btnSelectImages = findViewById(R.id.btnSelectImages);
        btnCreatePdf = findViewById(R.id.btnCreatePdf);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        selectedImages = new ArrayList<>();

        btnSelectImages.setOnClickListener(v -> openImagePicker());
        btnCreatePdf.setOnClickListener(v -> createPdf());

        btnCreatePdf.setEnabled(false);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_IMAGES_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImages.clear();
            imagesContainer.removeAllViews();

            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    selectedImages.add(uri);
                    addImagePreview(uri);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                selectedImages.add(uri);
                addImagePreview(uri);
            }

            statusText.setText(selectedImages.size() + " images selected");
            statusText.setVisibility(View.VISIBLE);
            btnCreatePdf.setEnabled(!selectedImages.isEmpty());
        }
    }

    private void addImagePreview(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap != null) {
                // Scale for preview
                int maxWidth = getResources().getDisplayMetrics().widthPixels - 64;
                float scale = (float) maxWidth / bitmap.getWidth();
                int previewHeight = (int) (bitmap.getHeight() * scale);

                ImageView imageView = new ImageView(this);
                imageView.setImageBitmap(bitmap);
                imageView.setAdjustViewBounds(true);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 0, 16);
                imageView.setLayoutParams(params);

                imagesContainer.addView(imageView);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createPdf() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please select images", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnCreatePdf.setEnabled(false);
        statusText.setText("Creating PDF...");

        new Thread(() -> {
            try {
                PdfDocument document = new PdfDocument();

                for (int i = 0; i < selectedImages.size(); i++) {
                    Uri uri = selectedImages.get(i);
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();

                    if (bitmap != null) {
                        // Create page with image dimensions
                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                bitmap.getWidth(), bitmap.getHeight(), i + 1).create();
                        PdfDocument.Page page = document.startPage(pageInfo);
                        Canvas canvas = page.getCanvas();
                        canvas.drawBitmap(bitmap, 0, 0, null);
                        document.finishPage(page);
                        bitmap.recycle();
                    }
                }

                // Save PDF
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File outputFile = new File(getExternalFilesDir(null), "images_" + timestamp + ".pdf");

                FileOutputStream fos = new FileOutputStream(outputFile);
                document.writeTo(fos);
                document.close();
                fos.close();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnCreatePdf.setEnabled(true);
                    statusText.setText("PDF created successfully!");
                    Toast.makeText(this, getString(R.string.saved_to, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnCreatePdf.setEnabled(true);
                    statusText.setText("Error creating PDF");
                    Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            }
        }).start();
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
