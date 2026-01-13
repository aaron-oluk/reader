package com.pdfreader.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.pdfreader.app.fragments.HomeFragment;
import com.pdfreader.app.fragments.InsightsFragment;
import com.pdfreader.app.fragments.LibraryFragment;
import com.pdfreader.app.fragments.ProfileFragment;
import com.pdfreader.app.fragments.ScannerFragment;

public class MainActivityNew extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        bottomNavigation = findViewById(R.id.bottom_navigation);
        
        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                fragment = new HomeFragment();
            } else if (itemId == R.id.navigation_library) {
                fragment = new LibraryFragment();
            } else if (itemId == R.id.navigation_scan) {
                fragment = new ScannerFragment();
            } else if (itemId == R.id.navigation_stats) {
                fragment = new InsightsFragment();
            } else if (itemId == R.id.navigation_profile) {
                fragment = new ProfileFragment();
            }

            return loadFragment(fragment);
        });
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}
