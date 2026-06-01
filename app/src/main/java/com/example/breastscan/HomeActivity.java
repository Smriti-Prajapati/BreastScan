package com.example.breastscan;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;

public class HomeActivity extends AppCompatActivity {

    // Feature cards — CardView in the new layout
    CardView btnQuickCheck, btnForm, btnImage, btnMedicalQA;

    ImageView btnMenu;
    DrawerLayout drawerLayout;

    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);

        getWindow().setStatusBarColor(getResources().getColor(R.color.pink));

        // ── Feature cards ──────────────────────────────────────────────
        btnQuickCheck = findViewById(R.id.btnQuickCheck);
        btnForm       = findViewById(R.id.btnForm);
        btnImage      = findViewById(R.id.btnImage);
        btnMedicalQA  = findViewById(R.id.btnMedicalQA);

        // ── Drawer ─────────────────────────────────────────────────────
        btnMenu      = findViewById(R.id.btnMenu);
        drawerLayout = findViewById(R.id.drawerLayout);

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.START));

        // ── Drawer menu items (still LinearLayout) ─────────────────────
        LinearLayout menuProfile = findViewById(R.id.menuProfile);
        LinearLayout menuInfo    = findViewById(R.id.menuInfo);
        LinearLayout menuPrivacy = findViewById(R.id.menuPrivacy);
        LinearLayout menuTerms   = findViewById(R.id.menuTerms);
        LinearLayout menuRate    = findViewById(R.id.menuRate);
        LinearLayout menuShare   = findViewById(R.id.menuShare);
        LinearLayout menuLogout  = findViewById(R.id.menuLogout);

        // ── Feature card actions ────────────────────────────────────────
        btnQuickCheck.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ChatbotActivity.class)));

        btnForm.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, FormPredictionActivity.class)));

        btnImage.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ImagePredictionActivity.class)));

        btnMedicalQA.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, MedicalQAActivity.class)));

        // ── Drawer menu actions ─────────────────────────────────────────
        menuProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        menuInfo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.who.int/news-room/fact-sheets/detail/breast-cancer"));
            startActivity(intent);
        });

        menuPrivacy.setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyActivity.class)));

        menuTerms.setOnClickListener(v ->
                startActivity(new Intent(this, TermsActivity.class)));

        menuRate.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });

        menuShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "Check out BreastScan app for breast cancer prediction:\n\n"
                    + "https://play.google.com/store/apps/details?id=" + getPackageName());
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        });

        menuLogout.setOnClickListener(v -> {
            sessionManager.logout();
            UserSession.clear();
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
