package com.pdfreader.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.R;
import com.pdfreader.app.ReadingProgressManager;
import com.pdfreader.app.SignatureManager;

import java.io.File;
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
    private ImageView activeSignaturePreview;
    private ImageView activeSignaturePlaceholder;
    private TextView activeSignatureName;

    private HistoryManager historyManager;
    private ReadingProgressManager readingProgressManager;
    private SignatureManager signatureManager;
    private ExecutorService executorService;
    private Handler mainHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        historyManager = new HistoryManager(requireContext());
        readingProgressManager = new ReadingProgressManager(requireContext());
        signatureManager = new SignatureManager(requireContext());
        executorService = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());

        initViews(view);
        loadProfileData();
        loadStats();
        loadActiveSignature();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadActiveSignature();
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
        activeSignaturePreview = view.findViewById(R.id.profile_active_signature_preview);
        activeSignaturePlaceholder = view.findViewById(R.id.profile_active_signature_placeholder);
        activeSignatureName = view.findViewById(R.id.profile_active_signature_name);

        View.OnClickListener openSignatures = v ->
                startActivity(new Intent(requireContext(), com.pdfreader.app.SignatureManagementActivity.class));

        view.findViewById(R.id.card_reading_preferences).setOnClickListener(v -> showReadingPreferencesDialog());
        view.findViewById(R.id.card_signature_management).setOnClickListener(openSignatures);
        View activeCard = view.findViewById(R.id.card_active_signature);
        if (activeCard != null) activeCard.setOnClickListener(openSignatures);
        View newSignatureBtn = view.findViewById(R.id.btn_new_signature);
        if (newSignatureBtn != null) newSignatureBtn.setOnClickListener(openSignatures);
        view.findViewById(R.id.card_notifications).setOnClickListener(v -> showNotificationsDialog());
        view.findViewById(R.id.card_about).setOnClickListener(v -> showAboutDialog());
        view.findViewById(R.id.card_help).setOnClickListener(v -> openHelpEmail());
        view.findViewById(R.id.sign_out_button).setOnClickListener(v -> handleSignOut());
    }

    private void loadActiveSignature() {
        if (activeSignaturePreview == null || signatureManager == null) return;

        String path = signatureManager.getActiveSignaturePath();
        if (path == null) {
            activeSignaturePreview.setVisibility(View.GONE);
            activeSignaturePreview.setImageDrawable(null);
            if (activeSignaturePlaceholder != null) {
                activeSignaturePlaceholder.setVisibility(View.VISIBLE);
            }
            if (activeSignatureName != null) {
                activeSignatureName.setText("No signature selected");
            }
            return;
        }

        Bitmap bitmap = signatureManager.loadSignature(path);
        if (bitmap != null) {
            activeSignaturePreview.setImageBitmap(bitmap);
            activeSignaturePreview.setVisibility(View.VISIBLE);
            if (activeSignaturePlaceholder != null) {
                activeSignaturePlaceholder.setVisibility(View.GONE);
            }
            if (activeSignatureName != null) {
                activeSignatureName.setText(signatureManager.getSignatureName(path));
            }
        } else {
            activeSignaturePreview.setVisibility(View.GONE);
            if (activeSignaturePlaceholder != null) {
                activeSignaturePlaceholder.setVisibility(View.VISIBLE);
            }
            if (activeSignatureName != null) {
                activeSignatureName.setText("No signature selected");
            }
        }
    }

    private void showReadingPreferencesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Reading Preferences")
                .setMessage("Daily goal: 60 min / 30 pages\n\nCustom goals coming soon.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showNotificationsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Notifications")
                .setMessage("Notification settings coming soon.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("About")
                .setMessage("PDF Reader\nVersion 1.0.0\n\nA simple, powerful PDF and EPUB reader.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void openHelpEmail() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@pdfreader.app"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "PDF Reader - Help & Support");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSignOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out of Signet?")
                .setPositiveButton("Log out", (dialog, which) -> {
                    SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().clear().apply();
                    Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show();
                    loadProfileData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadProfileData() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String userName = prefs.getString(KEY_USER_NAME, "User");
        String userEmail = prefs.getString(KEY_USER_EMAIL, "reader@versefoutain.com");

        profileName.setText(userName);
        profileEmail.setText(userEmail);

        int dailyTimeGoal = 60;
        int dailyPagesGoal = 30;
        dailyGoalsText.setText(dailyTimeGoal + " min / " + dailyPagesGoal + " pages daily");
    }

    private void loadStats() {
        executorService.execute(() -> {
            try {
                List<PdfBook> allBooks = historyManager.getHistory();
                int totalBooks = allBooks.size();
                int totalPagesRead = 0;

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

                int streak = calculateStreak(allBooks);
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
        return Math.min(books.size(), 30);
    }
}
