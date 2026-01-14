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
        if (!signaturesDir.exists()) {
            signaturesDir.mkdirs();
        }
    }
    
    /**
     * Save a signature bitmap to app storage
     * @param signatureBitmap The signature bitmap to save
     * @param name Optional name for the signature (null for auto-generated)
     * @return The saved signature file path, or null if failed
     */
    public String saveSignature(Bitmap signatureBitmap, String name) {
        if (signatureBitmap == null) {
            return null;
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
            FileOutputStream fos = new FileOutputStream(signatureFile);
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            
            Log.d(TAG, "Signature saved: " + signatureFile.getAbsolutePath());
            return signatureFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error saving signature", e);
            return null;
        }
    }
    
    /**
     * Load a signature bitmap from file path
     */
    public Bitmap loadSignature(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }
            return BitmapFactory.decodeFile(filePath);
        } catch (Exception e) {
            Log.e(TAG, "Error loading signature", e);
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
}
