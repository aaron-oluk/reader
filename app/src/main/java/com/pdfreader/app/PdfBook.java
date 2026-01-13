package com.pdfreader.app;

public class PdfBook {
    private String title;
    private String filePath;
    private String fileSize;

    public PdfBook(String title, String filePath, String fileSize) {
        this.title = title;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }
}
