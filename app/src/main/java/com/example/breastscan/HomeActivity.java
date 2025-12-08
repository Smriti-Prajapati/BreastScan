package com.example.breastscan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    Button btnForm, btnImage;
    ImageView btnProfile, btnLogout;
    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);

        btnForm = findViewById(R.id.btnForm);
        btnImage = findViewById(R.id.btnImage);
        btnProfile = findViewById(R.id.btnProfile);
        btnLogout = findViewById(R.id.btnLogout);

        btnForm.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, FormPredictionActivity.class)));

        btnImage.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ImagePredictionActivity.class)));

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class)));

        btnLogout.setOnClickListener(v -> {
            sessionManager.logout();     // ✔ FIXED
            UserSession.clear();

            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
