package com.pdfreader.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdfreader.app.HistoryManager;
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
    private RecyclerView upNextRecycler;
    private HistoryManager historyManager;
    private List<PdfBook> recentBooks = new ArrayList<>();
    private PdfBookAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        historyManager = new HistoryManager(requireContext());
        initViews(view);
        setupGreeting();
        setupQuickActions(view);
        loadRecentBooks();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentBooks();
    }

    private void initViews(View view) {
        greetingText = view.findViewById(R.id.greeting_text);
        streakNumber = view.findViewById(R.id.streak_number);
        upNextRecycler = view.findViewById(R.id.up_next_recycler);

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
                    // Open search if no books in history
                    Intent intent = new Intent(getActivity(), SearchActivity.class);
                    startActivity(intent);
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
    }
}
