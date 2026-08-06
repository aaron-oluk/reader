package com.pdfreader.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.ReadingProgressManager;
import com.pdfreader.app.R;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InsightsFragment extends Fragment {

    private static final String TAG = "InsightsFragment";

    private TextView monthlyPages;
    private TextView booksFinished;
    private TextView readingSpeed;
    private TextView currentStreak;
    private TextView goalPercentage;
    private TextView booksReadCount;
    private TextView booksGoalCount;
    private ProgressBar goalProgress;
    private TextView weeklyProgressText;
    private View shareButton;

    private HistoryManager historyManager;
    private ReadingProgressManager readingProgressManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    // Latest loaded values for sharing
    private String reportMonthlyPages = "0";
    private String reportBooksFinished = "0";
    private String reportStreak = "0";
    private String reportGoalPercent = "0%";
    private String reportBooksRead = "0";
    private String reportBooksGoal = "24";
    private String reportWeekly = "Last 7 days";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_insights, container, false);

        historyManager = new HistoryManager(requireContext());
        readingProgressManager = new ReadingProgressManager(requireContext());
        executorService = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());

        initViews(view);
        loadStats();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void initViews(View view) {
        monthlyPages = view.findViewById(R.id.monthly_pages);
        booksFinished = view.findViewById(R.id.books_finished);
        readingSpeed = view.findViewById(R.id.reading_speed);
        currentStreak = view.findViewById(R.id.current_streak);
        goalPercentage = view.findViewById(R.id.goal_percentage);
        booksReadCount = view.findViewById(R.id.books_read_count);
        booksGoalCount = view.findViewById(R.id.books_goal_count);
        goalProgress = view.findViewById(R.id.goal_progress);
        weeklyProgressText = view.findViewById(R.id.weekly_progress_text);
        shareButton = view.findViewById(R.id.share_button);

        if (shareButton != null) {
            shareButton.setOnClickListener(v -> shareReadingReport());
        }
    }

    private void shareReadingReport() {
        String report = "Signet Reading Insights\n"
                + "────────────────────\n"
                + "Pages read this month: " + reportMonthlyPages + "\n"
                + "Books finished: " + reportBooksFinished + "\n"
                + "Current streak: " + reportStreak + " days\n"
                + "Yearly goal: " + reportGoalPercent
                + " (" + reportBooksRead + " / " + reportBooksGoal + ")\n"
                + "Weekly activity: " + reportWeekly + "\n\n"
                + "Shared from Signet";

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Signet Reading Insights");
            shareIntent.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(shareIntent, "Share reading report"));
        } catch (Exception e) {
            Log.e(TAG, "Unable to share report", e);
            Toast.makeText(requireContext(), "Unable to share report", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadStats() {
        executorService.execute(() -> {
            try {
                List<PdfBook> allBooks = historyManager.getHistory();

                int finishedBooks = 0;
                int monthlyPagesRead = 0;
                int thisWeekPages = 0;
                int lastWeekPages = 0;

                long now = System.currentTimeMillis();
                long startOfMonth = startOfCurrentMonth();
                long startOfThisWeek = now - 7L * 24 * 60 * 60 * 1000;
                long startOfLastWeek = startOfThisWeek - 7L * 24 * 60 * 60 * 1000;

                for (PdfBook book : allBooks) {
                    String path = book.getFilePath();
                    int totalPages = readingProgressManager.getPageCount(path);
                    int currentPage = readingProgressManager.getProgress(path) / 1000;
                    long lastRead  = historyManager.getLastRead(path);

                    if (totalPages > 0) {
                        if (currentPage >= totalPages * 0.95f) finishedBooks++;

                        if (lastRead >= startOfMonth) monthlyPagesRead += currentPage;
                        if (lastRead >= startOfThisWeek) thisWeekPages += currentPage;
                        else if (lastRead >= startOfLastWeek) lastWeekPages += currentPage;
                    }
                }

                int streak    = historyManager.calculateStreak();
                int yearlyGoal = 24;
                int goalPercent = Math.min(100, (allBooks.size() * 100) / yearlyGoal);

                String weeklyLabel;
                if (lastWeekPages == 0) {
                    weeklyLabel = "Last 7 days";
                } else {
                    int pct = (int) (((thisWeekPages - lastWeekPages) * 100f) / lastWeekPages);
                    weeklyLabel = pct >= 0 ? "Last 7 days  +" + pct + "%" : "Last 7 days  " + pct + "%";
                }

                final int fMonthly = monthlyPagesRead;
                final int fFinished = finishedBooks;
                final int fStreak = streak;
                final int fGoal = goalPercent;
                final int fTotal = allBooks.size();
                final int fYearlyGoal = yearlyGoal;
                final String fWeekly = weeklyLabel;

                mainHandler.post(() -> {
                    reportMonthlyPages = String.format(Locale.US, "%,d", fMonthly);
                    reportBooksFinished = String.valueOf(fFinished);
                    reportStreak = String.valueOf(fStreak);
                    reportGoalPercent = fGoal + "%";
                    reportBooksRead = String.valueOf(fTotal);
                    reportBooksGoal = String.valueOf(fYearlyGoal);
                    reportWeekly = fWeekly;

                    monthlyPages.setText(reportMonthlyPages);
                    booksFinished.setText(reportBooksFinished);
                    readingSpeed.setText("—");
                    currentStreak.setText(reportStreak);
                    if (weeklyProgressText != null) weeklyProgressText.setText(fWeekly);
                    goalPercentage.setText(reportGoalPercent);
                    if (goalProgress != null) goalProgress.setProgress(fGoal);
                    booksReadCount.setText(reportBooksRead);
                    booksGoalCount.setText(" / " + fYearlyGoal);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading stats", e);
                mainHandler.post(() -> {
                    monthlyPages.setText("0");
                    booksFinished.setText("0");
                    readingSpeed.setText("—");
                    currentStreak.setText("0");
                    if (goalPercentage != null) goalPercentage.setText("0%");
                    if (goalProgress != null) goalProgress.setProgress(0);
                    if (booksReadCount != null) booksReadCount.setText("0");
                    if (booksGoalCount != null) booksGoalCount.setText(" / 24");
                    if (weeklyProgressText != null) weeklyProgressText.setText("Last 7 days");
                });
            }
        });
    }

    private long startOfCurrentMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
