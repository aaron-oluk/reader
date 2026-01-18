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
 * Manages notes for PDF documents
 * Notes are stored per PDF file path in SharedPreferences
 */
public class NotesManager {
    private static final String TAG = "NotesManager";
    private static final String PREFS_NAME = "pdf_notes";
    private final SharedPreferences prefs;

    public static class Note {
        public String id;
        public int page;
        public String text;
        public long timestamp;
        public float yPosition; // Position on page (0.0 to 1.0)
        public float x; // X coordinate of highlight
        public float y; // Y coordinate of highlight
        public float width; // Width of highlight
        public float height; // Height of highlight
        public boolean isHighlight; // Whether this is a highlight with note

        public Note(String id, int page, String text, long timestamp, float yPosition) {
            this.id = id;
            this.page = page;
            this.text = text;
            this.timestamp = timestamp;
            this.yPosition = yPosition;
            this.isHighlight = false;
        }

        public Note(String id, int page, String text, long timestamp, float yPosition, 
                   float x, float y, float width, float height) {
            this.id = id;
            this.page = page;
            this.text = text;
            this.timestamp = timestamp;
            this.yPosition = yPosition;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.isHighlight = true;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("page", page);
            json.put("text", text);
            json.put("timestamp", timestamp);
            json.put("yPosition", yPosition);
            json.put("isHighlight", isHighlight);
            if (isHighlight) {
                json.put("x", x);
                json.put("y", y);
                json.put("width", width);
                json.put("height", height);
            }
            return json;
        }

        public static Note fromJson(JSONObject json) throws JSONException {
            boolean isHighlight = json.optBoolean("isHighlight", false);
            if (isHighlight) {
                return new Note(
                    json.getString("id"),
                    json.getInt("page"),
                    json.getString("text"),
                    json.getLong("timestamp"),
                    (float) json.getDouble("yPosition"),
                    (float) json.getDouble("x"),
                    (float) json.getDouble("y"),
                    (float) json.getDouble("width"),
                    (float) json.getDouble("height")
                );
            } else {
                return new Note(
                    json.getString("id"),
                    json.getInt("page"),
                    json.getString("text"),
                    json.getLong("timestamp"),
                    (float) json.getDouble("yPosition")
                );
            }
        }
    }

    public NotesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Add a note for a PDF
     */
    public void addNote(String pdfPath, int page, String text, float yPosition) {
        try {
            List<Note> notes = getNotes(pdfPath);
            String id = "note_" + System.currentTimeMillis();
            Note note = new Note(id, page, text, System.currentTimeMillis(), yPosition);
            notes.add(note);
            saveNotes(pdfPath, notes);
            Log.d(TAG, "Note added for PDF: " + pdfPath + " on page " + page);
        } catch (Exception e) {
            Log.e(TAG, "Error adding note", e);
        }
    }

    /**
     * Add a highlight with note at specific coordinates
     */
    public void addHighlight(String pdfPath, int page, String text, float yPosition, 
                            float x, float y, float width, float height) {
        try {
            List<Note> notes = getNotes(pdfPath);
            String id = "highlight_" + System.currentTimeMillis();
            Note note = new Note(id, page, text, System.currentTimeMillis(), yPosition, 
                               x, y, width, height);
            notes.add(note);
            saveNotes(pdfPath, notes);
            Log.d(TAG, "Highlight added for PDF: " + pdfPath + " on page " + page);
        } catch (Exception e) {
            Log.e(TAG, "Error adding highlight", e);
        }
    }

    /**
     * Get all notes for a PDF
     */
    public List<Note> getNotes(String pdfPath) {
        List<Note> notes = new ArrayList<>();
        try {
            String key = getKey(pdfPath);
            String notesJson = prefs.getString(key, "[]");
            JSONArray jsonArray = new JSONArray(notesJson);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                Note note = Note.fromJson(jsonArray.getJSONObject(i));
                notes.add(note);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting notes", e);
        }
        return notes;
    }

    /**
     * Get notes for a specific page
     */
    public List<Note> getNotesForPage(String pdfPath, int page) {
        List<Note> allNotes = getNotes(pdfPath);
        List<Note> pageNotes = new ArrayList<>();
        for (Note note : allNotes) {
            if (note.page == page) {
                pageNotes.add(note);
            }
        }
        return pageNotes;
    }

    /**
     * Get highlights for a specific page
     */
    public List<Note> getHighlightsForPage(String pdfPath, int page) {
        List<Note> allNotes = getNotes(pdfPath);
        List<Note> highlights = new ArrayList<>();
        for (Note note : allNotes) {
            if (note.page == page && note.isHighlight) {
                highlights.add(note);
            }
        }
        return highlights;
    }

    /**
     * Delete a note
     */
    public void deleteNote(String pdfPath, String noteId) {
        try {
            List<Note> notes = getNotes(pdfPath);
            notes.removeIf(note -> note.id.equals(noteId));
            saveNotes(pdfPath, notes);
            Log.d(TAG, "Note deleted: " + noteId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting note", e);
        }
    }

    /**
     * Update a note
     */
    public void updateNote(String pdfPath, String noteId, String newText) {
        try {
            List<Note> notes = getNotes(pdfPath);
            for (Note note : notes) {
                if (note.id.equals(noteId)) {
                    note.text = newText;
                    note.timestamp = System.currentTimeMillis();
                    break;
                }
            }
            saveNotes(pdfPath, notes);
            Log.d(TAG, "Note updated: " + noteId);
        } catch (Exception e) {
            Log.e(TAG, "Error updating note", e);
        }
    }

    private void saveNotes(String pdfPath, List<Note> notes) {
        try {
            String key = getKey(pdfPath);
            JSONArray jsonArray = new JSONArray();
            for (Note note : notes) {
                jsonArray.put(note.toJson());
            }
            prefs.edit().putString(key, jsonArray.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving notes", e);
        }
    }

    private String getKey(String pdfPath) {
        return "notes_" + pdfPath.hashCode();
    }
}
