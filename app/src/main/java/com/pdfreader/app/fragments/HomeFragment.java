package com.pdfreader.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.MainActivityNew;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.PdfBookAdapter;
import com.pdfreader.app.PdfReaderActivity;
import com.pdfreader.app.R;
import com.pdfreader.app.SearchActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView greetingText;
    private TextView streakNumber;
    private TextView bookTitle;
    private TextView bookAuthor;
    private TextView progressPercentage;
    private ImageView bookCover;
    private RecyclerView upNextRecycler;
    private LinearLayout emptyState;
    private HistoryManager historyManager;
    private List<PdfBook> recentBooks = new ArrayList<>();
    private PdfBookAdapter adapter;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        historyManager = new HistoryManager(requireContext());

        setupFilePickerLauncher();
        initViews(view);
        setupGreeting();
        setupQuickActions(view);
        loadRecentBooks();

        return view;
    }

    private void setupFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        openPdfFromUri(uri);
                    }
                }
            }
        );
    }

    private void openPdfFromUri(Uri uri) {
        String fileName = "Document";
        try {
            String path = uri.getPath();
            if (path != null) {
                fileName = path.substring(path.lastIndexOf('/') + 1);
                if (fileName.toLowerCase().endsWith(".pdf")) {
                    fileName = fileName.substring(0, fileName.length() - 4);
                }
            }
        } catch (Exception ignored) {}

        Intent intent = new Intent(getActivity(), PdfReaderActivity.class);
        intent.putExtra("PDF_PATH", uri.toString());
        intent.putExtra("PDF_TITLE", fileName);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentBooks();
        updateCurrentlyReadingCard();
    }

    private void initViews(View view) {
        greetingText = view.findViewById(R.id.greeting_text);
        streakNumber = view.findViewById(R.id.streak_number);
        bookTitle = view.findViewById(R.id.book_title);
        bookAuthor = view.findViewById(R.id.book_author);
        progressPercentage = view.findViewById(R.id.progress_percentage);
        bookCover = view.findViewById(R.id.book_cover);
        upNextRecycler = view.findViewById(R.id.up_next_recycler);
        emptyState = view.findViewById(R.id.empty_state);

        upNextRecycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        adapter = new PdfBookAdapter(requireContext(), recentBooks);
        upNextRecycler.setAdapter(adapter);
    }

    private void setupGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting;

        if (hour < 12) {
            greeting = "Good Morning";
        } else if (hour < 17) {
            greeting = "Good Afternoon";
        } else {
            greeting = "Good Evening";
        }

        greetingText.setText(greeting);
        streakNumber.setText("12");
    }

    private void setupQuickActions(View view) {
        // Open PDF quick action
        View actionOpenPdf = view.findViewById(R.id.action_open_pdf);
        if (actionOpenPdf != null) {
            actionOpenPdf.setOnClickListener(v -> openFilePicker());
        }

        // Scan quick action
        View actionScan = view.findViewById(R.id.action_scan);
        if (actionScan != null) {
            actionScan.setOnClickListener(v -> {
                // Navigate to scanner tab
                if (getActivity() instanceof MainActivityNew) {
                    ((MainActivityNew) getActivity()).navigateToTab(R.id.navigation_scan);
                }
            });
        }

        // History quick action
        View actionHistory = view.findViewById(R.id.action_history);
        if (actionHistory != null) {
            actionHistory.setOnClickListener(v -> {
                // Navigate to library tab
                if (getActivity() instanceof MainActivityNew) {
                    ((MainActivityNew) getActivity()).navigateToTab(R.id.navigation_library);
                }
            });
        }

        // Browse quick action
        View actionBrowse = view.findViewById(R.id.action_browse);
        if (actionBrowse != null) {
            actionBrowse.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            });
        }

        // Start reading button
        View startReadingButton = view.findViewById(R.id.start_reading_button);
        if (startReadingButton != null) {
            startReadingButton.setOnClickListener(v -> {
                if (!recentBooks.isEmpty()) {
                    PdfBook lastBook = recentBooks.get(0);
                    Intent intent = new Intent(getActivity(), PdfReaderActivity.class);
                    intent.putExtra("PDF_PATH", lastBook.getFilePath());
                    intent.putExtra("PDF_TITLE", lastBook.getTitle());
                    startActivity(intent);
                } else {
                    openFilePicker();
                }
            });
        }

        // Currently reading card
        View currentlyReadingCard = view.findViewById(R.id.currently_reading_card);
        if (currentlyReadingCard != null) {
            currentlyReadingCard.setOnClickListener(v -> {
                if (!recentBooks.isEmpty()) {
                    PdfBook lastBook = recentBooks.get(0);
                    Intent intent = new Intent(getActivity(), PdfReaderActivity.class);
                    intent.putExtra("PDF_PATH", lastBook.getFilePath());
                    intent.putExtra("PDF_TITLE", lastBook.getTitle());
                    startActivity(intent);
                }
            });
        }

        // View all recent
        View viewAllRecent = view.findViewById(R.id.view_all_recent);
        if (viewAllRecent != null) {
            viewAllRecent.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivityNew) {
                    ((MainActivityNew) getActivity()).navigateToTab(R.id.navigation_library);
                }
            });
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        filePickerLauncher.launch(intent);
    }

    private void loadRecentBooks() {
        recentBooks.clear();
        List<PdfBook> history = historyManager.getHistory();

        // Get up to 5 recent books
        int count = Math.min(history.size(), 5);
        for (int i = 0; i < count; i++) {
            recentBooks.add(history.get(i));
        }

        adapter.notifyDataSetChanged();

        // Show/hide empty state
        if (emptyState != null) {
            if (recentBooks.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                upNextRecycler.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                upNextRecycler.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateCurrentlyReadingCard() {
        if (!recentBooks.isEmpty()) {
            PdfBook currentBook = recentBooks.get(0);
            if (bookTitle != null) {
                bookTitle.setText(currentBook.getTitle());
            }
            if (bookAuthor != null) {
                // For PDFs, we don't have author info, so show file size or path
                bookAuthor.setText("PDF Document");
            }
        }
    }
}
