package com.example.breastscan;

import android.content.Context;
import okhttp3.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
public class SupabaseClient {

    // ❗ These must NOT be final or hardcoded
    private static String SUPABASE_URL;
    private static String SUPABASE_ANON_KEY;


    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Logger LOG = Logger.getLogger("SupabaseClient");

    // 🔥 Load values from Secrets.java
    public static void init(Context context) {
        Secrets.load(context);
        SUPABASE_URL = Secrets.getSupabaseUrl();
        SUPABASE_ANON_KEY = Secrets.getSupabaseAnonKey();
    }

    private static Response doRequest(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    // USER REGISTRATION
    public static boolean registerUser(String email, String password, String fullName) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("email", email);
            json.addProperty("password", password);

            JsonObject data = new JsonObject();
            data.addProperty("full_name", fullName);
            json.add("data", data);

            RequestBody body = RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/signup")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = doRequest(request)) {
                String respStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "registerUser failed: code=" + response.code() + " body=" + respStr);
                    return false;
                }

                JsonObject obj = JsonParser.parseString(respStr).getAsJsonObject();

                if (!obj.has("access_token")) {
                    return true;
                }

                String token = obj.get("access_token").getAsString();
                String userId = obj.get("user").getAsJsonObject().get("id").getAsString();
                createProfileAuth(userId, fullName, email, token);
                return true;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "registerUser exception", e);
            return false;
        }
    }

    // LOGIN
    public static String loginUser(String email, String password) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("email", email);
            json.addProperty("password", password);

            RequestBody body = RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = doRequest(request)) {
                String respStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "loginUser failed: code=" + response.code() + " body=" + respStr);
                    return null;
                }

                JsonObject obj = JsonParser.parseString(respStr).getAsJsonObject();
                if (obj.has("access_token")) return obj.get("access_token").getAsString();
                return null;
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "loginUser exception", e);
            return null;
        }
    }

    // GET USER DETAILS
    public static JsonObject getUserFromToken(String token) {
        try {
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/user")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .get()
                    .build();

            try (Response response = doRequest(request)) {
                String respStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "getUserFromToken failed: code=" + response.code() + " body=" + respStr);
                    return null;
                }
                return JsonParser.parseString(respStr).getAsJsonObject();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "getUserFromToken exception", e);
            return null;
        }
    }

    public static String getUserIdFromToken(String token) {
        JsonObject user = getUserFromToken(token);
        if (user == null) return null;
        if (user.has("id")) return user.get("id").getAsString();
        return null;
    }

    // CREATE PROFILE
    public static void createProfileAuth(String userId, String fullName, String email, String token) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("id", userId);
            json.addProperty("full_name", fullName);
            json.addProperty("email", email);

            RequestBody body = RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/profiles")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(body)
                    .build();

            try (Response response = doRequest(request)) {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "createProfileAuth failed: code=" + response.code() + " body=" + resp);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "createProfileAuth exception", e);
        }
    }

    // FETCH PROFILE
    public static JsonObject fetchProfile(String userId, String token) {
        try {
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId + "&select=*")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .get()
                    .build();

            try (Response response = doRequest(request)) {
                String respStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "fetchProfile failed: code=" + response.code() + " body=" + respStr);
                    return null;
                }
                JsonArray arr = JsonParser.parseString(respStr).getAsJsonArray();
                if (arr.size() == 0) return null;
                return arr.get(0).getAsJsonObject();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "fetchProfile exception", e);
            return null;
        }
    }

    // UPDATE PROFILE
    public static boolean updateProfile(String userId, JsonObject updateFields, String token) {
        try {
            RequestBody body = RequestBody.create(updateFields.toString(), JSON);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(body)
                    .build();

            try (Response response = doRequest(request)) {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "updateProfile failed: code=" + response.code() + " body=" + resp);
                }
                return response.isSuccessful();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "updateProfile exception", e);
            return false;
        }
    }

    // INSERT PREDICTION
    public static boolean insertPrediction(JsonObject predictionJson, String token) {
        try {
            RequestBody body = RequestBody.create(predictionJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/predictions")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .post(body)
                    .build();

            try (Response response = doRequest(request)) {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    LOG.log(Level.WARNING, "insertPrediction failed: code=" + response.code() + " body=" + resp);
                }
                return response.isSuccessful();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "insertPrediction exception", e);
            return false;
        }
    }

    public static void saveUserProfile(String userId, String token,
                                       String age, String blood,
                                       String history, String bmi) {

        try {
            URL url = new URL(SUPABASE_URL + "/rest/v1/profiles");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");

            conn.setDoOutput(true);

            // ✅ convert types properly
            int ageInt = age.isEmpty() ? 0 : Integer.parseInt(age);
            double bmiDouble = bmi.isEmpty() ? 0.0 : Double.parseDouble(bmi);

            String json = "{"
                    + "\"id\":\"" + userId + "\","
                    + "\"age\":" + ageInt + ","   // ✅ NO quotes
                    + "\"blood_group\":\"" + blood + "\","
                    + "\"medical_history\":\"" + history + "\","
                    + "\"bmi\":" + bmiDouble      // ✅ NO quotes
                    + "}";

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes());
            os.close();

            int responseCode = conn.getResponseCode();
            System.out.println("Response: " + responseCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
