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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.PdfReaderActivity;
import com.pdfreader.app.EpubReaderActivity;
import com.pdfreader.app.R;

import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment {

    private TabLayout tabLayout;
    private RecyclerView booksRecycler;
    private EditText searchInput;
    private FloatingActionButton fabAddBook;
    private HistoryManager historyManager;
    private LibraryBookAdapter adapter;
    private List<PdfBook> allBooks = new ArrayList<>();
    private List<PdfBook> filteredBooks = new ArrayList<>();

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
        initViews(view);
        setupTabs();
        setupSearch();
        loadBooks();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBooks();
    }

    private void initViews(View view) {
        tabLayout = view.findViewById(R.id.tab_layout);
        booksRecycler = view.findViewById(R.id.books_recycler);
        searchInput = view.findViewById(R.id.search_input);
        fabAddBook = view.findViewById(R.id.fab_add_book);

        booksRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));

        adapter = new LibraryBookAdapter(filteredBooks, book -> {
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
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterBooks(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
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
        filterBooks(tabLayout.getSelectedTabPosition());
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
                    filteredBooks.add(book);
                    break;
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void searchBooks(String query) {
        filteredBooks.clear();

        if (query.isEmpty()) {
            filterBooks(tabLayout.getSelectedTabPosition());
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

        interface OnBookClickListener {
            void onBookClick(PdfBook book);
        }

        LibraryBookAdapter(List<PdfBook> books, OnBookClickListener listener) {
            this.books = books;
            this.listener = listener;
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
            holder.bind(book, listener);
        }

        @Override
        public int getItemCount() {
            return books.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final android.widget.TextView titleText;
            private final android.widget.ImageView coverImage;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.book_title);
                coverImage = itemView.findViewById(R.id.book_cover);
            }

            void bind(PdfBook book, OnBookClickListener listener) {
                titleText.setText(book.getTitle());
                itemView.setOnClickListener(v -> listener.onBookClick(book));
            }
        }
    }
}
