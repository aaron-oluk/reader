package com.pdfreader.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manages app-specific file storage and saving operations
 * Creates folders automatically and handles file saving with proper permissions
 * 
 * NOTE: This is ONLY for saving NEW PDFs created by the app (signed, merged, image-to-PDF).
 * For reading existing PDFs, the app uses the original file location and tracks
 * reading progress/notes/bookmarks in SharedPreferences (not file system).
 */
public class FileManager {
    private static final String TAG = "FileManager";
    private static final String APP_FOLDER_NAME = "PDFReader";
    private static final String PDFS_SUBFOLDER = "PDFs";
    private static final String SIGNATURES_SUBFOLDER = "Signatures";
    
    private Context context;
    private File appFolder;
    private File pdfsFolder;
    private File signaturesFolder;
    
    public FileManager(Context context) {
        this.context = context;
        initializeFolders();
    }
    
    /**
     * Initialize all app folders on first use
     */
    private void initializeFolders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses scoped storage
            // Folders are created in app-specific external storage
            appFolder = context.getExternalFilesDir(APP_FOLDER_NAME);
            if (appFolder != null) {
                pdfsFolder = new File(appFolder, PDFS_SUBFOLDER);
                signaturesFolder = new File(appFolder, SIGNATURES_SUBFOLDER);
            }
        } else {
            // Android 9 and below - use public Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            appFolder = new File(downloadsDir, APP_FOLDER_NAME);
            pdfsFolder = new File(appFolder, PDFS_SUBFOLDER);
            signaturesFolder = new File(appFolder, SIGNATURES_SUBFOLDER);
        }
        
        // Create folders if they don't exist
        createFoldersIfNeeded();
    }
    
    /**
     * Create all necessary folders
     */
    private void createFoldersIfNeeded() {
        try {
            if (appFolder != null && !appFolder.exists()) {
                boolean created = appFolder.mkdirs();
                Log.d(TAG, "App folder created: " + appFolder.getAbsolutePath() + " - " + created);
            }
            
            if (pdfsFolder != null && !pdfsFolder.exists()) {
                boolean created = pdfsFolder.mkdirs();
                Log.d(TAG, "PDFs folder created: " + pdfsFolder.getAbsolutePath() + " - " + created);
            }
            
            if (signaturesFolder != null && !signaturesFolder.exists()) {
                boolean created = signaturesFolder.mkdirs();
                Log.d(TAG, "Signatures folder created: " + signaturesFolder.getAbsolutePath() + " - " + created);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating folders", e);
        }
    }
    
    /**
     * Get the app's main folder path
     */
    public String getAppFolderPath() {
        return appFolder != null ? appFolder.getAbsolutePath() : null;
    }
    
    /**
     * Get the PDFs folder path
     */
    public String getPdfsFolderPath() {
        return pdfsFolder != null ? pdfsFolder.getAbsolutePath() : null;
    }
    
    /**
     * Get the signatures folder path
     */
    public String getSignaturesFolderPath() {
        return signaturesFolder != null ? signaturesFolder.getAbsolutePath() : null;
    }
    
    /**
     * Save a PDF file using MediaStore (Android 10+) or direct file (Android 9-)
     * Returns the saved file path or null if failed
     */
    public String savePdf(byte[] pdfData, String fileName) {
        if (pdfData == null || pdfData.length == 0) {
            Log.e(TAG, "Cannot save empty PDF data");
            return null;
        }
        
        if (fileName == null || fileName.trim().isEmpty()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            fileName = "document_" + timestamp + ".pdf";
        }
        
        // Ensure .pdf extension
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            fileName += ".pdf";
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                return savePdfWithMediaStore(pdfData, fileName);
            } else {
                // Use direct file for Android 9 and below
                return savePdfDirect(pdfData, fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving PDF", e);
            return null;
        }
    }
    
    /**
     * Save PDF using MediaStore (Android 10+)
     */
    private String savePdfWithMediaStore(byte[] pdfData, String fileName) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + APP_FOLDER_NAME + "/" + PDFS_SUBFOLDER);
        
        Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry");
            return null;
        }
        
        try (OutputStream outputStream = resolver.openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(pdfData);
                outputStream.flush();
                Log.d(TAG, "PDF saved via MediaStore: " + uri.toString());
                return uri.toString();
            }
        }
        
        return null;
    }
    
    /**
     * Save PDF directly to file (Android 9 and below)
     */
    private String savePdfDirect(byte[] pdfData, String fileName) throws IOException {
        if (pdfsFolder == null || !pdfsFolder.exists()) {
            createFoldersIfNeeded();
        }
        
        // Handle duplicate file names
        File pdfFile = new File(pdfsFolder, fileName);
        int counter = 1;
        String baseName = fileName.substring(0, fileName.length() - 4); // Remove .pdf
        while (pdfFile.exists()) {
            fileName = baseName + "_" + counter + ".pdf";
            pdfFile = new File(pdfsFolder, fileName);
            counter++;
        }
        
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            fos.write(pdfData);
            fos.flush();
            Log.d(TAG, "PDF saved directly: " + pdfFile.getAbsolutePath());
            return pdfFile.getAbsolutePath();
        }
    }
    
    /**
     * Get a file object for saving PDFs (for compatibility with existing code)
     */
    public File getPdfFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            fileName = "document_" + timestamp + ".pdf";
        }
        
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            fileName += ".pdf";
        }
        
        if (pdfsFolder == null || !pdfsFolder.exists()) {
            createFoldersIfNeeded();
        }
        
        // Handle duplicate file names
        File pdfFile = new File(pdfsFolder, fileName);
        int counter = 1;
        String baseName = fileName.substring(0, fileName.length() - 4);
        while (pdfFile.exists()) {
            fileName = baseName + "_" + counter + ".pdf";
            pdfFile = new File(pdfsFolder, fileName);
            counter++;
        }
        
        return pdfFile;
    }
    
    /**
     * Ensure all folders are created (call this on app startup)
     */
    public void ensureFoldersExist() {
        createFoldersIfNeeded();
    }
}
