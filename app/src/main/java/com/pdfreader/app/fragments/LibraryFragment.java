package com.pdfreader.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.PdfReaderActivity;
import com.pdfreader.app.EpubReaderActivity;
import com.pdfreader.app.PdfThumbnailGenerator;
import com.pdfreader.app.ReadingProgressManager;
import com.pdfreader.app.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment {

    private TextView tabAll, tabReading, tabToRead, tabFinished;
    private View tabIndicator;
    private RecyclerView booksRecycler;
    private EditText searchInput;
    private FloatingActionButton fabAddBook;
    private HistoryManager historyManager;
    private LibraryBookAdapter adapter;
    private List<PdfBook> allBooks = new ArrayList<>();
    private List<PdfBook> filteredBooks = new ArrayList<>();
    private int selectedTab = 0;
    private ExecutorService executorService;
    private Handler mainHandler;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        historyManager = new HistoryManager(requireContext());
        executorService = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
        initViews(view);
        setupTabs();
        setupSearch();
        loadBooks();

        return view;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBooks();
    }

    private void initViews(View view) {
        tabAll = view.findViewById(R.id.tab_all);
        tabReading = view.findViewById(R.id.tab_reading);
        tabToRead = view.findViewById(R.id.tab_to_read);
        tabFinished = view.findViewById(R.id.tab_finished);
        tabIndicator = view.findViewById(R.id.tab_indicator);
        booksRecycler = view.findViewById(R.id.books_recycler);
        searchInput = view.findViewById(R.id.search_input);
        fabAddBook = view.findViewById(R.id.fab_add_book);

        booksRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));

        adapter = new LibraryBookAdapter(filteredBooks, executorService, mainHandler, book -> {
            String path = book.getFilePath();
            if (path.toLowerCase().contains(".epub")) {
                Intent intent = new Intent(getActivity(), EpubReaderActivity.class);
                intent.putExtra("EPUB_PATH", path);
                intent.putExtra("EPUB_TITLE", book.getTitle());
                startActivity(intent);
            } else {
                Intent intent = new Intent(getActivity(), PdfReaderActivity.class);
                intent.putExtra("PDF_PATH", path);
                intent.putExtra("PDF_TITLE", book.getTitle());
                startActivity(intent);
            }
        });
        booksRecycler.setAdapter(adapter);

        // Open file manager to pick PDF or EPUB
        fabAddBook.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "application/epub+zip"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        // Take persistable permission
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        String title = getFileNameFromUri(uri);
        String path = uri.toString();

        // Add to history
        historyManager.addToHistory(title, path);

        // Open the file
        if (path.toLowerCase().contains(".epub") || title.toLowerCase().endsWith(".epub")) {
            Intent intent = new Intent(getActivity(), EpubReaderActivity.class);
            intent.putExtra("EPUB_PATH", path);
            intent.putExtra("EPUB_TITLE", title);
            startActivity(intent);
        } else {
            Intent intent = new Intent(getActivity(), PdfReaderActivity.class);
            intent.putExtra("PDF_PATH", path);
            intent.putExtra("PDF_TITLE", title);
            startActivity(intent);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "Unknown";
        try {
            Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
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
        // Remove extension for display
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        return fileName;
    }

    private void setupTabs() {
        tabAll.setOnClickListener(v -> selectTab(0));
        tabReading.setOnClickListener(v -> selectTab(1));
        tabToRead.setOnClickListener(v -> selectTab(2));
        tabFinished.setOnClickListener(v -> selectTab(3));
    }
    
    private void selectTab(int position) {
        selectedTab = position;
        updateTabAppearance();
        filterBooks(position);
    }
    
    private void updateTabAppearance() {
        // Reset all tabs with more visible colors
        int unselectedColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray);
        tabAll.setTextColor(unselectedColor);
        tabAll.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabReading.setTextColor(unselectedColor);
        tabReading.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabToRead.setTextColor(unselectedColor);
        tabToRead.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabFinished.setTextColor(unselectedColor);
        tabFinished.setTypeface(null, android.graphics.Typeface.NORMAL);
        
        // Highlight selected tab
        TextView selectedTextView = null;
        switch (selectedTab) {
            case 0:
                selectedTextView = tabAll;
                break;
            case 1:
                selectedTextView = tabReading;
                break;
            case 2:
                selectedTextView = tabToRead;
                break;
            case 3:
                selectedTextView = tabFinished;
                break;
        }
        
        if (selectedTextView != null) {
            final TextView finalSelectedTextView = selectedTextView; // Make effectively final for lambda
            finalSelectedTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
            finalSelectedTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            
            // Update indicator position after layout
            if (tabIndicator != null) {
                finalSelectedTextView.post(() -> {
                    int tabWidth = finalSelectedTextView.getWidth();
                    int tabStart = finalSelectedTextView.getLeft();
                    android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) tabIndicator.getLayoutParams();
                    params.width = tabWidth;
                    params.leftMargin = tabStart;
                    tabIndicator.setLayoutParams(params);
                });
            }
        }
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchBooks(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadBooks() {
        allBooks.clear();
        allBooks.addAll(historyManager.getHistory());
        filterBooks(selectedTab);
    }

    private void filterBooks(int tabPosition) {
        filteredBooks.clear();

        for (PdfBook book : allBooks) {
            switch (tabPosition) {
                case 0: // All
                    filteredBooks.add(book);
                    break;
                case 1: // Reading
                case 2: // To-Read
                case 3: // Finished
                    // For now, show all books in all tabs
                    // TODO: Implement proper filtering based on reading status
                    filteredBooks.add(book);
                    break;
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void searchBooks(String query) {
        filteredBooks.clear();

        if (query.isEmpty()) {
            filterBooks(selectedTab);
            return;
        }

        // Search only within app's library (allBooks from history)
        for (PdfBook book : allBooks) {
            if (book.getTitle().toLowerCase().contains(query.toLowerCase())) {
                filteredBooks.add(book);
            }
        }

        adapter.notifyDataSetChanged();
    }

    // Inner adapter class for grid display
    private static class LibraryBookAdapter extends RecyclerView.Adapter<LibraryBookAdapter.ViewHolder> {

        private final List<PdfBook> books;
        private final OnBookClickListener listener;
        private final ExecutorService executorService;
        private final Handler mainHandler;

        interface OnBookClickListener {
            void onBookClick(PdfBook book);
        }

        LibraryBookAdapter(List<PdfBook> books, ExecutorService executorService, Handler mainHandler, OnBookClickListener listener) {
            this.books = books;
            this.listener = listener;
            this.executorService = executorService;
            this.mainHandler = mainHandler;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_library_book, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PdfBook book = books.get(position);
            holder.bind(book, listener, executorService, mainHandler);
        }

        @Override
        public int getItemCount() {
            return books.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView titleText;
            private final android.widget.ImageView coverImage;
            private final ProgressBar progressBar;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.book_title);
                coverImage = itemView.findViewById(R.id.book_cover);
                progressBar = itemView.findViewById(R.id.reading_progress);
            }

            void bind(PdfBook book, OnBookClickListener listener, ExecutorService executorService, Handler mainHandler) {
                titleText.setText(book.getTitle());
                
                // Load thumbnail for PDF files (matching home page implementation)
                String path = book.getFilePath();
                String title = book.getTitle();
                
                // Load actual reading progress (calculate percentage from page number)
                if (progressBar != null) {
                    ReadingProgressManager progressManager = new ReadingProgressManager(itemView.getContext());
                    
                    // Get scroll position (stored as firstVisiblePosition * 1000)
                    int savedProgress = progressManager.getProgress(path);
                    int currentPage = savedProgress / 1000;
                    
                    // Calculate progress percentage asynchronously
                    executorService.execute(() -> {
                        try {
                            int totalPages = getPdfPageCount(itemView.getContext(), path);
                            
                            mainHandler.post(() -> {
                                if (totalPages > 0 && currentPage > 0) {
                                    // Calculate percentage: (currentPage / totalPages) * 100
                                    int progressPercent = Math.min(100, (int) ((currentPage * 100.0f) / totalPages));
                                    progressBar.setProgress(progressPercent);
                                } else {
                                    // No progress yet or can't read PDF
                                    progressBar.setProgress(0);
                                }
                            });
                        } catch (Exception e) {
                            android.util.Log.e("LibraryFragment", "Error calculating progress for: " + book.getTitle(), e);
                            mainHandler.post(() -> progressBar.setProgress(0));
                        }
                    });
                }

                // Show placeholder while loading
                coverImage.setImageResource(R.drawable.placeholder_book);
                coverImage.setTag(path); // Use path as tag to track which book this view is showing

                // Check if this is a PDF file - check both path and title for .pdf extension
                // Content URIs don't have extensions in the URI itself
                boolean isPdf = (path != null && path.toLowerCase().contains(".pdf")) ||
                               (title != null && title.toLowerCase().endsWith(".pdf")) ||
                               (path != null && !path.toLowerCase().contains(".epub"));

                if (path != null && isPdf) {
                    // Load thumbnail in background (same as home page)
                    executorService.execute(() -> {
                        try {
                            android.content.Context context = itemView.getContext();
                            if (context == null) {
                                return;
                            }
                            
                            // Get display dimensions for thumbnail (matching home page)
                            int maxWidth = 400; // Cover width
                            int maxHeight = 560; // Cover height (maintaining aspect ratio)
                            
                            Bitmap thumbnail = PdfThumbnailGenerator.generateThumbnail(
                                context,
                                path,
                                maxWidth,
                                maxHeight
                            );
                            
                            if (thumbnail != null && !thumbnail.isRecycled()) {
                                mainHandler.post(() -> {
                                    // Check if this view is still showing the same book (using path as tag)
                                    if (coverImage != null && coverImage.getTag() != null && 
                                        coverImage.getTag().equals(path)) {
                                        coverImage.setImageBitmap(thumbnail);
                                        coverImage.invalidate(); // Force redraw
                                        android.util.Log.d("LibraryFragment", "Cover loaded successfully for: " + book.getTitle());
                                    } else {
                                        // Fragment detached or view recycled, recycle bitmap
                                        if (thumbnail != null && !thumbnail.isRecycled()) {
                                            thumbnail.recycle();
                                        }
                                    }
                                });
                            } else {
                                android.util.Log.w("LibraryFragment", "Thumbnail is null or recycled for: " + book.getTitle());
                            }
                        } catch (Exception e) {
                            android.util.Log.e("LibraryFragment", "Error loading cover for: " + path, e);
                        }
                    });
                } else {
                    // EPUB or other - use placeholder
                    coverImage.setImageResource(R.drawable.placeholder_book);
                }
                
                itemView.setOnClickListener(v -> listener.onBookClick(book));
            }
            
            private int getPdfPageCount(android.content.Context context, String pdfPath) {
                try {
                    android.graphics.pdf.PdfRenderer renderer = null;
                    android.os.ParcelFileDescriptor pfd = null;
                    
                    if (pdfPath.startsWith("content://") || pdfPath.startsWith("file://")) {
                        android.net.Uri uri = android.net.Uri.parse(pdfPath);
                        pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                    } else {
                        java.io.File file = new java.io.File(pdfPath);
                        if (file.exists()) {
                            pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                        }
                    }
                    
                    if (pfd != null) {
                        renderer = new android.graphics.pdf.PdfRenderer(pfd);
                        int pageCount = renderer.getPageCount();
                        renderer.close();
                        pfd.close();
                        return pageCount;
                    }
                } catch (Exception e) {
                    android.util.Log.e("LibraryFragment", "Error reading PDF page count", e);
                }
                return 0;
            }
        }
    }
}
