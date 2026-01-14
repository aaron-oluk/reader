package com.pdfreader.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SignatureManager {
    private static final String TAG = "SignatureManager";
    private static final String SIGNATURES_DIR = "signatures";
    
    private Context context;
    private File signaturesDir;
    
    public SignatureManager(Context context) {
        this.context = context;
        signaturesDir = new File(context.getFilesDir(), SIGNATURES_DIR);
        // Ensure signatures directory exists
        if (!signaturesDir.exists()) {
            boolean created = signaturesDir.mkdirs();
            if (created) {
                Log.d(TAG, "Signatures directory created: " + signaturesDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create signatures directory");
            }
        } else {
            Log.d(TAG, "Signatures directory exists: " + signaturesDir.getAbsolutePath());
        }
    }
    
    /**
     * Save a signature bitmap to app storage
     * @param signatureBitmap The signature bitmap to save
     * @param name Optional name for the signature (null for auto-generated)
     * @return The saved signature file path, or null if failed
     */
    public String saveSignature(Bitmap signatureBitmap, String name) {
        if (signatureBitmap == null || signatureBitmap.isRecycled()) {
            Log.e(TAG, "Cannot save null or recycled bitmap");
            return null;
        }
        
        if (signatureBitmap.getWidth() <= 0 || signatureBitmap.getHeight() <= 0) {
            Log.e(TAG, "Cannot save bitmap with invalid dimensions");
            return null;
        }
        
        // Ensure signatures directory exists before saving
        if (!signaturesDir.exists()) {
            boolean created = signaturesDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create signatures directory");
                return null;
            }
        }
        
        try {
            String fileName;
            if (name != null && !name.trim().isEmpty()) {
                // Sanitize filename
                fileName = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".png";
            } else {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                fileName = "signature_" + timestamp + ".png";
            }
            
            File signatureFile = new File(signaturesDir, fileName);
            
            // If file already exists, append number to make it unique
            int counter = 1;
            String baseFileName = fileName.substring(0, fileName.length() - 4); // Remove .png
            while (signatureFile.exists()) {
                fileName = baseFileName + "_" + counter + ".png";
                signatureFile = new File(signaturesDir, fileName);
                counter++;
            }
            
            FileOutputStream fos = new FileOutputStream(signatureFile);
            boolean success = signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            
            if (!success) {
                Log.e(TAG, "Bitmap compression failed");
                signatureFile.delete();
                return null;
            }
            
            Log.d(TAG, "Signature saved to: " + signatureFile.getAbsolutePath());
            return signatureFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error saving signature", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error saving signature", e);
            return null;
        }
    }
    
    /**
     * Load a signature bitmap from file path
     */
    public Bitmap loadSignature(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.w(TAG, "Signature file does not exist: " + filePath);
                return null;
            }
            
            if (file.length() == 0) {
                Log.w(TAG, "Signature file is empty: " + filePath);
                return null;
            }
            
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode signature bitmap: " + filePath);
            }
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error loading signature from " + filePath, e);
            return null;
        }
    }
    
    /**
     * Get list of all saved signature file paths
     */
    public List<String> getSavedSignatures() {
        List<String> signatures = new ArrayList<>();
        if (signaturesDir.exists() && signaturesDir.isDirectory()) {
            File[] files = signaturesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    signatures.add(file.getAbsolutePath());
                }
            }
        }
        return signatures;
    }
    
    /**
     * Get display name for a signature file
     */
    public String getSignatureName(String filePath) {
        File file = new File(filePath);
        String name = file.getName();
        // Remove .png extension
        if (name.toLowerCase().endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        // Remove timestamp prefix if present
        if (name.startsWith("signature_")) {
            name = name.substring(10);
        }
        return name;
    }
    
    /**
     * Delete a signature
     */
    public boolean deleteSignature(String filePath) {
        try {
            File file = new File(filePath);
            return file.delete();
        } catch (Exception e) {
            Log.e(TAG, "Error deleting signature", e);
            return false;
        }
    }
    
    /**
     * Get the signatures directory path
     */
    public String getSignaturesDirectory() {
        return signaturesDir.getAbsolutePath();
    }
    
    /**
     * Get the number of saved signatures
     */
    public int getSignatureCount() {
        if (signaturesDir.exists() && signaturesDir.isDirectory()) {
            File[] files = signaturesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            return files != null ? files.length : 0;
        }
        return 0;
    }
}
