package com.example.breastscan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

public class ProfileActivity extends AppCompatActivity {

    ImageView ivProfile;
    ImageButton ibRemovePhoto, ibEditToggle;
    TextView tvName, tvEmail;
    EditText etAge, etBloodGroup, etMedicalHistory, etBmi;
    Button btnSelectImage, btnSave, btnLogout;

    Bitmap profileBitmap = null;
    boolean editingEnabled = false;
    private static final int REQ_PICK_IMAGE = 222;

    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);

        ivProfile = findViewById(R.id.ivProfile);
        ibRemovePhoto = findViewById(R.id.ibRemovePhoto);
        ibEditToggle = findViewById(R.id.ibEditToggle);

        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);

        etAge = findViewById(R.id.etAge);
        etBloodGroup = findViewById(R.id.etBloodGroup);
        etMedicalHistory = findViewById(R.id.etMedicalHistory);
        etBmi = findViewById(R.id.etBmi);

        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSave = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);

        // Load name and email
        tvName.setText(UserSession.name);
        tvEmail.setText(UserSession.email);

        loadLocalProfileIntoViews();

        // Load stored photo
        Bitmap storedBmp = LocalProfileStorage.getPhotoBitmap(this);
        if (storedBmp != null) {
            profileBitmap = storedBmp;
            ivProfile.setImageBitmap(storedBmp);
        } else {
            ivProfile.setImageResource(R.drawable.user_placeholder);
        }

        setEditingEnabled(false);

        ibEditToggle.setOnClickListener(v -> setEditingEnabled(!editingEnabled));

        btnSelectImage.setOnClickListener(v -> {
            if (!editingEnabled) { toastEdit(); return; }
            pickImage();
        });

        // REMOVE PHOTO LOGIC
        ibRemovePhoto.setOnClickListener(v -> {
            if (!editingEnabled) { toastEdit(); return; }

            profileBitmap = null;
            ivProfile.setImageResource(R.drawable.user_placeholder);

            LocalProfileStorage.removePhoto(this);

            Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            if (!editingEnabled) { toastEdit(); return; }
            saveProfileLocally();
        });

        btnLogout.setOnClickListener(v -> {
            sessionManager.logout();
            UserSession.clear();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void toastEdit() {
        Toast.makeText(this, "Tap edit to enable changes", Toast.LENGTH_SHORT).show();
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, REQ_PICK_IMAGE);
    }

    private void setEditingEnabled(boolean enable) {
        editingEnabled = enable;

        ibEditToggle.setImageResource(enable ? R.drawable.ic_edit_on : R.drawable.ic_edit_off);

        etAge.setEnabled(enable);
        etBloodGroup.setEnabled(enable);
        etMedicalHistory.setEnabled(enable);
        etBmi.setEnabled(enable);

        btnSelectImage.setEnabled(enable);
        btnSave.setEnabled(enable);

        // Tint remove button when disabled
        if (enable)
            ibRemovePhoto.setColorFilter(null);
        else
            ibRemovePhoto.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_PICK_IMAGE && res == RESULT_OK && data != null) {
            Uri uri = data.getData();

            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
                    profileBitmap = ImageDecoder.decodeBitmap(src);
                } else {
                    profileBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                }

                ivProfile.setImageBitmap(profileBitmap);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveProfileLocally() {

        LocalProfileStorage.saveProfileFields(
                this,
                etAge.getText().toString(),
                etBloodGroup.getText().toString(),
                etMedicalHistory.getText().toString(),
                etBmi.getText().toString()
        );

        if (profileBitmap != null)
            LocalProfileStorage.savePhotoBitmap(this, profileBitmap);

        Toast.makeText(this, "Profile saved locally", Toast.LENGTH_SHORT).show();
        setEditingEnabled(false);
    }

    private void loadLocalProfileIntoViews() {
        etAge.setText(LocalProfileStorage.getAge(this));
        etBloodGroup.setText(LocalProfileStorage.getBlood(this));
        etMedicalHistory.setText(LocalProfileStorage.getHistory(this));
        etBmi.setText(LocalProfileStorage.getBmi(this));
    }
}
