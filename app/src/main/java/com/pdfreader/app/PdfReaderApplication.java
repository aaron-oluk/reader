package com.pdfreader.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public class PdfReaderApplication extends Application {

    private static final String TAG = "PdfReaderApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PDFBoxResourceLoader.init(getApplicationContext());
            Log.i(TAG, "PDFBoxResourceLoader.init() completed, isReady=" + PDFBoxResourceLoader.isReady());
        } catch (Throwable t) {
            Log.e(TAG, "PDFBoxResourceLoader.init() failed", t);
        }

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                // After setContentView in onCreate — apply edge-to-edge + real system bar insets.
                WindowInsetsHelper.applyDefault(activity);
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}
