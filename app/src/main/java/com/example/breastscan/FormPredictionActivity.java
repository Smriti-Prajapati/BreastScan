package com.example.breastscan;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FormPredictionActivity extends AppCompatActivity {

    private static final String TAG = "FormPredictionActivity";
    private static final String MODEL_FILE = "breast_tabular_model.tflite";

    // UI fields (10)
    EditText et_radius_mean, et_texture_mean, et_perimeter_mean, et_area_mean,
            et_smoothness_mean, et_compactness_mean, et_concavity_mean,
            et_concave_points_mean, et_symmetry_mean, et_fractal_dimension_mean;

    Button btnPredictForm;
    Interpreter tflite;

    // ORDER MUST MATCH MODEL (30 features)
    private final String[] FEATURE_NAMES = new String[] {
            "radius_mean","texture_mean","perimeter_mean","area_mean","smoothness_mean",
            "compactness_mean","concavity_mean","concave_points_mean","symmetry_mean","fractal_dimension_mean",
            "radius_se","texture_se","perimeter_se","area_se","smoothness_se",
            "compactness_se","concavity_se","concave_points_se","symmetry_se","fractal_dimension_se",
            "radius_worst","texture_worst","perimeter_worst","area_worst","smoothness_worst",
            "compactness_worst","concavity_worst","concave_points_worst","symmetry_worst","fractal_dimension_worst"
    };

    // Dataset mean and std (30) — used for standardization (same as you used)
    private final float[] MEAN = new float[] {
            14.127292f, 19.289649f, 91.969033f, 654.889104f, 0.096360f,
            0.104341f, 0.088799f, 0.048919f, 0.181162f, 0.062798f,
            0.405172f, 1.216853f, 2.866059f, 40.337079f, 0.007041f,
            0.025478f, 0.031894f, 0.011796f, 0.020542f, 0.003794f,
            16.269190f, 25.677223f, 107.261213f, 880.583128f, 0.132369f,
            0.254265f, 0.272188f, 0.114606f, 0.290076f, 0.083946f
    };

    private final float[] STD = new float[] {
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

        // bind UI
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

        // load model
        try {
            tflite = new Interpreter(FileUtil.loadMappedFile(this, MODEL_FILE));
        } catch (IOException e) {
            Log.e(TAG, "Model load failed", e);
            Toast.makeText(this, "Model load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        btnPredictForm.setOnClickListener(v -> {
            float[] raw30 = buildRaw30Array();
            if (raw30 == null) return;

            // standardize -> scaled inputs for model
            float[] scaled = new float[30];
            for (int i = 0; i < 30; i++) scaled[i] = (raw30[i] - MEAN[i]) / STD[i];

            float probability = runModelAndGetProbability(scaled);

            // label inference
            String label = probability >= 0.5f ? "Malignant" : "Benign";

            // SAVE prediction to Supabase (background)
            savePredictionToSupabase(raw30, probability, label);

            // show result
            Intent intent = new Intent(FormPredictionActivity.this, ResultActivity.class);
            intent.putExtra("result_score", probability);
            intent.putExtra("result_label", label);
            startActivity(intent);
        });
    }

    /**
     * Build a raw 30-length feature vector:
     * - first 10 parsed from UI
     * - remaining 20 filled with dataset MEAN (so standardized -> 0)
     */
    private float[] buildRaw30Array() {
        float[] raw = new float[30];
        try {
            raw[0] = Float.parseFloat(et_radius_mean.getText().toString().trim());
            raw[1] = Float.parseFloat(et_texture_mean.getText().toString().trim());
            raw[2] = Float.parseFloat(et_perimeter_mean.getText().toString().trim());
            raw[3] = Float.parseFloat(et_area_mean.getText().toString().trim());
            raw[4] = Float.parseFloat(et_smoothness_mean.getText().toString().trim());
            raw[5] = Float.parseFloat(et_compactness_mean.getText().toString().trim());
            raw[6] = Float.parseFloat(et_concavity_mean.getText().toString().trim());
            raw[7] = Float.parseFloat(et_concave_points_mean.getText().toString().trim());
            raw[8] = Float.parseFloat(et_symmetry_mean.getText().toString().trim());
            raw[9] = Float.parseFloat(et_fractal_dimension_mean.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "Please enter valid values for all 10 fields.", Toast.LENGTH_SHORT).show();
            return null;
        }

        for (int i = 10; i < 30; i++) raw[i] = MEAN[i]; // use dataset mean
        return raw;
    }

    /**
     * Runs model and returns probability (0..1) for "malignant".
     * Supports single-output or two-output model outputs.
     */
    private float runModelAndGetProbability(float[] scaledInput) {
        try {
            // prepare input buffer
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(30 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());
            for (float v : scaledInput) inputBuffer.putFloat(v);
            inputBuffer.rewind();

            int[] outShape = tflite.getOutputTensor(0).shape();
            int outDim = outShape[outShape.length - 1];

            if (outDim == 1) {
                float[][] out = new float[1][1];
                tflite.run(inputBuffer, out);
                float val = out[0][0];
                // if val outside [0,1] -> apply sigmoid
                if (val < 0f || val > 1f) val = sigmoid(val);
                return val;
            } else {
                float[][] out = new float[1][outDim];
                tflite.run(inputBuffer, out);
                float[] scores = out[0];
                float[] soft = softmax(scores);
                // assume index 1 = malignant
                return soft.length >= 2 ? soft[1] : soft[soft.length - 1];
            }
        } catch (Exception e) {
            Log.e(TAG, "Model run failed", e);
            Toast.makeText(this, "Prediction failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return 0f;
        }
    }

    private float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    private float[] softmax(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) if (v > max) max = v;
        double sum = 0.0;
        double[] exps = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exps[i] = Math.exp(logits[i] - max);
            sum += exps[i];
        }
        float[] out = new float[logits.length];
        for (int i = 0; i < logits.length; i++) out[i] = (float) (exps[i] / sum);
        return out;
    }

    /**
     * Save prediction to Supabase using your SupabaseClient (OkHttp-based).
     * Sends raw feature values (not standardized) so DB columns show actual measurements.
     */
    private void savePredictionToSupabase(final float[] rawFeatures, final float probability, final String label) {
        new Thread(() -> {
            try {
                JsonObject json = new JsonObject();
                // add all 30 features
                for (int i = 0; i < FEATURE_NAMES.length; i++) {
                    json.addProperty(FEATURE_NAMES[i], rawFeatures[i]);
                }
                json.addProperty("result", label);
                json.addProperty("probability", probability);

                // session manager to retrieve token (you already use this elsewhere)
                SessionManager sm = new SessionManager(FormPredictionActivity.this);
                String token = sm.getToken();
                if (token == null || token.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(FormPredictionActivity.this, "Not logged in — prediction not saved.", Toast.LENGTH_SHORT).show());
                    return;
                }

                String userId = SupabaseClient.getUserIdFromToken(token);
                if (userId != null) json.addProperty("user_id", userId);

                boolean ok = SupabaseClient.insertPrediction(json, token);
                runOnUiThread(() -> Toast.makeText(FormPredictionActivity.this, ok ? "Prediction saved" : "Failed to save prediction", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Error saving prediction", e);
                runOnUiThread(() -> Toast.makeText(FormPredictionActivity.this, "Error saving prediction", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) tflite.close();
    }
}
