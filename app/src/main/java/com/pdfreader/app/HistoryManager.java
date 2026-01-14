package com.pdfreader.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    private static final String PREFS_NAME = "pdf_reader_prefs";
    private static final String KEY_HISTORY = "pdf_history";
    private static final int MAX_HISTORY_SIZE = 20;

    private final SharedPreferences prefs;

    public HistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void addToHistory(String title, String path) {
        List<PdfBook> history = getHistory();

        // Remove if already exists (to move to top)
        history.removeIf(book -> book.getFilePath().equals(path));

        // Add to beginning
        history.add(0, new PdfBook(title, path, ""));

        // Limit size
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }

        saveHistory(history);
    }

    public List<PdfBook> getHistory() {
        List<PdfBook> history = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, "[]");

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                history.add(new PdfBook(
                        obj.getString("title"),
                        obj.getString("path"),
                        obj.optString("size", "")
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return history;
    }

    private void saveHistory(List<PdfBook> history) {
        JSONArray array = new JSONArray();

        try {
            for (PdfBook book : history) {
                JSONObject obj = new JSONObject();
                obj.put("title", book.getTitle());
                obj.put("path", book.getFilePath());
                obj.put("size", book.getFileSize());
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    public void removeFromHistory(String filePath) {
        List<PdfBook> history = getHistory();
        history.removeIf(book -> book.getFilePath().equals(filePath));
        saveHistory(history);
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }
}
