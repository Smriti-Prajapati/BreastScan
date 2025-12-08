package com.example.breastscan;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ImageView ivTogglePwd; // 👈 added

    private boolean isPasswordVisible = false; // 👈 added

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        ivTogglePwd = findViewById(R.id.ivTogglePwd); // 👈 added

        // 👇 PASSWORD TOGGLE LOGIC (ONLY THIS IS NEW)
        ivTogglePwd.setOnClickListener(v -> {
            if (isPasswordVisible) {
                // hide password
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivTogglePwd.setImageResource(R.drawable.ic_eye_off); // closed eye icon
                isPasswordVisible = false;
            } else {
                // show password
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivTogglePwd.setImageResource(R.drawable.ic_eye_on); // open eye icon
                isPasswordVisible = true;
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> doLogin());
        tvRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please enter email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {

            String token = SupabaseClient.loginUser(email, pass);

            runOnUiThread(() -> btnLogin.setEnabled(true));

            if (token != null) {

                JsonObject user = SupabaseClient.getUserFromToken(token);

                // temp holders
                String tempUserId = "";
                String tempFullName = "";
                String tempEmail = "";

                if (user != null) {
                    if (user.has("id"))
                        tempUserId = user.get("id").getAsString();

                    if (user.has("email"))
                        tempEmail = user.get("email").getAsString();

                    if (user.has("user_metadata")) {
                        JsonObject meta = user.getAsJsonObject("user_metadata");
                        if (meta.has("full_name"))
                            tempFullName = meta.get("full_name").getAsString();
                    }
                }

                // Save to SharedPreferences
                SessionManager sm = new SessionManager(LoginActivity.this);
                sm.saveToken(token);
                sm.saveUserId(tempUserId);
                sm.saveName(tempFullName);
                sm.saveEmail(tempEmail);

                // Save to in-memory session
                UserSession.id = tempUserId;
                UserSession.name = tempFullName;
                UserSession.email = tempEmail;

                // Fetch profile
                String finalUserId = tempUserId;
                String finalToken = token;

                new Thread(() -> {
                    JsonObject profile = SupabaseClient.fetchProfile(finalUserId, finalToken);
                }).start();

                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    finish();
                });

            } else {
                runOnUiThread(() ->
                        Toast.makeText(LoginActivity.this, "Invalid email or password.", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}
