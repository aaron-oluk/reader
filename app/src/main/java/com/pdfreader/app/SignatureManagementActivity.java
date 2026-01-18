package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class SignatureManagementActivity extends AppCompatActivity {

    private RecyclerView signaturesRecycler;
    private LinearLayout emptyStateContainer;
    private MaterialCardView cardDrawSignature;
    private MaterialCardView cardCameraSignature;
    private SignatureManager signatureManager;
    private SignatureAdapter adapter;
    private TextView emptyStateText;

    private ActivityResultLauncher<Intent> cameraSignatureLauncher;
    private ActivityResultLauncher<Intent> drawSignatureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_management);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Signatures");
        }

        signatureManager = new SignatureManager(this);
        initViews();
        setupLaunchers();
        loadSignatures();
    }

    private void initViews() {
        signaturesRecycler = findViewById(R.id.signatures_recycler);
        emptyStateContainer = findViewById(R.id.empty_state_container);
        cardDrawSignature = findViewById(R.id.card_draw_signature);
        cardCameraSignature = findViewById(R.id.card_camera_signature);
        emptyStateText = findViewById(R.id.empty_state_text);

        signaturesRecycler.setLayoutManager(new LinearLayoutManager(this));

        // Create new signature options
        cardDrawSignature.setOnClickListener(v -> openDrawSignature());
        cardCameraSignature.setOnClickListener(v -> openCameraSignatureCapture());
    }

    private void setupLaunchers() {
        // Camera signature launcher
        cameraSignatureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String savedPath = result.getData().getStringExtra(
                                ReviewSignatureActivity.EXTRA_SAVED_SIGNATURE_PATH);
                        if (savedPath != null) {
                            Toast.makeText(this, "Signature saved successfully", Toast.LENGTH_SHORT).show();
                            loadSignatures();
                        }
                    }
                });

        // Draw signature launcher
        drawSignatureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String savedPath = result.getData().getStringExtra(
                                ReviewSignatureActivity.EXTRA_SAVED_SIGNATURE_PATH);
                        if (savedPath != null) {
                            Toast.makeText(this, "Signature saved successfully", Toast.LENGTH_SHORT).show();
                            loadSignatures();
                        }
                    }
                });
    }

    private void loadSignatures() {
        List<String> savedSignatures = signatureManager.getSavedSignatures();

        if (savedSignatures.isEmpty()) {
            signaturesRecycler.setVisibility(View.GONE);
            emptyStateContainer.setVisibility(View.VISIBLE);
            emptyStateText.setText("No saved signatures yet\nCreate one using the options above");
        } else {
            signaturesRecycler.setVisibility(View.VISIBLE);
            emptyStateContainer.setVisibility(View.GONE);
        }

        adapter = new SignatureAdapter(savedSignatures, signatureManager);
        signaturesRecycler.setAdapter(adapter);

        adapter.setOnSignatureClickListener(filePath -> {
            // View signature details
            showSignatureDetails(filePath);
        });

        adapter.setOnSignatureDeleteListener(filePath -> {
            showDeleteConfirmation(filePath);
        });
    }

    private void showSignatureDetails(String filePath) {
        Bitmap signature = signatureManager.loadSignature(filePath);
        if (signature == null) {
            Toast.makeText(this, "Failed to load signature", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signature_preview, null);
        
        ImageView previewImage = dialogView.findViewById(R.id.signature_preview);
        TextView signatureName = dialogView.findViewById(R.id.signature_name);
        
        previewImage.setImageBitmap(signature);
        signatureName.setText(signatureManager.getSignatureName(filePath));
        
        builder.setView(dialogView);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showDeleteConfirmation(String filePath) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Signature")
                .setMessage("Are you sure you want to delete this signature?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (signatureManager.deleteSignature(filePath)) {
                        Toast.makeText(this, "Signature deleted", Toast.LENGTH_SHORT).show();
                        loadSignatures();
                    } else {
                        Toast.makeText(this, "Failed to delete signature", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openCameraSignatureCapture() {
        Intent intent = new Intent(this, CaptureSignatureActivity.class);
        cameraSignatureLauncher.launch(intent);
    }

    private void openDrawSignature() {
        Intent intent = new Intent(this, DrawSignatureActivity.class);
        drawSignatureLauncher.launch(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
