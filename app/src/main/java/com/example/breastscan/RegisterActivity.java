package com.example.breastscan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    EditText etName, etEmail, etPassword;
    Button btnRegister;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> doRegister());
    }

    public void onRegisterClick(android.view.View v) { doRegister(); }

    private void doRegister() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        // ⭐ 1. Check empty fields
        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ⭐ 2. NEW CHECK: Password must be 6+ characters
        if (pass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegister.setEnabled(false);
        Toast.makeText(this, "Registering...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean success = SupabaseClient.registerUser(email, pass, name);

            runOnUiThread(() -> {
                btnRegister.setEnabled(true);
                if (success) {
                    Toast.makeText(RegisterActivity.this, "Registered! Please login.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this, "Registration failed!", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
