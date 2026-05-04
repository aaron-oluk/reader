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
        // getExternalFilesDir() always works — no runtime permissions required on any
        // API level. It places files under Android/data/<package>/files/<category>/
        // which is visible in any file manager and survives reinstalls on Android 10+.
        appFolder        = context.getExternalFilesDir(null);
        signedFolder     = context.getExternalFilesDir(CATEGORY_SIGNED);
        mergedFolder     = context.getExternalFilesDir(CATEGORY_MERGED);
        convertedFolder  = context.getExternalFilesDir(CATEGORY_CONVERTED);
        scannedFolder    = context.getExternalFilesDir(CATEGORY_SCANNED);
        signaturesFolder = context.getExternalFilesDir(CATEGORY_SIGNATURES);
        // getExternalFilesDir() creates the directory automatically; these mkdirs
        // calls are just a safety net in case external storage is temporarily unmounted.
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
            Log.d(TAG, label + " folder created: " + (dir.getAbsolutePath()) + " – " + created);
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
     * On Android 10+ files also appear in the Downloads app under PDFReader/<category>.
     *
     * @param pdfData  raw PDF bytes
     * @param fileName desired file name (will have .pdf appended if missing)
     * @param category one of the CATEGORY_* constants
     * @return absolute file path of the saved file, or null on failure
     */
    public String savePdf(byte[] pdfData, String fileName, String category) {
        if (pdfData == null || pdfData.length == 0) {
            Log.e(TAG, "Cannot save empty PDF data");
            return null;
        }

        fileName = sanitizeFileName(fileName);

        try {
            // Primary: write to app-specific external storage (always writable, no permissions needed).
            String result = savePdfDirect(pdfData, fileName, category);
            if (result != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Additionally publish to MediaStore so the file also appears in Downloads.
                publishToMediaStore(pdfData, fileName, category);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error saving PDF", e);
            return null;
        }
    }

    /**
     * Get a File object inside the correct category folder.
     */
    public File getPdfFile(String fileName, String category) {
        fileName = sanitizeFileName(fileName);
        File folder = getCategoryFolder(category);
        if (folder == null) {
            folder = new File(context.getCacheDir(), category);
        }
        folder.mkdirs();
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

    /** Write directly to app-specific external storage — always succeeds, no permissions. */
    private String savePdfDirect(byte[] pdfData, String fileName, String category)
            throws IOException {
        File folder = getCategoryFolder(category);
        if (folder == null) {
            Log.e(TAG, "Cannot resolve folder for category: " + category);
            return null;
        }
        folder.mkdirs();

        File pdfFile = uniqueFile(folder, fileName);
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            fos.write(pdfData);
            fos.flush();
            Log.d(TAG, "PDF saved [" + category + "]: " + pdfFile.getAbsolutePath());
            return pdfFile.getAbsolutePath();
        }
    }

    /**
     * On Android 10+ also insert a copy into MediaStore Downloads so the file
     * appears in the system Downloads app under PDFReader/<category>/.
     * Failures here are non-fatal — the file is already saved via savePdfDirect.
     */
    private void publishToMediaStore(byte[] pdfData, String fileName, String category) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            // RELATIVE_PATH for MediaStore.Downloads is relative to the Downloads root directory.
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + APP_FOLDER_NAME + "/" + category);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) { Log.w(TAG, "MediaStore insert returned null, skipping"); return; }

            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out != null) { out.write(pdfData); out.flush(); }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            Log.d(TAG, "PDF also published to MediaStore Downloads [" + category + "]");
        } catch (Exception e) {
            Log.w(TAG, "MediaStore publish failed (non-fatal): " + e.getMessage());
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
