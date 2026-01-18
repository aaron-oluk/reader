package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class DrawSignatureActivity extends AppCompatActivity {

    private SignatureView signatureView;
    private SignatureManager signatureManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw_signature);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Draw Signature");
        }

        signatureManager = new SignatureManager(this);
        signatureView = findViewById(R.id.signatureView);
        Button btnClear = findViewById(R.id.btnClear);
        Button btnSave = findViewById(R.id.btnSave);

        btnClear.setOnClickListener(v -> signatureView.clear());
        
        btnSave.setOnClickListener(v -> {
            if (signatureView.hasSignature()) {
                showSaveSignatureNameDialog(signatureView.getSignatureBitmap());
            } else {
                Toast.makeText(this, "Please draw your signature first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSaveSignatureNameDialog(Bitmap signatureBitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Signature");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter signature name (optional)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            saveSignature(signatureBitmap, name.isEmpty() ? null : name);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveSignature(Bitmap signatureBitmap, String name) {
        if (signatureBitmap == null) {
            Toast.makeText(this, "No signature to save", Toast.LENGTH_SHORT).show();
            return;
        }

        // Process and save signature
        Bitmap processed = SignatureProcessor.processSignature(signatureBitmap, 200);
        if (processed == null) {
            processed = signatureBitmap;
        }

        // Scale to reasonable size
        Bitmap scaled = SignatureProcessor.scaleSignature(processed, 800, 400);
        if (scaled == null) {
            scaled = processed;
        }

        String savedPath = signatureManager.saveSignature(scaled, name);

        // Clean up
        if (scaled != processed && scaled != signatureBitmap && !scaled.isRecycled()) {
            scaled.recycle();
        }
        if (processed != signatureBitmap && !processed.isRecycled()) {
            processed.recycle();
        }

        if (savedPath != null) {
            Toast.makeText(this, "Signature saved", Toast.LENGTH_SHORT).show();
            Intent resultIntent = new Intent();
            resultIntent.putExtra(ReviewSignatureActivity.EXTRA_SAVED_SIGNATURE_PATH, savedPath);
            setResult(RESULT_OK, resultIntent);
            finish();
        } else {
            Toast.makeText(this, "Failed to save signature", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
