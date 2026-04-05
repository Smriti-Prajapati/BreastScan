package com.example.breastscan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class OCRReportActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri uri = getIntent().getData();

        if (uri != null) {
            processFromUri(uri);
        } else {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            processFromUri(imageUri);
        }
    }


    private void processFromUri(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            processImage(bitmap);
        } catch (IOException e) {
            Toast.makeText(this, "Image error", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(text -> {

                    String resultText = text.getText().toLowerCase();

                    // 🔥 DEBUG (see OCR output)
                    Toast.makeText(this, resultText, Toast.LENGTH_LONG).show();

                    extractAndSend(resultText);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "OCR failed", Toast.LENGTH_SHORT).show();
                });
    }

    // 🔥 FINAL EXTRACTION (LABEL BASED)
    private void extractAndSend(String text) {

        text = text.toLowerCase();

        // 🔥 Step 1: remove everything BEFORE table
        int start = text.indexOf("quantitative");
        if (start != -1) {
            text = text.substring(start);
        }

        // 🔥 Step 2: extract ONLY decimal numbers
        String[] tokens = text.replaceAll("[^0-9.]", " ")
                .trim()
                .split("\\s+");

        float[] values = new float[10];
        int index = 0;

        for (String t : tokens) {
            try {
                if (t.contains(".")) {
                    float val = Float.parseFloat(t);

                    // ignore very large numbers (like 598.2 is ok, but 2026 etc not)
                    if (val < 2000) {
                        values[index++] = val;
                        if (index == 10) break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 🔥 DEBUG
        Toast.makeText(this,
                "R=" + values[0] + " T=" + values[1],
                Toast.LENGTH_LONG).show();

        DataHolder.ocrValues = values;
        DataHolder.hasData = true;

        Intent intent = new Intent(this, FormPredictionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // 🔥 VALUE EXTRACTOR (IMPORTANT)
    private float getValue(String text, String key) {
        try {
            int index = text.indexOf(key);

            if (index != -1) {

                // take nearby text
                String sub = text.substring(index, Math.min(index + 50, text.length()));

                // extract numbers
                String[] tokens = sub.replaceAll("[^0-9.]", " ")
                        .trim()
                        .split("\\s+");

                for (String t : tokens) {
                    try {
                        return Float.parseFloat(t);
                    } catch (Exception ignored) {}
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1f;
    }
}