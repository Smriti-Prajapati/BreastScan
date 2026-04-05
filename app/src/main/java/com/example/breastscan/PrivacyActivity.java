package com.example.breastscan;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class PrivacyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_privacy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}