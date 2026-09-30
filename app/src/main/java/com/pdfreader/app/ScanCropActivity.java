package com.pdfreader.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanCropActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";

    private CropImageView cropImageView;
    private MaterialButton btnDone;
    private String imagePath;
    private Bitmap pageBitmap;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowInsetsHelper.enableEdgeToEdge(this, false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_crop);

        View appBar = findViewById(R.id.app_bar);
        if (appBar != null) WindowInsetsHelper.applyAppBarInsets(appBar);
        View bottomBar = findViewById(R.id.bottom_bar);
        if (bottomBar != null) WindowInsetsHelper.applyNavigationBarPadding(bottomBar);

        cropImageView = findViewById(R.id.crop_image_view);
        btnDone = findViewById(R.id.btn_done);
        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (imagePath == null) {
            Toast.makeText(this, "Could not open this page", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            if (pageBitmap != null) cropImageView.resetCrop();
        });
        btnDone.setOnClickListener(v -> applyCrop());
        loadPage();
    }

    private void loadPage() {
        executor.execute(() -> {
            Bitmap bitmap = decodePage(imagePath);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    Toast.makeText(this, "Could not open this page", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                pageBitmap = bitmap;
                cropImageView.setImageBitmap(pageBitmap);
            });
        });
    }

    private void applyCrop() {
        if (pageBitmap == null) return;
        Bitmap cropped = cropImageView.getCroppedBitmap();
        if (cropped == null) {
            Toast.makeText(this, "Could not crop this page", Toast.LENGTH_SHORT).show();
            return;
        }
        btnDone.setEnabled(false);
        boolean saved = writeCroppedPage(imagePath, cropped);
        if (cropped != pageBitmap) cropped.recycle();
        if (!saved) {
            btnDone.setEnabled(true);
            Toast.makeText(this, "Could not save the crop", Toast.LENGTH_SHORT).show();
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    private static Bitmap decodePage(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longEdge / sample > 2400) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        return ImageOrientationUtils.applyExifOrientation(bitmap, path);
    }

    private static boolean writeCroppedPage(String path, Bitmap cropped) {
        File file = new File(path);
        File ready = new File(file.getParentFile(), file.getName() + ".crop");
        try (FileOutputStream fos = new FileOutputStream(ready)) {
            if (!cropped.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                ready.delete();
                return false;
            }
        } catch (Exception e) {
            ready.delete();
            return false;
        }
        try {
            ExifInterface exif = new ExifInterface(ready.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            exif.saveAttributes();
        } catch (Exception ignored) {
        }
        if (file.exists() && !file.delete()) {
            ready.delete();
            return false;
        }
        return ready.renameTo(file);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (pageBitmap != null && !pageBitmap.isRecycled()) pageBitmap.recycle();
    }
}
