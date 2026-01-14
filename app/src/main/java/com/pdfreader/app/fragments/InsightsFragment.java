package com.pdfreader.app.fragments;

import android.content.Context;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.ReadingProgressManager;
import com.pdfreader.app.R;
import com.pdfreader.app.models.ReadingStats;

import java.io.File;
import java.text.SimpleDateFormat;
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
    private TextView monthlyActivityText;
    
    private HistoryManager historyManager;
    private ReadingProgressManager readingProgressManager;
    private ExecutorService executorService;
    private Handler mainHandler;

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
        monthlyActivityText = view.findViewById(R.id.monthly_activity_text);
    }

    private void loadStats() {
        executorService.execute(() -> {
            try {
                // Get all books from history
                List<PdfBook> allBooks = historyManager.getHistory();
                
                // Calculate stats
                int totalBooks = allBooks.size();
                int finishedBooks = 0;
                int totalPagesRead = 0;
                int monthlyPagesRead = 0;
                
                Calendar currentMonth = Calendar.getInstance();
                int currentMonthNum = currentMonth.get(Calendar.MONTH);
                int currentYear = currentMonth.get(Calendar.YEAR);
                
                for (PdfBook book : allBooks) {
                    String path = book.getFilePath();
                    if (path == null || !path.toLowerCase().endsWith(".pdf")) {
                        continue;
                    }
                    
                    // Get progress for this book (stored as scroll position)
                    int savedProgress = readingProgressManager.getProgress(path);
                    
                    // Get page count
                    int pageCount = getPdfPageCount(requireContext(), path);
                    
                    if (pageCount > 0) {
                        // Progress is stored as: firstVisiblePosition * 1000
                        // So dividing by 1000 gives us the page number
                        int currentPage = savedProgress / 1000;
                        
                        // Clamp to valid range
                        currentPage = Math.max(0, Math.min(pageCount, currentPage));
                        
                        // Pages read is the current page (0-indexed, so add 1 for actual pages)
                        int pagesRead = currentPage;
                        totalPagesRead += pagesRead;
                        
                        // Estimate monthly pages (for now, count all as this month)
                        // TODO: Track actual reading dates
                        monthlyPagesRead += pagesRead;
                        
                        // Check if finished (progress indicates near completion or all pages read)
                        // Consider finished if current page is >= 95% of total pages
                        if (currentPage >= (pageCount * 0.95f) || currentPage >= pageCount) {
                            finishedBooks++;
                        }
                    }
                }
                
                // Calculate streak (simplified - count consecutive days with any reading)
                // For now, if user has books, assume they've been reading
                int streak = calculateStreak(allBooks);
                
                // Calculate reading speed (default estimate, can be improved with actual time tracking)
                int speed = 250; // Default WPM estimate
                
                // Calculate goal progress
                int yearlyGoal = 24; // Default goal
                int goalPercent = totalBooks > 0 ? Math.min(100, (totalBooks * 100) / yearlyGoal) : 0;
                
                // Get current month name
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.getDefault());
                String monthName = monthFormat.format(cal.getTime());
                
                // Update UI on main thread
                final int finalMonthlyPages = monthlyPagesRead;
                final int finalFinishedBooks = finishedBooks;
                final int finalSpeed = speed;
                final int finalStreak = streak;
                final int finalTotalBooks = totalBooks;
                final int finalGoalPercent = goalPercent;
                final String finalMonthName = monthName;
                
                mainHandler.post(() -> {
                    monthlyPages.setText(String.format("%,d", finalMonthlyPages));
                    booksFinished.setText(String.valueOf(finalFinishedBooks));
                    readingSpeed.setText(String.valueOf(finalSpeed));
                    currentStreak.setText(String.valueOf(finalStreak));
                    
                    // Update monthly activity text
                    if (monthlyActivityText != null) {
                        monthlyActivityText.setText("Activity in " + finalMonthName);
                    }
                    
                    // Update goal section
                    goalPercentage.setText(finalGoalPercent + "%");
                    if (goalProgress != null) {
                        goalProgress.setProgress(finalGoalPercent);
                    }
                    booksReadCount.setText(finalTotalBooks + " books read");
                    booksGoalCount.setText(yearlyGoal + " books goal");
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading stats", e);
                // Set defaults on error
                mainHandler.post(() -> {
                    monthlyPages.setText("0");
                    booksFinished.setText("0");
                    readingSpeed.setText("250");
                    currentStreak.setText("0");
                    if (goalPercentage != null) goalPercentage.setText("0%");
                    if (goalProgress != null) goalProgress.setProgress(0);
                    if (booksReadCount != null) booksReadCount.setText("0 books read");
                    if (booksGoalCount != null) booksGoalCount.setText("24 books goal");
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
        // Simplified streak calculation
        // If user has books in history, assume they've been reading
        // TODO: Implement proper streak tracking based on actual reading dates
        if (books.isEmpty()) {
            return 0;
        }
        
        // For now, return a simple estimate based on number of books
        // A more sophisticated implementation would track reading dates
        return Math.min(books.size(), 30); // Cap at 30 days
    }
}
