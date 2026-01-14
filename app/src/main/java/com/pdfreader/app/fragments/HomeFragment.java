package com.pdfreader.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.pdfreader.app.HistoryManager;
import com.pdfreader.app.ReadingProgressManager;
import com.pdfreader.app.MainActivityNew;
import com.pdfreader.app.PdfBook;
import com.pdfreader.app.PdfBookAdapter;
import com.pdfreader.app.PdfReaderActivity;
import com.pdfreader.app.PdfThumbnailGenerator;
import com.pdfreader.app.R;
import com.pdfreader.app.SearchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private TextView greetingText;
    private TextView bookTitle;
    private TextView bookAuthor;
    private TextView progressPercentage;
    private TextView pagesLeft;
    private TextView progressCorner;
    private ImageView bookCover;
    private RecyclerView upNextRecycler;
    private LinearLayout emptyState;
    private View progressFill;
    private View currentlyReadingCard;
    private View continueReadingSection;
    private HistoryManager historyManager;
    private ReadingProgressManager readingProgressManager;
    private List<PdfBook> recentBooks = new ArrayList<>();
    private PdfBookAdapter adapter;
    private ExecutorService executorService;
    private Handler mainHandler;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        historyManager = new HistoryManager(requireContext());
        readingProgressManager = new ReadingProgressManager(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

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
        // Take persistable permission
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.e(TAG, "Error taking persistable permission", e);
        }

        String fileName = getFileNameFromUri(uri);
        String path = uri.toString();

        // Add to history
        historyManager.addToHistory(fileName, path);

        Intent intent = new Intent(getActivity(), PdfReaderActivity.class);
        intent.putExtra("PDF_PATH", path);
        intent.putExtra("PDF_TITLE", fileName);
        startActivity(intent);
    }
    
    private String getFileNameFromUri(Uri uri) {
        String fileName = "Document";
        try {
            android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex("_display_name");
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
            // Fallback to path-based name
            try {
                String path = uri.getPath();
                if (path != null) {
                    fileName = path.substring(path.lastIndexOf('/') + 1);
                }
            } catch (Exception ignored) {}
        }
        
        // Remove extension for display
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        return fileName;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentBooks();
        updateCurrentlyReadingCard();
    }

    private void initViews(View view) {
        greetingText = view.findViewById(R.id.greeting_text);
        bookTitle = view.findViewById(R.id.book_title);
        bookAuthor = view.findViewById(R.id.book_author);
        progressPercentage = view.findViewById(R.id.progress_percentage);
        pagesLeft = view.findViewById(R.id.pages_left);
        progressCorner = view.findViewById(R.id.progress_corner);
        progressFill = view.findViewById(R.id.progress_fill);
        bookCover = view.findViewById(R.id.book_cover);
        upNextRecycler = view.findViewById(R.id.up_next_recycler);
        emptyState = view.findViewById(R.id.empty_state);
        currentlyReadingCard = view.findViewById(R.id.currently_reading_card);
        continueReadingSection = view.findViewById(R.id.continue_reading_section);

        if (upNextRecycler != null) {
        upNextRecycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        adapter = new PdfBookAdapter(requireContext(), recentBooks);
        upNextRecycler.setAdapter(adapter);
        }
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

        // Continue reading button
        View continueReadingButton = view.findViewById(R.id.continue_reading_button);
        if (continueReadingButton != null) {
            continueReadingButton.setOnClickListener(v -> {
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
        
        // Menu button - show options for current book
        View menuButton = view.findViewById(R.id.menu_button);
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> {
                if (!recentBooks.isEmpty()) {
                    showBookMenu(recentBooks.get(0));
                }
            });
        }
        
        // View all reading button
        View viewAllReading = view.findViewById(R.id.view_all_reading);
        if (viewAllReading != null) {
            viewAllReading.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivityNew) {
                    ((MainActivityNew) getActivity()).navigateToTab(R.id.navigation_library);
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

        // Filter out books that don't exist and get up to 5 valid recent books
        int added = 0;
        for (PdfBook book : history) {
            if (added >= 5) break;
            
            // Verify file exists
            if (book.getFilePath() != null && fileExists(book.getFilePath())) {
                recentBooks.add(book);
                added++;
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // Show/hide empty state
        if (emptyState != null && upNextRecycler != null) {
            if (recentBooks.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                upNextRecycler.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                upNextRecycler.setVisibility(View.VISIBLE);
            }
        }
        
        // Update currently reading card with cover
        updateCurrentlyReadingCard();
    }
    
    private boolean fileExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        
        try {
            if (filePath.startsWith("content://")) {
                // For content URIs, try to open it
                android.net.Uri uri = android.net.Uri.parse(filePath);
                android.content.ContentResolver resolver = requireContext().getContentResolver();
                try (android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
                    return pfd != null;
                } catch (Exception e) {
                    return false;
                }
            } else {
                // For file paths
                java.io.File file = new java.io.File(filePath);
                return file.exists() && file.isFile();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking file existence: " + filePath, e);
            return false;
        }
    }

    private void updateCurrentlyReadingCard() {
        if (!recentBooks.isEmpty()) {
            PdfBook currentBook = recentBooks.get(0);
            
            // Show the continue reading section
            if (continueReadingSection != null) {
                continueReadingSection.setVisibility(View.VISIBLE);
            }
            
            if (bookTitle != null) {
                bookTitle.setText(currentBook.getTitle());
            }
            if (bookAuthor != null) {
                // Show file size if available
                String authorText = currentBook.getFileSize() != null && !currentBook.getFileSize().isEmpty() 
                    ? currentBook.getFileSize() 
                    : "PDF Document";
                bookAuthor.setText(authorText);
            }
            
            // Update progress with actual data
            updateReadingProgress(currentBook.getFilePath());
            
            // Load PDF thumbnail as book cover
            loadBookCover(currentBook.getFilePath());
        } else {
            // Hide the continue reading section when no books
            if (continueReadingSection != null) {
                continueReadingSection.setVisibility(View.GONE);
            }
        }
    }
    
    private void updateReadingProgress(String pdfPath) {
        if (pdfPath == null || pdfPath.isEmpty()) {
            setProgress(0, 0);
            return;
        }
        
        // Get current page from saved progress
        int savedProgress = readingProgressManager.getProgress(pdfPath);
        int currentPage = savedProgress / 1000; // Convert scroll position to page number
        
        // Get total page count from PDF
        executorService.execute(() -> {
            try {
                int totalPages = getPdfPageCount(pdfPath);
                
                mainHandler.post(() -> {
                    if (totalPages > 0 && currentPage > 0) {
                        int progressPercent = (int) ((currentPage * 100.0f) / totalPages);
                        int pagesRemaining = Math.max(0, totalPages - currentPage);
                        setProgress(progressPercent, pagesRemaining);
                    } else {
                        // No progress yet or can't read PDF
                        setProgress(0, totalPages > 0 ? totalPages : 0);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting PDF page count", e);
                mainHandler.post(() -> setProgress(0, 0));
            }
        });
    }
    
    private int getPdfPageCount(String pdfPath) {
        try {
            android.graphics.pdf.PdfRenderer renderer = null;
            android.os.ParcelFileDescriptor pfd = null;
            
            if (pdfPath.startsWith("content://") || pdfPath.startsWith("file://")) {
                android.net.Uri uri = android.net.Uri.parse(pdfPath);
                pfd = requireContext().getContentResolver().openFileDescriptor(uri, "r");
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
            Log.e(TAG, "Error reading PDF page count", e);
        }
        return 0;
    }
    
    private void setProgress(int progressPercent, int pagesRemaining) {
        String progressText = progressPercent + "% complete";
        
        if (progressPercentage != null) {
            progressPercentage.setText(progressText);
        }
        
        if (progressCorner != null) {
            progressCorner.setText(progressPercent + "%");
        }
        
        if (pagesLeft != null) {
            if (pagesRemaining > 0) {
                pagesLeft.setText(pagesRemaining + " pages left");
            } else {
                pagesLeft.setText("");
            }
        }
        
        // Update progress fill width
        if (progressFill != null && progressFill.getParent() != null) {
            ViewGroup parent = (ViewGroup) progressFill.getParent();
            int parentWidth = parent.getWidth();
            if (parentWidth > 0) {
                ViewGroup.LayoutParams params = progressFill.getLayoutParams();
                params.width = (int) (parentWidth * progressPercent / 100.0f);
                progressFill.setLayoutParams(params);
            } else {
                // Post to measure after layout
                parent.post(() -> {
                    if (progressFill != null && progressFill.getParent() != null && isAdded()) {
                        ViewGroup p = (ViewGroup) progressFill.getParent();
                        int pw = p.getWidth();
                        if (pw > 0) {
                            ViewGroup.LayoutParams p2 = progressFill.getLayoutParams();
                            p2.width = (int) (pw * progressPercent / 100.0f);
                            progressFill.setLayoutParams(p2);
                        }
                    }
                });
            }
        }
    }
    
    private void loadBookCover(String pdfPath) {
        if (bookCover == null || pdfPath == null || pdfPath.isEmpty()) {
            return;
        }
        
        // Show placeholder while loading
        bookCover.setImageResource(R.drawable.placeholder_book);
        
        executorService.execute(() -> {
            try {
                // Get display dimensions for thumbnail
                int maxWidth = 400; // Cover width
                int maxHeight = 560; // Cover height (maintaining aspect ratio)
                
                Bitmap thumbnail = PdfThumbnailGenerator.generateThumbnail(
                    requireContext(),
                    pdfPath,
                    maxWidth,
                    maxHeight
                );
                
                if (thumbnail != null && !thumbnail.isRecycled()) {
                    mainHandler.post(() -> {
                        if (bookCover != null && isAdded()) {
                            bookCover.setImageBitmap(thumbnail);
                            Log.d(TAG, "Book cover loaded successfully");
                        } else {
                            // Fragment detached, recycle bitmap
                            thumbnail.recycle();
                        }
                    });
                } else {
                    Log.w(TAG, "Failed to generate thumbnail for: " + pdfPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading book cover", e);
            }
        });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    private void showBookMenu(PdfBook book) {
        String[] options = {"Remove from History", "Share", "Cancel"};
        
        new AlertDialog.Builder(requireContext())
            .setTitle(book.getTitle())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Remove from History
                        removeFromHistory(book);
                        break;
                    case 1: // Share
                        shareBook(book);
                        break;
                    // case 2: Cancel - do nothing
                }
            })
            .show();
    }
    
    private void removeFromHistory(PdfBook book) {
        historyManager.removeFromHistory(book.getFilePath());
        loadRecentBooks();
        Toast.makeText(requireContext(), "Removed from history", Toast.LENGTH_SHORT).show();
    }
    
    private void shareBook(PdfBook book) {
        try {
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            
            if (book.getFilePath().startsWith("content://")) {
                shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(book.getFilePath()));
            } else {
                java.io.File file = new java.io.File(book.getFilePath());
                if (file.exists()) {
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".provider",
                        file
                    );
                    shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                }
            }
            
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing book", e);
            Toast.makeText(requireContext(), "Unable to share document", Toast.LENGTH_SHORT).show();
        }
    }
}
