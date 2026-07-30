package com.pdfreader.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CropSignatureActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";
    public static final String EXTRA_CROPPED_IMAGE_PATH = "cropped_image_path";

    // Optional suggested crop region, in full-resolution upright-image pixel coordinates
    // (i.e. after accounting for EXIF rotation), mapped from the on-screen capture guide.
    public static final String EXTRA_SUGGESTED_CROP_LEFT = "suggested_crop_left";
    public static final String EXTRA_SUGGESTED_CROP_TOP = "suggested_crop_top";
    public static final String EXTRA_SUGGESTED_CROP_RIGHT = "suggested_crop_right";
    public static final String EXTRA_SUGGESTED_CROP_BOTTOM = "suggested_crop_bottom";

    private static final int MAX_DIMENSION = 2048;

    private CropImageView cropImageView;
    private MaterialButton btnCrop;
    private MaterialButton btnSkip;
    private ImageView btnBack;

    private String imagePath;
    private Bitmap originalBitmap;
    private boolean hasSuggestedCropRect;
    private float suggestedCropLeft, suggestedCropTop, suggestedCropRight, suggestedCropBottom;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_signature);

        cropImageView = findViewById(R.id.crop_image_view);
        btnCrop = findViewById(R.id.btn_crop);
        btnSkip = findViewById(R.id.btn_skip);
        btnBack = findViewById(R.id.btn_back);

        imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (imagePath == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        hasSuggestedCropRect = getIntent().hasExtra(EXTRA_SUGGESTED_CROP_LEFT);
        if (hasSuggestedCropRect) {
            suggestedCropLeft = getIntent().getFloatExtra(EXTRA_SUGGESTED_CROP_LEFT, 0f);
            suggestedCropTop = getIntent().getFloatExtra(EXTRA_SUGGESTED_CROP_TOP, 0f);
            suggestedCropRight = getIntent().getFloatExtra(EXTRA_SUGGESTED_CROP_RIGHT, 0f);
            suggestedCropBottom = getIntent().getFloatExtra(EXTRA_SUGGESTED_CROP_BOTTOM, 0f);
        }

        loadImage();

        btnBack.setOnClickListener(v -> finish());
        btnSkip.setOnClickListener(v -> skipCrop());
        btnCrop.setOnClickListener(v -> performCrop());
    }

    private void loadImage() {
        executorService.execute(() -> {
            try {
                // First, check the image dimensions without allocating a bitmap
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imagePath, options);

                // Calculate appropriate sample size so we never decode a full
                // camera-resolution (e.g. 4000x3000+) image at full size
                int sampleSize = 1;
                if (options.outHeight > MAX_DIMENSION || options.outWidth > MAX_DIMENSION) {
                    final int heightRatio = Math.round((float) options.outHeight / (float) MAX_DIMENSION);
                    final int widthRatio = Math.round((float) options.outWidth / (float) MAX_DIMENSION);
                    // Use the larger ratio so BOTH dimensions end up under MAX_DIMENSION;
                    // taking the smaller one leaves typical 4:3 camera photos undownsampled.
                    sampleSize = Math.max(heightRatio, widthRatio);
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap decodedBitmap = BitmapFactory.decodeFile(imagePath, options);
                if (decodedBitmap != null) {
                    decodedBitmap = ImageOrientationUtils.applyExifOrientation(decodedBitmap, imagePath);
                }
                final Bitmap decoded = decodedBitmap;
                final int finalSampleSize = sampleSize;

                runOnUiThread(() -> {
                    if (decoded == null) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    originalBitmap = decoded;
                    if (hasSuggestedCropRect) {
                        cropImageView.setSuggestedCropRegion(new RectF(
                                suggestedCropLeft / finalSampleSize,
                                suggestedCropTop / finalSampleSize,
                                suggestedCropRight / finalSampleSize,
                                suggestedCropBottom / finalSampleSize));
                    }
                    cropImageView.setImageBitmap(originalBitmap);
                });
            } catch (Throwable t) {
                t.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void skipCrop() {
        // Return the original image path without cropping
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_CROPPED_IMAGE_PATH, imagePath);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void performCrop() {
        if (originalBitmap == null) {
            Toast.makeText(this, "No image to crop", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bitmap croppedBitmap = cropImageView.getCroppedBitmap();
            if (croppedBitmap == null) {
                Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save cropped image
            File croppedFile = new File(getCacheDir(), "signature_cropped_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(croppedFile);
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();

            if (croppedBitmap != originalBitmap) {
                croppedBitmap.recycle();
            }

            // Return cropped image path
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_CROPPED_IMAGE_PATH, croppedFile.getAbsolutePath());
            setResult(RESULT_OK, resultIntent);
            finish();

        } catch (Throwable t) {
            t.printStackTrace();
            Toast.makeText(this, "Error cropping image: " + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
    }
}
