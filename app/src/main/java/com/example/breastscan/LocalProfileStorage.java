package com.example.breastscan;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;

public class LocalProfileStorage {

    private static final String PREF = "local_profile_pref";
    private static final String KEY_AGE = "age";
    private static final String KEY_BLOOD = "blood";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_BMI = "bmi";

    private static final String PHOTO_FILENAME = "profile_photo.png";

    public static void saveProfileFields(Context ctx, String age, String blood, String history, String bmi) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_AGE, age)
                .putString(KEY_BLOOD, blood)
                .putString(KEY_HISTORY, history)
                .putString(KEY_BMI, bmi)
                .apply();
    }

    public static String getAge(Context ctx) { return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_AGE, ""); }
    public static String getBlood(Context ctx) { return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_BLOOD, ""); }
    public static String getHistory(Context ctx) { return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_HISTORY, ""); }
    public static String getBmi(Context ctx) { return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_BMI, ""); }

    public static void savePhotoBitmap(Context ctx, Bitmap bmp) {
        try {
            File file = new File(ctx.getFilesDir(), PHOTO_FILENAME);
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (Exception ignored) {}
    }

    public static Bitmap getPhotoBitmap(Context ctx) {
        try {
            File file = new File(ctx.getFilesDir(), PHOTO_FILENAME);
            if (!file.exists()) return null;
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Exception e) { return null; }
    }

    public static void removePhoto(Context ctx) {
        File file = new File(ctx.getFilesDir(), PHOTO_FILENAME);
        if (file.exists()) file.delete();
    }
}
