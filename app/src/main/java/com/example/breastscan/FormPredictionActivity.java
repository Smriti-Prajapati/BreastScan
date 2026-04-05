package com.example.breastscan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FormPredictionActivity extends AppCompatActivity {

    private static final String MODEL_FILE = "breast_tabular_model.tflite";

    EditText et_radius_mean, et_texture_mean, et_perimeter_mean, et_area_mean,
            et_smoothness_mean, et_compactness_mean, et_concavity_mean,
            et_concave_points_mean, et_symmetry_mean, et_fractal_dimension_mean;

    Button btnPredictForm, btnUploadReport;
    Interpreter tflite;

    private boolean isDataLoaded = false;

    private final float[] MEAN = {
            14.127292f, 19.289649f, 91.969033f, 654.889104f, 0.096360f,
            0.104341f, 0.088799f, 0.048919f, 0.181162f, 0.062798f,
            0.405172f, 1.216853f, 2.866059f, 40.337079f, 0.007041f,
            0.025478f, 0.031894f, 0.011796f, 0.020542f, 0.003794f,
            16.269190f, 25.677223f, 107.261213f, 880.583128f, 0.132369f,
            0.254265f, 0.272188f, 0.114606f, 0.290076f, 0.083946f
    };

    private final float[] STD = {
            3.524049f, 4.301036f, 24.298981f, 351.914129f, 0.014064f,
            0.052813f, 0.079720f, 0.038803f, 0.027388f, 0.007060f,
            0.277313f, 0.551648f, 2.021855f, 45.491006f, 0.003003f,
            0.017909f, 0.030186f, 0.006170f, 0.008266f, 0.002646f,
            4.833242f, 6.140854f, 33.602542f, 569.356993f, 0.022832f,
            0.162938f, 0.208624f, 0.065732f, 0.061867f, 0.018061f
    };


    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_form_prediction);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        android.util.Log.d("DEBUG_FORM", "onCreate called");
        android.util.Log.d("FLOW", "Form opened");

        // Bind UI
        et_radius_mean = findViewById(R.id.et_radius_mean);
        et_texture_mean = findViewById(R.id.et_texture_mean);
        et_perimeter_mean = findViewById(R.id.et_perimeter_mean);
        et_area_mean = findViewById(R.id.et_area_mean);
        et_smoothness_mean = findViewById(R.id.et_smoothness_mean);
        et_compactness_mean = findViewById(R.id.et_compactness_mean);
        et_concavity_mean = findViewById(R.id.et_concavity_mean);
        et_concave_points_mean = findViewById(R.id.et_concave_points_mean);
        et_symmetry_mean = findViewById(R.id.et_symmetry_mean);
        et_fractal_dimension_mean = findViewById(R.id.et_fractal_dimension_mean);

        btnPredictForm = findViewById(R.id.btnPredictForm);
        btnUploadReport = findViewById(R.id.btnUploadReport);


        // ✅ RESTORE VALUES (VERY IMPORTANT)
        if (s != null) {
            et_radius_mean.setText(s.getString("r", ""));
            et_texture_mean.setText(s.getString("t", ""));
            et_perimeter_mean.setText(s.getString("p", ""));
            et_area_mean.setText(s.getString("a", ""));
            et_smoothness_mean.setText(s.getString("s", ""));
            et_compactness_mean.setText(s.getString("c", ""));
            et_concavity_mean.setText(s.getString("co", ""));
            et_concave_points_mean.setText(s.getString("cp", ""));
            et_symmetry_mean.setText(s.getString("sy", ""));
            et_fractal_dimension_mean.setText(s.getString("f", ""));
        }

        // Load model
        try {
            tflite = new Interpreter(FileUtil.loadMappedFile(this, MODEL_FILE));
        } catch (IOException e) {
            Toast.makeText(this, "Model load failed", Toast.LENGTH_LONG).show();
            return;
        }

        // Upload
        btnUploadReport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"image/*", "application/pdf"});
            startActivityForResult(intent, 100);
        });






        // ✅ OCR APPLY ONLY ON FIRST LOAD
        if (DataHolder.hasData) {

            float[] v = DataHolder.ocrValues;

            setIfEmpty(et_radius_mean, v[0]);
            setIfEmpty(et_texture_mean, v[1]);
            setIfEmpty(et_perimeter_mean, v[2]);
            setIfEmpty(et_area_mean, v[3]);
            setIfEmpty(et_smoothness_mean, v[4]);
            setIfEmpty(et_compactness_mean, v[5]);
            setIfEmpty(et_concavity_mean, v[6]);
            setIfEmpty(et_concave_points_mean, v[7]);
            setIfEmpty(et_symmetry_mean, v[8]);
            setIfEmpty(et_fractal_dimension_mean, v[9]);

            DataHolder.hasData = false; // only once
        }



        // Predict
        btnPredictForm.setOnClickListener(v -> {

            float[] raw = buildRaw30Array();
            if (raw == null) return;

            float[] scaled = new float[30];
            for (int x = 0; x < 30; x++) {
                scaled[x] = (raw[x] - MEAN[x]) / STD[x];
            }

            float prob = runModel(scaled);
            String label = prob >= 0.5f ? "Malignant" : "Benign";

            Intent intent = new Intent(this, ResultActivity.class);
            intent.putExtra("result_score", prob);
            intent.putExtra("result_label", label);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
              // 🔥 THIS LINE FIXES EVERYTHING
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // if no previous activity → go to home
        if (isTaskRoot()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }
    }
    private void setIfEmpty(EditText et, float value) {
        if (et.getText().toString().trim().isEmpty()) {
            et.setText(String.valueOf(value));
        }
    }

    // SAVE STATE
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("r", et_radius_mean.getText().toString());
        outState.putString("t", et_texture_mean.getText().toString());
        outState.putString("p", et_perimeter_mean.getText().toString());
        outState.putString("a", et_area_mean.getText().toString());
        outState.putString("s", et_smoothness_mean.getText().toString());
        outState.putString("c", et_compactness_mean.getText().toString());
        outState.putString("co", et_concavity_mean.getText().toString());
        outState.putString("cp", et_concave_points_mean.getText().toString());
        outState.putString("sy", et_symmetry_mean.getText().toString());
        outState.putString("f", et_fractal_dimension_mean.getText().toString());
    }

    private void setIfPresent(Intent i, String key, EditText et) {
        if (i.hasExtra(key)) {
            float v = i.getFloatExtra(key, -1f);
            if (v > 0 && et.getText().toString().trim().isEmpty()) {
                et.setText(String.valueOf(v));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // 🔥 THIS STOPS RESET
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Upload image
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            String type = getContentResolver().getType(uri);

            if (type != null && type.equals("application/pdf")) {
                Toast.makeText(this, "PDF selected", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, OCRReportActivity.class);
                intent.setData(uri);
                startActivity(intent);
            }
        }



    }

    private float safeParse(EditText et) {

        String val = et.getText().toString().trim();

        // remove OCR garbage
        val = val.replaceAll("[^0-9.]", "");

        if (val.isEmpty() || val.equals(".")) {
            return -1f; // ❌ DO NOT CRASH
        }

        try {
            return Float.parseFloat(val);
        } catch (Exception e) {
            return -1f; // ❌ DO NOT CRASH
        }
    }

    private float[] buildRaw30Array() {
        try {
            float[] raw = new float[30];

            raw[0] = safeParse(et_radius_mean);
            raw[1] = safeParse(et_texture_mean);
            raw[2] = safeParse(et_perimeter_mean);
            raw[3] = safeParse(et_area_mean);
            raw[4] = safeParse(et_smoothness_mean);
            raw[5] = safeParse(et_compactness_mean);
            raw[6] = safeParse(et_concavity_mean);
            raw[7] = safeParse(et_concave_points_mean);
            raw[8] = safeParse(et_symmetry_mean);
            raw[9] = safeParse(et_fractal_dimension_mean);

            // 🔥 VALIDATION (VERY IMPORTANT)
            for (int i = 0; i < 10; i++) {
                if (raw[i] == -1f) {
                    Toast.makeText(this, "Invalid input detected!", Toast.LENGTH_SHORT).show();
                    return null;
                }
            }

            for (int i = 10; i < 30; i++) raw[i] = MEAN[i];

            return raw;

        } catch (Exception e) {
            Toast.makeText(this, "Fix invalid values!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private float runModel(float[] scaled) {

        try {
            float[][] input = new float[1][30];

            for (int i = 0; i < 30; i++) {
                input[0][i] = scaled[i];
            }

            // ✅ CORRECT OUTPUT SHAPE
            float[][] output = new float[1][2];

            tflite.run(input, output);

            // 🔥 IMPORTANT: take malignant probability
            return output[0][1];

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Model crash: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return 0f;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) tflite.close();
    }
}