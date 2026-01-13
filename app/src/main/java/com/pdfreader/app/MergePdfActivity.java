package com.pdfreader.app;

import android.content.ClipData;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MergePdfActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private Button btnSelectPdfs;
    private Button btnMerge;
    private ProgressBar progressBar;
    private TextView statusText;
    private List<Uri> selectedPdfs;
    private PdfBookAdapter adapter;
    private List<PdfBook> pdfList;
    private ActivityResultLauncher<Intent> pdfPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merge_pdf);
        
        // Register activity result launcher
        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePdfSelection(result.getData());
                    }
                });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.merge_pdfs);
        }

        recyclerView = findViewById(R.id.recyclerView);
        btnSelectPdfs = findViewById(R.id.btnSelectPdfs);
        btnMerge = findViewById(R.id.btnMerge);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        selectedPdfs = new ArrayList<>();
        pdfList = new ArrayList<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PdfBookAdapter(this, pdfList);
        recyclerView.setAdapter(adapter);

        btnSelectPdfs.setOnClickListener(v -> openFilePicker());
        btnMerge.setOnClickListener(v -> mergePdfs());

        btnMerge.setEnabled(false);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pdfPickerLauncher.launch(intent);
    }
    
    private void handlePdfSelection(Intent data) {
        selectedPdfs.clear();
        pdfList.clear();

        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                selectedPdfs.add(uri);
                pdfList.add(new PdfBook("PDF " + (i + 1), uri.toString(), ""));
            }
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            selectedPdfs.add(uri);
            pdfList.add(new PdfBook("PDF 1", uri.toString(), ""));
        }

        adapter.notifyDataSetChanged();
        statusText.setText(selectedPdfs.size() + " PDFs selected");
        statusText.setVisibility(View.VISIBLE);
        btnMerge.setEnabled(selectedPdfs.size() >= 2);
    }

    private void mergePdfs() {
        if (selectedPdfs.size() < 2) {
            Toast.makeText(this, "Please select at least 2 PDFs", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnMerge.setEnabled(false);
        statusText.setText("Merging PDFs...");

        new Thread(() -> {
            try {
                PdfDocument mergedDocument = new PdfDocument();
                int pageNumber = 1;

                for (Uri uri : selectedPdfs) {
                    // Copy to cache
                    File cacheFile = new File(getCacheDir(), "merge_temp_" + pageNumber + ".pdf");
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

                    for (int i = 0; i < renderer.getPageCount(); i++) {
                        PdfRenderer.Page page = renderer.openPage(i);

                        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(0xFFFFFFFF);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

                        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                                page.getWidth(), page.getHeight(), pageNumber++).create();
                        PdfDocument.Page newPage = mergedDocument.startPage(pageInfo);
                        Canvas canvas = newPage.getCanvas();
                        canvas.drawBitmap(bitmap, 0, 0, null);
                        mergedDocument.finishPage(newPage);

                        page.close();
                        bitmap.recycle();
                    }

                    renderer.close();
                    pfd.close();
                    cacheFile.delete();
                }

                // Save merged PDF
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File outputFile = new File(getExternalFilesDir(null), "merged_" + timestamp + ".pdf");

                FileOutputStream fos = new FileOutputStream(outputFile);
                mergedDocument.writeTo(fos);
                mergedDocument.close();
                fos.close();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnMerge.setEnabled(true);
                    statusText.setText("Merged successfully!");
                    Toast.makeText(this, getString(R.string.saved_to, outputFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnMerge.setEnabled(true);
                    statusText.setText("Error merging PDFs");
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
