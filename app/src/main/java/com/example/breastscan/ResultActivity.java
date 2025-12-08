package com.example.breastscan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ResultActivity extends AppCompatActivity {

    TextView tvLabel, tvScore, tvMessage;
    Button btnBack;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_result);

        tvLabel = findViewById(R.id.tvLabel);
        tvScore = findViewById(R.id.tvScore);
        tvMessage = findViewById(R.id.tvMessage);
        btnBack = findViewById(R.id.btnBack);

        float score = getIntent().getFloatExtra("result_score", 0f);
        String label = getIntent().getStringExtra("result_label");
        if (label == null) label = (score >= 0.5f) ? "Malignant" : "Benign";

        tvLabel.setText(label);

        if ("Malignant".equals(label)) {
            tvLabel.setTextColor(ContextCompat.getColor(this, R.color.red_dark));
            tvMessage.setText("The model indicates a higher risk of malignancy. Please consult a doctor.");
        } else {
            tvLabel.setTextColor(ContextCompat.getColor(this, R.color.green_dark));
            tvMessage.setText("The model indicates the tumor is likely benign.");
        }

        float confidencePercent = score * 100f;
        tvScore.setText(String.format("Confidence: %.2f%%", confidencePercent));

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(ResultActivity.this, HomeActivity.class));
            finish();
        });
    }
}
