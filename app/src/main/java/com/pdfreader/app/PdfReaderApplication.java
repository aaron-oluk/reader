package com.pdfreader.app;

import android.app.Application;
import android.util.Log;

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
    }
}
