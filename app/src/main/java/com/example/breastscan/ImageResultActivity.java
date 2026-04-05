package com.example.breastscan;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ImageResultActivity extends AppCompatActivity {

    TextView tvResultType, tvScore, tvMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_result);

        tvResultType = findViewById(R.id.tvResultType);
        tvScore = findViewById(R.id.tvScore);
        tvMessage = findViewById(R.id.tvMessage);

        // ✅ Get data
        String result = getIntent().getStringExtra("result");

        if (result == null) result = "Unknown";

        tvResultType.setText(result);

        // 🎨 Color + Message
        if (result.toLowerCase().contains("malignant")) {
            tvResultType.setTextColor(Color.RED);
            tvMessage.setText("⚠️ Possible cancer detected.\nPlease consult a doctor immediately.");
        }
        else if (result.toLowerCase().contains("benign")) {
            tvResultType.setTextColor(Color.parseColor("#2E7D32"));
            tvMessage.setText("✅ No cancer detected.\nRegular check-up recommended.");
        }
        else {
            tvResultType.setTextColor(Color.GRAY);
            tvMessage.setText("ℹ️ Unable to determine clearly.");
        }

        // ✅ Optional confidence extraction (if present in string)
        tvScore.setText(result);
    }
}