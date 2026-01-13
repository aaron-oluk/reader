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
        String key = getKey(filePath);
        prefs.edit().putInt(key, scrollPosition).apply();
    }

    public int getProgress(String filePath) {
        String key = getKey(filePath);
        return prefs.getInt(key, 0);
    }

    private String getKey(String filePath) {
        // Use hash of file path as key to avoid issues with special characters
        return "scroll_" + filePath.hashCode();
    }
}
