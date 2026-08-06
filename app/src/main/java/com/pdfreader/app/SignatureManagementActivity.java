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
    private TextView signatureCount;
    private ImageView activeSignaturePreview;
    private ImageView activeSignaturePlaceholder;
    private TextView activeSignatureName;

    private ActivityResultLauncher<Intent> cameraSignatureLauncher;
    private ActivityResultLauncher<Intent> drawSignatureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowInsetsHelper.enableEdgeToEdge(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_management);
        View appBar = findViewById(R.id.app_bar);
        if (appBar != null) {
            WindowInsetsHelper.applyAppBarInsets(appBar);
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
        signatureCount = findViewById(R.id.signature_count);
        activeSignaturePreview = findViewById(R.id.active_signature_preview);
        activeSignaturePlaceholder = findViewById(R.id.active_signature_placeholder);
        activeSignatureName = findViewById(R.id.active_signature_name);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        signaturesRecycler.setLayoutManager(new LinearLayoutManager(this));

        cardDrawSignature.setOnClickListener(v -> openDrawSignature());
        cardCameraSignature.setOnClickListener(v -> openCameraSignatureCapture());
    }

    private void setupLaunchers() {
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

        if (signatureCount != null) {
            signatureCount.setText(String.valueOf(savedSignatures.size()));
        }

        updateActiveSignature(savedSignatures);

        if (savedSignatures.isEmpty()) {
            signaturesRecycler.setVisibility(View.GONE);
            emptyStateContainer.setVisibility(View.VISIBLE);
            emptyStateText.setText("Draw or photograph a signature to get started");
        } else {
            signaturesRecycler.setVisibility(View.VISIBLE);
            emptyStateContainer.setVisibility(View.GONE);
        }

        adapter = new SignatureAdapter(savedSignatures, signatureManager);
        signaturesRecycler.setAdapter(adapter);

        adapter.setOnSignatureClickListener(this::showSignatureDetails);
        adapter.setOnSignatureDeleteListener(this::showDeleteConfirmation);
    }

    private void updateActiveSignature(List<String> savedSignatures) {
        if (activeSignaturePreview == null) return;

        if (savedSignatures.isEmpty()) {
            activeSignaturePreview.setVisibility(View.GONE);
            activeSignaturePreview.setImageDrawable(null);
            if (activeSignaturePlaceholder != null) {
                activeSignaturePlaceholder.setVisibility(View.VISIBLE);
            }
            if (activeSignatureName != null) {
                activeSignatureName.setText("No signature selected");
            }
            return;
        }

        String path = savedSignatures.get(0);
        Bitmap bitmap = signatureManager.loadSignature(path);
        if (bitmap != null) {
            activeSignaturePreview.setImageBitmap(bitmap);
            activeSignaturePreview.setVisibility(View.VISIBLE);
            if (activeSignaturePlaceholder != null) {
                activeSignaturePlaceholder.setVisibility(View.GONE);
            }
            if (activeSignatureName != null) {
                activeSignatureName.setText(signatureManager.getSignatureName(path));
            }
        }
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
}
