package com.example.breastscan;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
public class ChatbotActivity extends AppCompatActivity {

    TextView tvQuestion, tvResult;
    EditText etAge;
    Button btnYes, btnNo, btnNext, btnHospital;


    int step = 0;
    int score = 0;
    StringBuilder reason = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        tvQuestion = findViewById(R.id.tvQuestion);
        tvResult = findViewById(R.id.tvResult);
        etAge = findViewById(R.id.etAge);
        btnYes = findViewById(R.id.btnYes);
        btnNo = findViewById(R.id.btnNo);
        btnNext = findViewById(R.id.btnNext);
        btnHospital = findViewById(R.id.btnHospital);

        loadQuestion();

        btnHospital.setOnClickListener(v -> {
            Uri uri = Uri.parse("geo:0,0?q=hospitals near me");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        });

        btnYes.setOnClickListener(v -> handleAnswer(true));
        btnNo.setOnClickListener(v -> handleAnswer(false));
        btnNext.setOnClickListener(v -> handleAge());
    }

    private void loadQuestion() {

        etAge.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnYes.setVisibility(View.VISIBLE);
        btnNo.setVisibility(View.VISIBLE);

        switch (step) {

            case 0:
                tvQuestion.setText("Enter your age");
                etAge.setVisibility(View.VISIBLE);
                btnNext.setVisibility(View.VISIBLE);
                btnYes.setVisibility(View.GONE);
                btnNo.setVisibility(View.GONE);
                break;

            case 1:
                tvQuestion.setText("Do you feel any lump?");
                break;

            case 2:
                tvQuestion.setText("Any breast pain?");
                break;

            case 3:
                tvQuestion.setText("Change in size or shape?");
                break;

            case 4:
                tvQuestion.setText("Any nipple discharge?");
                break;

            case 5:
                tvQuestion.setText("Skin changes?");
                break;

            case 6:
                tvQuestion.setText("Family history?");
                break;

            case 7:
                tvQuestion.setText("Recent sudden changes?");
                break;

            default:
                showResult();
        }
    }

    private void handleAge() {
        String ageStr = etAge.getText().toString();

        if (ageStr.isEmpty()) {
            etAge.setError("Enter age");
            return;
        }

        int age = Integer.parseInt(ageStr);

        if (age > 50) {
            score += 2;
            reason.append("• Age above 50\n");
        } else if (age > 35) {
            score += 1;
            reason.append("• Age above 35\n");
        }

        step++;
        loadQuestion();
    }

    private void handleAnswer(boolean yes) {

        switch (step) {

            case 1:
                if (yes) { score += 3; reason.append("• Lump detected\n"); }
                break;

            case 2:
                if (yes) { score += 1; reason.append("• Breast pain\n"); }
                break;

            case 3:
                if (yes) { score += 2; reason.append("• Shape change\n"); }
                break;

            case 4:
                if (yes) { score += 2; reason.append("• Nipple discharge\n"); }
                break;

            case 5:
                if (yes) { score += 2; reason.append("• Skin changes\n"); }
                break;

            case 6:
                if (yes) { score += 2; reason.append("• Family history\n"); }
                break;

            case 7:
                if (yes) { score += 1; reason.append("• Sudden changes\n"); }
                break;
        }

        step++;
        loadQuestion();
    }

    private void showResult() {

        String riskTitle;
        int color;

        if (score >= 8) {
            riskTitle = "HIGH RISK";
            color = 0xFFD32F2F; // Red
        } else if (score >= 4) {
            riskTitle = "MODERATE RISK";
            color = 0xFFF9A825; // Yellow
        } else {
            riskTitle = "LOW RISK";
            color = 0xFF388E3C; // Green
        }

        String result =
                "🔍 Breast Health Assessment Result\n\n" +

                        "Risk Level: " + riskTitle + "\n\n" +

                        "📊 Key Observations:\n" +
                        reason.toString() + "\n" +

                        "🩺 Recommendation:\n" +
                        getRecommendation(score) + "\n\n" +

                        "⚠️ Disclaimer:\n" +
                        "This is a preliminary self-assessment and not a medical diagnosis.\n" +
                        "Please consult a qualified doctor for proper evaluation.";

        tvResult.setText(result);
        tvResult.setTextColor(color);
        tvResult.setVisibility(View.VISIBLE);

        findViewById(R.id.cardQuestion).setVisibility(View.GONE);

        btnHospital.setVisibility(View.VISIBLE);


    }

    private String getRecommendation(int score) {

        if (score >= 8) {
            return "Immediate medical consultation is strongly advised.";
        } else if (score >= 4) {
            return "Monitor symptoms and consult a doctor if conditions persist.";
        } else {
            return "Maintain regular self-check and a healthy lifestyle.";
        }
    }
}