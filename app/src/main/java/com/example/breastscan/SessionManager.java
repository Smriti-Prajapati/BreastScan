package com.example.breastscan;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF = "breastscan_pref";
    private final SharedPreferences sp;

    public SessionManager(Context context) {
        sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void saveToken(String token) {
        sp.edit().putString("token", token).apply();
    }

    public String getToken() {
        return sp.getString("token", null);
    }

    public void saveUserId(String id) {
        sp.edit().putString("user_id", id).apply();
    }

    public String getUserId() {
        return sp.getString("user_id", null);
    }

    public void saveName(String name) {
        sp.edit().putString("name", name).apply();
    }

    public String getName() {
        return sp.getString("name", "");
    }

    public void saveEmail(String email) {
        sp.edit().putString("email", email).apply();
    }

    public String getEmail() {
        return sp.getString("email", "");
    }

    // 🔥 LOGOUT FUNCTION — Correct
    public void logout() {
        sp.edit().clear().apply();
    }

    // Optional clear()
    public void clear() {
        sp.edit().clear().apply();
    }
}
