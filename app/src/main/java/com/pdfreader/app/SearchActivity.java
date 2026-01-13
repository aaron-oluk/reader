package com.pdfreader.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchActivity extends AppCompatActivity {

    private EditText searchInput;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyText;
    private PdfBookAdapter adapter;
    private List<PdfBook> allFiles;
    private List<PdfBook> filteredFiles;
    private HistoryManager historyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.search_files);
        }

        historyManager = new HistoryManager(this);

        searchInput = findViewById(R.id.searchInput);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);

        allFiles = new ArrayList<>();
        filteredFiles = new ArrayList<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PdfBookAdapter(this, filteredFiles);
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFiles(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Start scanning for files
        scanFiles();
    }

    private void filterFiles(String query) {
        filteredFiles.clear();
        if (query.isEmpty()) {
            filteredFiles.addAll(allFiles);
        } else {
            String lowerQuery = query.toLowerCase();
            for (PdfBook book : allFiles) {
                if (book.getTitle().toLowerCase().contains(lowerQuery)) {
                    filteredFiles.add(book);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredFiles.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    public void openPdfReader(String filePath, String title) {
        historyManager.addToHistory(title, filePath);
        Intent intent = new Intent(this, PdfReaderActivity.class);
        intent.putExtra("PDF_PATH", filePath);
        intent.putExtra("PDF_TITLE", title);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private void scanFiles() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        executorService.execute(() -> {
            List<PdfBook> files = new ArrayList<>();

            // Scan common directories
            File[] directories = {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStorageDirectory()
            };

            for (File dir : directories) {
                if (dir != null && dir.exists()) {
                    scanDirectory(dir, files, 0);
                }
            }

            // Update UI on main thread
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                allFiles.clear();
                allFiles.addAll(files);
                filterFiles(searchInput.getText().toString());
            });
        });
    }

    private void scanDirectory(File directory, List<PdfBook> files, int depth) {
        if (depth > 5) return; // Limit recursion depth

        File[] fileList = directory.listFiles();
        if (fileList == null) return;

        for (File file : fileList) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                scanDirectory(file, files, depth + 1);
            } else if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".pdf") || name.endsWith(".epub")) {
                    String title = file.getName();
                    if (title.contains(".")) {
                        title = title.substring(0, title.lastIndexOf("."));
                    }
                    files.add(new PdfBook(title, file.getAbsolutePath(), formatFileSize(file.length())));
                }
            }
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
