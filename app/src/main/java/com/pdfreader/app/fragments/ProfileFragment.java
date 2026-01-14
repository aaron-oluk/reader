package com.pdfreader.app.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.ReadingProgressManager;
import com.pdfreader.app.R;

import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String PREFS_NAME = "user_profile";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";

    private TextView profileName;
    private TextView profileEmail;
    private TextView totalBooksCount;
    private TextView totalPagesCount;
    private TextView currentStreakCount;
    private TextView dailyGoalsText;

    private HistoryManager historyManager;
    private ReadingProgressManager readingProgressManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        historyManager = new HistoryManager(requireContext());
        readingProgressManager = new ReadingProgressManager(requireContext());
        executorService = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());

        initViews(view);
        loadProfileData();
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
        profileName = view.findViewById(R.id.profile_name);
        profileEmail = view.findViewById(R.id.profile_email);
        totalBooksCount = view.findViewById(R.id.total_books_count);
        totalPagesCount = view.findViewById(R.id.total_pages_count);
        currentStreakCount = view.findViewById(R.id.current_streak_count);
        dailyGoalsText = view.findViewById(R.id.daily_goals_text);
        
        // Sign Out button
        View signOutButton = view.findViewById(R.id.sign_out_button);
        if (signOutButton != null) {
            signOutButton.setOnClickListener(v -> handleSignOut());
        }
    }
    
    private void handleSignOut() {
        // TODO: Implement sign out logic
        // For now, just show a toast
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), "Sign out clicked", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProfileData() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String userName = prefs.getString(KEY_USER_NAME, "User");
        String userEmail = prefs.getString(KEY_USER_EMAIL, "reader@example.com");

        profileName.setText(userName);
        profileEmail.setText(userEmail);

        // Load daily goals
        int dailyTimeGoal = 60; // Default
        int dailyPagesGoal = 30; // Default
        dailyGoalsText.setText(dailyTimeGoal + " min / " + dailyPagesGoal + " pages daily");
    }

    private void loadStats() {
        executorService.execute(() -> {
            try {
                List<PdfBook> allBooks = historyManager.getHistory();
                int totalBooks = allBooks.size();
                int totalPagesRead = 0;
                int streak = 0;

                for (PdfBook book : allBooks) {
                    String path = book.getFilePath();
                    if (path == null || !path.toLowerCase().endsWith(".pdf")) {
                        continue;
                    }

                    int savedProgress = readingProgressManager.getProgress(path);
                    int pageCount = getPdfPageCount(requireContext(), path);

                    if (pageCount > 0) {
                        int currentPage = savedProgress / 1000;
                        currentPage = Math.max(0, Math.min(pageCount, currentPage));
                        totalPagesRead += currentPage;
                    }
                }

                // Calculate streak (simplified)
                streak = calculateStreak(allBooks);

                final int finalTotalBooks = totalBooks;
                final int finalTotalPages = totalPagesRead;
                final int finalStreak = streak;

                mainHandler.post(() -> {
                    totalBooksCount.setText(String.valueOf(finalTotalBooks));
                    totalPagesCount.setText(String.format("%,d", finalTotalPages));
                    currentStreakCount.setText(String.valueOf(finalStreak));
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading stats", e);
                mainHandler.post(() -> {
                    totalBooksCount.setText("0");
                    totalPagesCount.setText("0");
                    currentStreakCount.setText("0");
                });
            }
        });
    }

    private int getPdfPageCount(Context context, String pdfPath) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;

        try {
            if (pdfPath.startsWith("content://") || pdfPath.startsWith("file://")) {
                Uri uri = Uri.parse(pdfPath);
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            } else {
                File file = new File(pdfPath);
                if (file.exists()) {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                }
            }

            if (pfd != null) {
                renderer = new PdfRenderer(pfd);
                return renderer.getPageCount();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting page count for: " + pdfPath, e);
        } finally {
            if (renderer != null) {
                renderer.close();
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return 0;
    }

    private int calculateStreak(List<PdfBook> books) {
        if (books.isEmpty()) {
            return 0;
        }
        // Simplified streak calculation
        return Math.min(books.size(), 30);
    }
}
