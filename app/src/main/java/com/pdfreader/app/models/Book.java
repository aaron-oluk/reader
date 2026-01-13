package com.pdfreader.app.models;

public class Book {
    private String id;
    private String title;
    private String author;
    private String coverUrl;
    private int currentPage;
    private int totalPages;
    private String status; // "reading", "to-read", "finished", "paused"
    private long lastReadTime;
    private String filePath;

    public Book(String id, String title, String author, int totalPages) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.totalPages = totalPages;
        this.currentPage = 0;
        this.status = "to-read";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getProgressPercentage() {
        if (totalPages == 0) return 0;
        return (int) ((currentPage * 100.0) / totalPages);
    }
}
