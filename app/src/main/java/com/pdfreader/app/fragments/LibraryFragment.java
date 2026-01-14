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
        // Reset all tabs
        tabAll.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tabAll.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabReading.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tabReading.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabToRead.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tabToRead.setTypeface(null, android.graphics.Typeface.NORMAL);
        tabFinished.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
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
                
                // Load progress (for now, set a default progress - actual progress calculation needs page count)
                if (progressBar != null) {
                    // TODO: Calculate actual progress percentage based on scroll position and total pages
                    progressBar.setProgress(30); // Placeholder
                }
                
                // Load thumbnail for PDF files
                String path = book.getFilePath();
                if (path.toLowerCase().endsWith(".pdf")) {
                    // Set placeholder first
                    coverImage.setImageResource(R.drawable.placeholder_book);
                    
                    // Load thumbnail in background
                    executorService.execute(() -> {
                        Bitmap thumbnail = PdfThumbnailGenerator.generateThumbnail(
                            itemView.getContext(),
                            path,
                            400, // max width
                            300  // max height
                        );
                        
                        mainHandler.post(() -> {
                            if (thumbnail != null) {
                                coverImage.setImageBitmap(thumbnail);
                            }
                        });
                    });
                } else {
                    // EPUB or other - use placeholder
                    coverImage.setImageResource(R.drawable.placeholder_book);
                }
                
                itemView.setOnClickListener(v -> listener.onBookClick(book));
            }
        }
    }
}
