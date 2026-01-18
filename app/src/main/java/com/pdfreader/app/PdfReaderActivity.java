package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdfreader.app.HistoryManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PdfReaderActivity extends AppCompatActivity {

    private static final String TAG = "PdfReaderActivity";

    private RecyclerView recyclerView;
    private CoordinatorLayout coordinatorLayout;
    private String pdfPath;
    private String pdfTitle;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private ReadingProgressManager progressManager;
    private HistoryManager historyManager;
    private NotesManager notesManager;
    private BookmarkManager bookmarkManager;
    private PdfPageAdapter pdfPageAdapter;

    // UI Elements
    private TextView toolbarTitle;
    private TextView toolbarSubtitle;
    private TextView pageIndicator;
    private View topToolbar;


    // Page tracking
    private int pageCount = 0;
    private int currentPage = 1;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideIndicatorRunnable;
    private LinearLayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_reader);

        initViews();
        setupListeners();

        progressManager = new ReadingProgressManager(this);
        historyManager = new HistoryManager(this);
        notesManager = new NotesManager(this);
        bookmarkManager = new BookmarkManager(this);

        pdfPath = getIntent().getStringExtra("PDF_PATH");
        pdfTitle = getIntent().getStringExtra("PDF_TITLE");
        
        // Save to history when PDF is opened
        if (pdfPath != null && pdfTitle != null && !pdfPath.isEmpty()) {
            historyManager.addToHistory(pdfTitle, pdfPath);
        }

        if (pdfTitle != null) {
            toolbarTitle.setText(pdfTitle);
        }

        if (pdfPath != null && !pdfPath.isEmpty()) {
            displayPdf();
        } else {
            Toast.makeText(this, "Error: PDF path not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        recyclerView = findViewById(R.id.pdf_recycler_view);
        toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarSubtitle = findViewById(R.id.toolbar_subtitle);
        pageIndicator = findViewById(R.id.page_indicator);
        topToolbar = findViewById(R.id.top_toolbar);
        
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
    }

    private void setupListeners() {
        // Back button
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Notes button
        ImageButton btnNotes = findViewById(R.id.btn_notes);
        btnNotes.setOnClickListener(v -> showNotesDialog());
        
        // Bookmark button
        ImageButton btnBookmark = findViewById(R.id.btn_bookmark);
        btnBookmark.setOnClickListener(v -> addBookmark());
        
        // Share button
        ImageButton btnShare = findViewById(R.id.btn_share);
        btnShare.setOnClickListener(v -> shareDocument());
        
        // Search button
        ImageButton btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> {
            Toast.makeText(this, "Search feature coming soon", Toast.LENGTH_SHORT).show();
        });

        // Scroll listener for page tracking
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateCurrentPageFromScroll();
            }
        });
    }


    private void shareDocument() {
        if (pdfPath == null) return;

        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");

            if (pdfPath.startsWith("content://")) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(pdfPath));
            } else {
                File file = new File(pdfPath);
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        file
                );
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            }

            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share document", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayPdf() {
        try {
            File file;
            if (pdfPath.startsWith("content://")) {
                Uri uri = Uri.parse(pdfPath);
                file = new File(getCacheDir(), "temp.pdf");
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    Toast.makeText(this, "Cannot open PDF file", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                FileOutputStream outputStream = new FileOutputStream(file);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();
            } else {
                file = new File(pdfPath);
            }

            if (!file.exists()) {
                Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);

            pageCount = pdfRenderer.getPageCount();

            // Update UI
            toolbarSubtitle.setText(pageCount + " pages");

            // Setup RecyclerView adapter with lazy loading
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            Log.d(TAG, "Creating adapter for " + pageCount + " pages, screen width: " + screenWidth);
            pdfPageAdapter = new PdfPageAdapter(this, pdfRenderer, screenWidth);
            pdfPageAdapter.setPdfPath(pdfPath);
            pdfPageAdapter.setOnHighlightListener(new com.pdfreader.app.views.HighlightOverlayView.OnHighlightListener() {
                @Override
                public void onLineSelected(int page, float yPosition, float x, float y, float width, float height) {
                    showLineSelectionDialog(page, yPosition, x, y, width, height);
                }

                @Override
                public void onHighlightTapped(com.pdfreader.app.views.HighlightOverlayView.Highlight highlight) {
                    showHighlightNoteDialog(highlight);
                }
            });
            recyclerView.setAdapter(pdfPageAdapter);
            Log.d(TAG, "Adapter set, item count: " + pdfPageAdapter.getItemCount());

            // Restore scroll position after layout
            recyclerView.post(() -> {
                int savedPage = progressManager.getProgress(pdfPath) / 1000; // Convert to page number
                if (savedPage > 0 && savedPage < pageCount) {
                    layoutManager.scrollToPositionWithOffset(savedPage, 0);
                }
                updateCurrentPageFromScroll();
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading PDF", e);
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private void updateCurrentPageFromScroll() {
        if (pageCount == 0 || layoutManager == null) return;

        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        int newPage = firstVisiblePosition + 1;

        if (newPage > 0 && newPage != currentPage) {
            currentPage = newPage;
            updatePageIndicators();
        }
    }

    private void updatePageIndicators() {
        toolbarSubtitle.setText(getString(R.string.page_info, currentPage, pageCount));
    }

    private void showPageIndicator() {
        pageIndicator.setText(getString(R.string.page_info, currentPage, pageCount));
        pageIndicator.setVisibility(View.VISIBLE);

        if (hideIndicatorRunnable != null) {
            handler.removeCallbacks(hideIndicatorRunnable);
        }

        hideIndicatorRunnable = () -> pageIndicator.setVisibility(View.GONE);
        handler.postDelayed(hideIndicatorRunnable, 2000);
    }

    private void showNotesDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Notes for Page " + currentPage);
        
        // Get existing notes for current page
        List<NotesManager.Note> pageNotes = notesManager.getNotesForPage(pdfPath, currentPage);
        
        if (pageNotes.isEmpty()) {
            // Show add note dialog
            showAddNoteDialog();
        } else {
            // Show list of notes with option to add new
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(48, 24, 48, 24);
            
            for (NotesManager.Note note : pageNotes) {
                android.widget.TextView noteView = new android.widget.TextView(this);
                noteView.setText(note.text);
                noteView.setPadding(0, 8, 0, 8);
                noteView.setTextAppearance(android.R.style.TextAppearance_Medium);
                layout.addView(noteView);
            }
            
            builder.setView(layout);
            builder.setPositiveButton("Add Note", (dialog, which) -> showAddNoteDialog());
            builder.setNegativeButton("Close", null);
            builder.show();
        }
    }
    
    private void showAddNoteDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Add Note - Page " + currentPage);
        
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter your note...");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String noteText = input.getText().toString().trim();
            if (!noteText.isEmpty()) {
                // Calculate approximate Y position (middle of visible area)
                float yPosition = 0.5f; // Default to middle
                if (layoutManager != null) {
                    View firstView = layoutManager.findViewByPosition(currentPage - 1);
                    if (firstView != null) {
                        // Could calculate actual scroll position here if needed
                    }
                }
                notesManager.addNote(pdfPath, currentPage, noteText, yPosition);
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showLineSelectionDialog(int page, float yPosition, float x, float y, float width, float height) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Action");
        builder.setMessage("Long-press detected on line. What would you like to do?");

        builder.setPositiveButton("Highlight & Note", (dialog, which) -> {
            showAddHighlightNoteDialog(page, yPosition, x, y, width, height);
        });

        builder.setNeutralButton("Bookmark", (dialog, which) -> {
            bookmarkManager.addBookmark(pdfPath, page, "Line " + (int)(yPosition * 100) + "%", yPosition);
            Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAddHighlightNoteDialog(int page, float yPosition, float x, float y, float width, float height) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Add Highlight & Note - Page " + (page + 1));

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter your note (optional)...");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String noteText = input.getText().toString().trim();
            // Allow empty note (just highlight)
            if (noteText.isEmpty()) {
                noteText = "Highlighted line";
            }
            // Store normalized coordinates (x, y, width, height are already normalized 0.0-1.0)
            notesManager.addHighlight(pdfPath, page, noteText, yPosition, x, y, width, height);
            Toast.makeText(this, "Highlight saved", Toast.LENGTH_SHORT).show();
            // Refresh highlights
            refreshHighlights();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showHighlightNoteDialog(com.pdfreader.app.views.HighlightOverlayView.Highlight highlight) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Note on Page " + (highlight.page + 1));

        if (highlight.note != null && !highlight.note.isEmpty()) {
            builder.setMessage(highlight.note);
            builder.setPositiveButton("Edit", (dialog, which) -> {
                showEditHighlightNoteDialog(highlight);
            });
            builder.setNeutralButton("Delete", (dialog, which) -> {
                notesManager.deleteNote(pdfPath, highlight.id);
                Toast.makeText(this, "Highlight removed", Toast.LENGTH_SHORT).show();
                refreshHighlights();
            });
        } else {
            builder.setMessage("No note attached");
            builder.setPositiveButton("Add Note", (dialog, which) -> {
                showEditHighlightNoteDialog(highlight);
            });
            builder.setNeutralButton("Delete", (dialog, which) -> {
                notesManager.deleteNote(pdfPath, highlight.id);
                Toast.makeText(this, "Highlight removed", Toast.LENGTH_SHORT).show();
                refreshHighlights();
            });
        }

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showEditHighlightNoteDialog(com.pdfreader.app.views.HighlightOverlayView.Highlight highlight) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Note");

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(highlight.note);
        input.setHint("Enter your note...");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String noteText = input.getText().toString().trim();
            notesManager.updateNote(pdfPath, highlight.id, noteText);
            Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show();
            refreshHighlights();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void refreshHighlights() {
        if (pdfPageAdapter != null) {
            // Notify adapter to refresh highlights
            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            int lastVisible = layoutManager.findLastVisibleItemPosition();
            for (int i = firstVisible; i <= lastVisible; i++) {
                if (i >= 0 && i < pageCount) {
                    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
                    if (holder instanceof PdfPageAdapter.PageViewHolder) {
                        // Trigger rebind to refresh highlights
                        pdfPageAdapter.notifyItemChanged(i);
                    }
                }
            }
        }
    }

    private void addBookmark() {
        if (pdfPath == null) return;
        
        // Check if current page already has a bookmark
        if (bookmarkManager.hasBookmark(pdfPath, currentPage)) {
            // Show existing bookmark dialog
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Bookmark Already Exists");
            builder.setMessage("Page " + currentPage + " is already bookmarked. Would you like to remove it?");
            builder.setPositiveButton("Remove", (dialog, which) -> {
                List<BookmarkManager.Bookmark> bookmarks = bookmarkManager.getBookmarks(pdfPath);
                for (BookmarkManager.Bookmark bookmark : bookmarks) {
                    if (bookmark.page == currentPage) {
                        bookmarkManager.deleteBookmark(pdfPath, bookmark.id);
                        Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            // Add new bookmark
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Add Bookmark");
            
            android.widget.EditText input = new android.widget.EditText(this);
            input.setHint("Optional label (e.g., 'Important section')");
            input.setPadding(48, 32, 48, 32);
            builder.setView(input);
            
            builder.setPositiveButton("Save", (dialog, which) -> {
                String label = input.getText().toString().trim();
                float scrollPosition = 0.0f; // Could calculate actual scroll position
                bookmarkManager.addBookmark(pdfPath, currentPage, label, scrollPosition);
                Toast.makeText(this, "Bookmark added to page " + currentPage, Toast.LENGTH_SHORT).show();
            });
            
            builder.setNegativeButton("Cancel", null);
            builder.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pdfPath != null && layoutManager != null) {
            // Save current page as progress
            int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
            progressManager.saveProgress(pdfPath, firstVisiblePosition * 1000);
            
            // Also save as bookmark (last reading position)
            if (currentPage > 0) {
                BookmarkManager.Bookmark lastBookmark = bookmarkManager.getLastBookmark(pdfPath);
                // Only update if this is a new position or significantly different
                if (lastBookmark == null || lastBookmark.page != currentPage) {
                    bookmarkManager.addBookmark(pdfPath, currentPage, "Last read", 0.0f);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup adapter and bitmaps
        if (pdfPageAdapter != null) {
            pdfPageAdapter.cleanup();
        }
        
        try {
            if (pdfRenderer != null) {
                pdfRenderer.close();
            }
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing PDF renderer", e);
        }

        if (hideIndicatorRunnable != null) {
            handler.removeCallbacks(hideIndicatorRunnable);
        }
        
        // Clear cache
        File cacheFile = new File(getCacheDir(), "temp.pdf");
        if (cacheFile.exists()) {
            if (!cacheFile.delete()) {
                Log.w(TAG, "Failed to delete cache file");
            }
        }
    }
}
