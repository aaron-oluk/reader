package com.pdfreader.app.models;

public class ReadingSession {
    private String id;
    private String bookId;
    private long startTime;
    private long endTime;
    private int pagesRead;
    private int startPage;
    private int endPage;
    private String notes;

    public ReadingSession(String id, String bookId) {
        this.id = id;
        this.bookId = bookId;
        this.startTime = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getPagesRead() {
        return pagesRead;
    }

    public void setPagesRead(int pagesRead) {
        this.pagesRead = pagesRead;
    }

    public int getStartPage() {
        return startPage;
    }

    public void setStartPage(int startPage) {
        this.startPage = startPage;
    }

    public int getEndPage() {
        return endPage;
    }

    public void setEndPage(int endPage) {
        this.endPage = endPage;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getDurationMinutes() {
        if (endTime == 0) {
            return (System.currentTimeMillis() - startTime) / (1000 * 60);
        }
        return (endTime - startTime) / (1000 * 60);
    }
}
