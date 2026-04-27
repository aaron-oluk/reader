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
 * Manages app-specific file storage and saving operations.
 * Creates a categorised folder structure on first launch and routes
 * each file type to the correct sub-folder.
 *
 * Folder layout (inside Downloads/PDFReader/ or app-specific external storage):
 *   PDFReader/
 *   ├── Signed/       – PDFs with signatures added
 *   ├── Merged/       – Merged PDFs
 *   ├── Converted/    – Image-to-PDF conversions
 *   ├── Scanned/      – Scanned documents
 *   └── Signatures/   – Signature image files
 *
 * NOTE: This is ONLY for saving NEW PDFs created by the app (signed, merged,
 * image-to-PDF, scanned). For reading existing PDFs the app uses the original
 * file location and tracks reading progress/notes/bookmarks in SharedPreferences.
 */
public class FileManager {
    private static final String TAG = "FileManager";

    private static final String APP_FOLDER_NAME = "PDFReader";

    // Public category constants – callers use these when saving files
    public static final String CATEGORY_SIGNED     = "Signed";
    public static final String CATEGORY_MERGED     = "Merged";
    public static final String CATEGORY_CONVERTED  = "Converted";
    public static final String CATEGORY_SCANNED    = "Scanned";
    public static final String CATEGORY_SIGNATURES = "Signatures";

    private final Context context;
    private File appFolder;
    private File signedFolder;
    private File mergedFolder;
    private File convertedFolder;
    private File scannedFolder;
    private File signaturesFolder;

    public FileManager(Context context) {
        this.context = context;
        initializeFolders();
    }

    // -------------------------------------------------------------------------
    // Folder initialisation
    // -------------------------------------------------------------------------

    private void initializeFolders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses scoped storage (app-specific external storage)
            appFolder = context.getExternalFilesDir(APP_FOLDER_NAME);
        } else {
            // Android 9 and below – use public Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            appFolder = new File(downloadsDir, APP_FOLDER_NAME);
        }

        if (appFolder != null) {
            signedFolder     = new File(appFolder, CATEGORY_SIGNED);
            mergedFolder     = new File(appFolder, CATEGORY_MERGED);
            convertedFolder  = new File(appFolder, CATEGORY_CONVERTED);
            scannedFolder    = new File(appFolder, CATEGORY_SCANNED);
            signaturesFolder = new File(appFolder, CATEGORY_SIGNATURES);
        }

        createFoldersIfNeeded();
    }

    private void createFoldersIfNeeded() {
        try {
            createDir(appFolder,       "App");
            createDir(signedFolder,    CATEGORY_SIGNED);
            createDir(mergedFolder,    CATEGORY_MERGED);
            createDir(convertedFolder, CATEGORY_CONVERTED);
            createDir(scannedFolder,   CATEGORY_SCANNED);
            createDir(signaturesFolder, CATEGORY_SIGNATURES);
        } catch (Exception e) {
            Log.e(TAG, "Error creating folders", e);
        }
    }

    private void createDir(File dir, String label) {
        if (dir != null && !dir.exists()) {
            boolean created = dir.mkdirs();
            Log.d(TAG, label + " folder created: " + dir.getAbsolutePath() + " – " + created);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Ensure all category folders exist (call on app startup). */
    public void ensureFoldersExist() {
        createFoldersIfNeeded();
    }

    /** @return absolute path of the app root folder */
    public String getAppFolderPath() {
        return appFolder != null ? appFolder.getAbsolutePath() : null;
    }

    /** @return absolute path of the requested category folder */
    public String getCategoryFolderPath(String category) {
        File folder = getCategoryFolder(category);
        return folder != null ? folder.getAbsolutePath() : null;
    }

    /**
     * Save a PDF byte array to the correct category folder.
     * Uses MediaStore on Android 10+, direct file write on Android 9-.
     *
     * @param pdfData  raw PDF bytes
     * @param fileName desired file name (will have .pdf appended if missing)
     * @param category one of the CATEGORY_* constants
     * @return path or content URI string of the saved file, or null on failure
     */
    public String savePdf(byte[] pdfData, String fileName, String category) {
        if (pdfData == null || pdfData.length == 0) {
            Log.e(TAG, "Cannot save empty PDF data");
            return null;
        }

        fileName = sanitizeFileName(fileName);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return savePdfWithMediaStore(pdfData, fileName, category);
            } else {
                return savePdfDirect(pdfData, fileName, category);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving PDF", e);
            return null;
        }
    }

    /**
     * Get a File object inside the correct category folder (for activities that
     * write the PDF themselves before passing bytes to savePdf).
     *
     * @param fileName desired file name
     * @param category one of the CATEGORY_* constants
     */
    public File getPdfFile(String fileName, String category) {
        fileName = sanitizeFileName(fileName);

        File folder = getCategoryFolder(category);
        if (folder == null || (!folder.exists() && !folder.mkdirs())) {
            Log.e(TAG, "Cannot access folder for category: " + category);
            return null;
        }

        return uniqueFile(folder, fileName);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private File getCategoryFolder(String category) {
        if (category == null) return signedFolder; // safe default
        switch (category) {
            case CATEGORY_SIGNED:     return signedFolder;
            case CATEGORY_MERGED:     return mergedFolder;
            case CATEGORY_CONVERTED:  return convertedFolder;
            case CATEGORY_SCANNED:    return scannedFolder;
            case CATEGORY_SIGNATURES: return signaturesFolder;
            default:                  return signedFolder;
        }
    }

    /** Returns a subpath string like "PDFReader/Signed" for MediaStore. */
    private String getMediaStoreRelativePath(String category) {
        return Environment.DIRECTORY_DOWNLOADS + "/" + APP_FOLDER_NAME + "/" + category;
    }

    private String savePdfWithMediaStore(byte[] pdfData, String fileName, String category)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                getMediaStoreRelativePath(category));

        Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for category: " + category);
            return null;
        }

        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out != null) {
                out.write(pdfData);
                out.flush();
                Log.d(TAG, "PDF saved via MediaStore [" + category + "]: " + uri);
                return uri.toString();
            }
        }
        return null;
    }

    private String savePdfDirect(byte[] pdfData, String fileName, String category)
            throws IOException {
        File folder = getCategoryFolder(category);
        if (folder == null || (!folder.exists() && !folder.mkdirs())) {
            Log.e(TAG, "Cannot create folder for category: " + category);
            return null;
        }

        File pdfFile = uniqueFile(folder, fileName);
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            fos.write(pdfData);
            fos.flush();
            Log.d(TAG, "PDF saved directly [" + category + "]: " + pdfFile.getAbsolutePath());
            return pdfFile.getAbsolutePath();
        }
    }

    /** Ensures the file name ends with .pdf and is not blank. */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            fileName = "document_" + ts + ".pdf";
        }
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            fileName += ".pdf";
        }
        return fileName;
    }

    /** Returns a File inside {@code folder} that doesn't already exist. */
    private File uniqueFile(File folder, String fileName) {
        File file = new File(folder, fileName);
        if (!file.exists()) return file;

        String base = fileName.substring(0, fileName.length() - 4); // strip .pdf
        int counter = 1;
        while (file.exists()) {
            file = new File(folder, base + "_" + counter + ".pdf");
            counter++;
        }
        return file;
    }
}
