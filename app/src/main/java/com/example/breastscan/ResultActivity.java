package com.example.breastscan;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ResultActivity extends AppCompatActivity {

    TextView tvLabel, tvScore, tvMessage, tvResultType;
    Button btnBack;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_result);

        tvLabel = findViewById(R.id.tvLabel);
        tvScore = findViewById(R.id.tvScore);
        tvMessage = findViewById(R.id.tvMessage);
        tvResultType = findViewById(R.id.tvResultType);
        btnBack = findViewById(R.id.btnBack);

        // ✅ GET DATA FROM INTENT
        float score = getIntent().getFloatExtra("result_score", 0f);
        String label = getIntent().getStringExtra("result_label");

        if (label == null) {
            label = (score >= 0.5f) ? "Malignant" : "Benign";
        }

        // ✅ SET RESULT TYPE (UI)
        if ("Malignant".equalsIgnoreCase(label)) {
            tvResultType.setText("Malignant");
            tvResultType.setTextColor(Color.RED);
        } else {
            tvResultType.setText("Benign");
            tvResultType.setTextColor(Color.parseColor("#2E7D32"));
        }

        // ✅ MAIN LABEL
        tvLabel.setText("Prediction Result");

        if ("Malignant".equalsIgnoreCase(label)) {
            tvLabel.setTextColor(ContextCompat.getColor(this, R.color.red_dark));
            tvMessage.setText("The model indicates a higher risk of malignancy. Please consult a doctor.");
        } else {
            tvLabel.setTextColor(ContextCompat.getColor(this, R.color.green_dark));
            tvMessage.setText("The model indicates the tumor is likely benign.");
        }

        // ✅ CONFIDENCE
        float confidencePercent = score * 100f;
        tvScore.setText(String.format("Confidence: %.2f%%", confidencePercent));

        // ✅ BACK BUTTON
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(ResultActivity.this, HomeActivity.class));
            finish();
        });
    }
}