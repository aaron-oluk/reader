package com.pdfreader.app;

import android.content.Context;
import android.content.SharedPreferences;

public class ReadingProgressManager {
    private static final String PREFS_NAME = "reading_progress";
    private final SharedPreferences prefs;

    public ReadingProgressManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveProgress(String filePath, int scrollPosition) {
        prefs.edit().putInt(scrollKey(filePath), scrollPosition).apply();
    }

    public int getProgress(String filePath) {
        return prefs.getInt(scrollKey(filePath), 0);
    }

    public void savePageCount(String filePath, int pageCount) {
        prefs.edit().putInt(pageCountKey(filePath), pageCount).apply();
    }

    public int getPageCount(String filePath) {
        return prefs.getInt(pageCountKey(filePath), 0);
    }

    /**
     * Returns "not_started", "reading", or "finished" based on saved progress.
     * Requires savePageCount to have been called at least once for this file.
     */
    public String getReadingStatus(String filePath) {
        int totalPages = getPageCount(filePath);
        if (totalPages <= 0) return "not_started";
        int currentPage = getProgress(filePath) / 1000;
        if (currentPage <= 0) return "not_started";
        if (currentPage >= totalPages * 0.95f) return "finished";
        return "reading";
    }

    private String scrollKey(String filePath) {
        return "scroll_" + filePath.hashCode();
    }

    private String pageCountKey(String filePath) {
        return "pages_" + filePath.hashCode();
    }
}
