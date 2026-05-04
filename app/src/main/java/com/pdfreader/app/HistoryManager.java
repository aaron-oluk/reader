package com.pdfreader.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryManager {
    private static final String PREFS_NAME = "pdf_reader_prefs";
    private static final String KEY_HISTORY = "pdf_history";
    private static final int MAX_HISTORY_SIZE = 20;
    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final SharedPreferences prefs;

    public HistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addToHistory(String title, String path) {
        List<HistoryEntry> history = getEntries();

        // Update existing entry's lastRead timestamp, or insert new at top
        HistoryEntry existing = null;
        for (HistoryEntry e : history) {
            if (e.path.equals(path)) { existing = e; break; }
        }
        if (existing != null) {
            history.remove(existing);
            existing.lastRead = System.currentTimeMillis();
            history.add(0, existing);
        } else {
            history.add(0, new HistoryEntry(title, path, "", System.currentTimeMillis()));
        }

        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }
        saveEntries(history);
    }

    public List<PdfBook> getHistory() {
        List<PdfBook> result = new ArrayList<>();
        for (HistoryEntry e : getEntries()) {
            result.add(new PdfBook(e.title, e.path, e.size));
        }
        return result;
    }

    /** Returns the timestamp (ms) of when the given path was last opened, or 0. */
    public long getLastRead(String path) {
        for (HistoryEntry e : getEntries()) {
            if (e.path.equals(path)) return e.lastRead;
        }
        return 0L;
    }

    /**
     * Returns a set of calendar-day strings ("yyyy-MM-dd") on which any document was opened.
     * Used to calculate reading streaks.
     */
    public Set<String> getReadingDays() {
        Set<String> days = new HashSet<>();
        for (HistoryEntry e : getEntries()) {
            if (e.lastRead > 0) {
                days.add(DAY_FORMAT.format(new Date(e.lastRead)));
            }
        }
        return days;
    }

    /**
     * Counts the current reading streak: consecutive calendar days ending today (or yesterday)
     * on which at least one document was opened.
     */
    public int calculateStreak() {
        Set<String> days = getReadingDays();
        if (days.isEmpty()) return 0;

        Calendar cal = Calendar.getInstance();
        int streak = 0;

        // Start from today; if today has no reading, check yesterday as the seed
        String today = DAY_FORMAT.format(cal.getTime());
        if (!days.contains(today)) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            if (!days.contains(DAY_FORMAT.format(cal.getTime()))) return 0;
        }

        while (days.contains(DAY_FORMAT.format(cal.getTime()))) {
            streak++;
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    /**
     * Returns the paths of books opened since the given timestamp.
     */
    public List<String> getPathsOpenedSince(long sinceMs) {
        List<String> paths = new ArrayList<>();
        for (HistoryEntry e : getEntries()) {
            if (e.lastRead >= sinceMs) paths.add(e.path);
        }
        return paths;
    }

    public void removeFromHistory(String filePath) {
        List<HistoryEntry> history = getEntries();
        history.removeIf(e -> e.path.equals(filePath));
        saveEntries(history);
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private List<HistoryEntry> getEntries() {
        List<HistoryEntry> entries = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                entries.add(new HistoryEntry(
                        obj.getString("title"),
                        obj.getString("path"),
                        obj.optString("size", ""),
                        obj.optLong("lastRead", 0L)
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return entries;
    }

    private void saveEntries(List<HistoryEntry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (HistoryEntry e : entries) {
                JSONObject obj = new JSONObject();
                obj.put("title", e.title);
                obj.put("path", e.path);
                obj.put("size", e.size);
                obj.put("lastRead", e.lastRead);
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    private static class HistoryEntry {
        String title, path, size;
        long lastRead;
        HistoryEntry(String t, String p, String s, long lr) {
            title = t; path = p; size = s; lastRead = lr;
        }
    }
}
