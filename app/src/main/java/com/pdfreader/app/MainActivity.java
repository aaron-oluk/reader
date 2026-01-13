package com.pdfreader.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private RecyclerView recyclerView;
    private PdfBookAdapter adapter;
    private List<PdfBook> pdfBooks;
    private TextView resultsHeader;
    private HistoryManager historyManager;
    
    private ActivityResultLauncher<Intent> pdfPickerLauncher;
    private ActivityResultLauncher<Intent> epubPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Register activity result launchers
        pdfPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleSelectedFile(uri, true);
                        }
                    }
                });
        
        epubPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleSelectedFile(uri, false);
                        }
                    }
                });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        historyManager = new HistoryManager(this);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        resultsHeader = findViewById(R.id.resultsHeader);

        pdfBooks = new ArrayList<>();
        adapter = new PdfBookAdapter(this, pdfBooks);
        recyclerView.setAdapter(adapter);

        // Setup card click listeners
        setupCardListeners();

        // Check permissions on start
        checkPermissions();
    }

    private void setupCardListeners() {
        CardView cardOpenPdf = findViewById(R.id.cardOpenPdf);
        CardView cardSearch = findViewById(R.id.cardSearch);
        CardView cardHistory = findViewById(R.id.cardHistory);
        CardView cardMerge = findViewById(R.id.cardMerge);
        CardView cardSign = findViewById(R.id.cardSign);
        CardView cardImageToPdf = findViewById(R.id.cardImageToPdf);
        CardView cardEpub = findViewById(R.id.cardEpub);

        cardOpenPdf.setOnClickListener(v -> {
            if (hasStoragePermission()) {
                openPdfPicker();
            } else {
                checkPermissions();
            }
        });

        cardSearch.setOnClickListener(v -> {
            if (hasStoragePermission()) {
                startActivity(new Intent(this, SearchActivity.class));
            } else {
                checkPermissions();
            }
        });

        cardHistory.setOnClickListener(v -> loadHistory());

        cardMerge.setOnClickListener(v -> {
            startActivity(new Intent(this, MergePdfActivity.class));
        });

        cardSign.setOnClickListener(v -> {
            startActivity(new Intent(this, SignPdfActivity.class));
        });

        cardImageToPdf.setOnClickListener(v -> {
            startActivity(new Intent(this, ImageToPdfActivity.class));
        });

        cardEpub.setOnClickListener(v -> {
            openEpubPicker();
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void showStoragePermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs access to your files to open documents. Please grant \"All files access\" permission.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(this, "Permission denied. Some features may not work.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(true)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied. Some features may not work.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadHistory() {
        pdfBooks.clear();
        List<PdfBook> history = historyManager.getHistory();

        if (history.isEmpty()) {
            resultsHeader.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            Toast.makeText(this, R.string.no_history, Toast.LENGTH_SHORT).show();
        } else {
            pdfBooks.addAll(history);
            resultsHeader.setVisibility(View.VISIBLE);
            resultsHeader.setText(R.string.recent_files);
            recyclerView.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pdfPickerLauncher.launch(intent);
    }

    private void openEpubPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/epub+zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        epubPickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri, boolean isPdf) {
        // Take persistable permission
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        String title = getFileNameFromUri(uri);
        String path = uri.toString();

        if (isPdf) {
            historyManager.addToHistory(title, path);
            openPdfReader(path, title);
        } else {
            historyManager.addToHistory(title, path);
            openEpubReader(path, title);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "Unknown";
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex("_display_name");
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Remove extension
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        return fileName;
    }

    public void openPdfReader(String filePath, String title) {
        historyManager.addToHistory(title, filePath);
        Intent intent = new Intent(this, PdfReaderActivity.class);
        intent.putExtra("PDF_PATH", filePath);
        intent.putExtra("PDF_TITLE", title);
        startActivity(intent);
    }

    public void openEpubReader(String filePath, String title) {
        historyManager.addToHistory(title, filePath);
        Intent intent = new Intent(this, EpubReaderActivity.class);
        intent.putExtra("EPUB_PATH", filePath);
        intent.putExtra("EPUB_TITLE", title);
        startActivity(intent);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
}
