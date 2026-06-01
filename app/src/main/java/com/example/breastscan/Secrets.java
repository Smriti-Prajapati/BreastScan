package com.example.breastscan;

import android.content.Context;
import java.io.InputStream;
import java.util.Properties;

public class Secrets {

    private static String supabaseUrl;
    private static String supabaseAnonKey;
    private static String medicalQaBackendUrl;

    public static void load(Context context) {
        try {
            Properties props = new Properties();
            InputStream inputStream = context.getAssets().open("local.properties");
            props.load(inputStream);

            supabaseUrl         = props.getProperty("SUPABASE_URL", "");
            supabaseAnonKey     = props.getProperty("SUPABASE_ANON_KEY", "");
            medicalQaBackendUrl = props.getProperty("MEDICAL_QA_BACKEND_URL", "http://10.0.2.2:5000");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getSupabaseUrl() {
        return supabaseUrl;
    }

    public static String getSupabaseAnonKey() {
        return supabaseAnonKey;
    }

    /** Base URL of the deployed Medical Q&A RAG backend (no trailing slash). */
    public static String getMedicalQaBackendUrl() {
        return medicalQaBackendUrl;
    }
}
