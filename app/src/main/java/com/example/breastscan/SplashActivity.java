package com.example.breastscan;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);

        // 🔥 IMPORTANT: Initialize Supabase here
        SupabaseClient.init(this);

        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }, 2000);
    }
}
