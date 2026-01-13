package com.pdfreader.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pdfreader.app.R;
import com.pdfreader.app.models.ReadingStats;

public class InsightsFragment extends Fragment {

    private TextView monthlyPages;
    private TextView booksFinished;
    private TextView readingSpeed;
    private TextView currentStreak;
    private ReadingStats stats;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_insights, container, false);

        initViews(view);
        loadStats();

        return view;
    }

    private void initViews(View view) {
        monthlyPages = view.findViewById(R.id.monthly_pages);
        booksFinished = view.findViewById(R.id.books_finished);
        readingSpeed = view.findViewById(R.id.reading_speed);
        currentStreak = view.findViewById(R.id.current_streak);
    }

    private void loadStats() {
        // TODO: Load actual stats from storage
        stats = new ReadingStats();
        stats.setMonthlyVolume(1420);
        stats.setBooksFinished(12);
        stats.setReadingSpeed(280);
        stats.setCurrentStreak(15);

        updateUI();
    }

    private void updateUI() {
        monthlyPages.setText(String.format("%,d", stats.getMonthlyVolume()));
        booksFinished.setText(String.valueOf(stats.getBooksFinished()));
        readingSpeed.setText(String.valueOf(stats.getReadingSpeed()));
        currentStreak.setText(String.valueOf(stats.getCurrentStreak()));
    }
}
