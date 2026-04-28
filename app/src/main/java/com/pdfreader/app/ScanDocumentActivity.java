package com.pdfreader.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.pdfreader.app.fragments.ScannerFragment;

public class ScanDocumentActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "scan_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_document);

        if (savedInstanceState == null) {
            String mode = getIntent().getStringExtra(EXTRA_MODE);
            if (mode == null) mode = ScannerFragment.MODE_PAGE;

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.scan_fragment_container, ScannerFragment.newInstance(mode))
                    .commit();
        }
    }
}
