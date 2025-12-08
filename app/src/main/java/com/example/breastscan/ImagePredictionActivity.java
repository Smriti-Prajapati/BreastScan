package com.example.breastscan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;

public class ImagePredictionActivity extends AppCompatActivity {

    ImageView ivPreview;
    MaterialButton btnUpload, btnCamera, btnPredict;
    Bitmap selectedBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_prediction);

        ivPreview = findViewById(R.id.previewImage);
        btnUpload = findViewById(R.id.btnUploadImage);
        btnCamera = findViewById(R.id.btnCamera);
        btnPredict = findViewById(R.id.btnPredict);

        btnUpload.setOnClickListener(v -> pickImage.launch("image/*"));
        btnCamera.setOnClickListener(v -> openCamera());
        btnPredict.setOnClickListener(v -> {
            if (selectedBitmap == null) {
                Toast.makeText(this, "Please upload or capture an image first!", Toast.LENGTH_SHORT).show();
            } else {
                // Placeholder - implement your image model prediction here.
                Toast.makeText(this, "Prediction feature coming soon!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        ivPreview.setImageBitmap(selectedBitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 200);
                return;
            }
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 101 && res == RESULT_OK && data != null) {
            selectedBitmap = (Bitmap) data.getExtras().get("data");
            ivPreview.setImageBitmap(selectedBitmap);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
