package com.example.breastscan;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileOutputStream;
import java.io.FileInputStream;

public class LocalProfileStorage {

    private static final String PREF_NAME = "UserProfile";

    // 🔹 SAVE TEXT DATA
    public static void saveProfileFields(Context context, String age, String blood, String history, String bmi) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("age", age);
        editor.putString("blood", blood);
        editor.putString("history", history);
        editor.putString("bmi", bmi);

        editor.apply();
    }

    // 🔹 GET DATA
    public static String getAge(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("age", "");
    }

    public static String getBlood(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("blood", "");
    }

    public static String getHistory(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("history", "");
    }

    public static String getBmi(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("bmi", "");
    }

    // 🔹 SAVE IMAGE
    public static void savePhotoBitmap(Context context, Bitmap bitmap) {
        try {
            FileOutputStream fos = context.openFileOutput("profile.jpg", Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 LOAD IMAGE
    public static Bitmap getPhotoBitmap(Context context) {
        try {
            FileInputStream fis = context.openFileInput("profile.jpg");
            return BitmapFactory.decodeStream(fis);
        } catch (Exception e) {
            return null;
        }
    }

    // 🔹 REMOVE IMAGE
    public static void removePhoto(Context context) {
        context.deleteFile("profile.jpg");
    }
}