package com.pdfreader.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages bookmarks for PDF documents
 * Bookmarks mark specific pages where user stopped reading
 */
public class BookmarkManager {
    private static final String TAG = "BookmarkManager";
    private static final String PREFS_NAME = "pdf_bookmarks";
    private final SharedPreferences prefs;

    public static class Bookmark {
        public String id;
        public int page;
        public String label; // Optional label/note
        public long timestamp;
        public float scrollPosition; // Scroll position on page (0.0 to 1.0)

        public Bookmark(String id, int page, String label, long timestamp, float scrollPosition) {
            this.id = id;
            this.page = page;
            this.label = label;
            this.timestamp = timestamp;
            this.scrollPosition = scrollPosition;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("page", page);
            json.put("label", label);
            json.put("timestamp", timestamp);
            json.put("scrollPosition", scrollPosition);
            return json;
        }

        public static Bookmark fromJson(JSONObject json) throws JSONException {
            return new Bookmark(
                json.getString("id"),
                json.getInt("page"),
                json.optString("label", ""),
                json.getLong("timestamp"),
                (float) json.optDouble("scrollPosition", 0.0)
            );
        }
    }

    public BookmarkManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Add a bookmark at current position
     */
    public void addBookmark(String pdfPath, int page, String label, float scrollPosition) {
        try {
            List<Bookmark> bookmarks = getBookmarks(pdfPath);
            String id = "bookmark_" + System.currentTimeMillis();
            Bookmark bookmark = new Bookmark(id, page, label, System.currentTimeMillis(), scrollPosition);
            bookmarks.add(bookmark);
            saveBookmarks(pdfPath, bookmarks);
            Log.d(TAG, "Bookmark added for PDF: " + pdfPath + " on page " + page);
        } catch (Exception e) {
            Log.e(TAG, "Error adding bookmark", e);
        }
    }

    /**
     * Get all bookmarks for a PDF
     */
    public List<Bookmark> getBookmarks(String pdfPath) {
        List<Bookmark> bookmarks = new ArrayList<>();
        try {
            String key = getKey(pdfPath);
            String bookmarksJson = prefs.getString(key, "[]");
            JSONArray jsonArray = new JSONArray(bookmarksJson);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                Bookmark bookmark = Bookmark.fromJson(jsonArray.getJSONObject(i));
                bookmarks.add(bookmark);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting bookmarks", e);
        }
        return bookmarks;
    }

    /**
     * Get the most recent bookmark (last reading position)
     */
    public Bookmark getLastBookmark(String pdfPath) {
        List<Bookmark> bookmarks = getBookmarks(pdfPath);
        if (bookmarks.isEmpty()) {
            return null;
        }
        // Return the most recent bookmark
        Bookmark last = bookmarks.get(0);
        for (Bookmark bookmark : bookmarks) {
            if (bookmark.timestamp > last.timestamp) {
                last = bookmark;
            }
        }
        return last;
    }

    /**
     * Delete a bookmark
     */
    public void deleteBookmark(String pdfPath, String bookmarkId) {
        try {
            List<Bookmark> bookmarks = getBookmarks(pdfPath);
            bookmarks.removeIf(bookmark -> bookmark.id.equals(bookmarkId));
            saveBookmarks(pdfPath, bookmarks);
            Log.d(TAG, "Bookmark deleted: " + bookmarkId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting bookmark", e);
        }
    }

    /**
     * Check if a page has a bookmark
     */
    public boolean hasBookmark(String pdfPath, int page) {
        List<Bookmark> bookmarks = getBookmarks(pdfPath);
        for (Bookmark bookmark : bookmarks) {
            if (bookmark.page == page) {
                return true;
            }
        }
        return false;
    }

    private void saveBookmarks(String pdfPath, List<Bookmark> bookmarks) {
        try {
            String key = getKey(pdfPath);
            JSONArray jsonArray = new JSONArray();
            for (Bookmark bookmark : bookmarks) {
                jsonArray.put(bookmark.toJson());
            }
            prefs.edit().putString(key, jsonArray.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving bookmarks", e);
        }
    }

    private String getKey(String pdfPath) {
        return "bookmarks_" + pdfPath.hashCode();
    }
}
