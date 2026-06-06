package com.example.breastscan;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MedicalQAActivity
 * -----------------
 * Upload a medical PDF → ask plain-language questions → get grounded answers
 * with page-level source citations.
 *
 * Architecture:
 *   Android app  ──►  RAG backend (Flask, deployed on Render/Railway)
 *                          │
 *                          ▼
 *                     ChromaDB (vector store, on the server)
 *                          │
 *                          ▼
 *                     HuggingFace LLM (flan-t5-large, free)
 *
 *   Every Q&A session is also saved to Supabase (medical_qa_sessions table)
 *   so the user's history is persisted alongside the rest of the app's data.
 *
 * Setup:
 *   1. Deploy medical_qa/backend.py (see medical_qa/README.md).
 *   2. Set MEDICAL_QA_BACKEND_URL in assets/local.properties to the deployed URL.
 *      For emulator testing use: http://10.0.2.2:5000
 *
 * Supabase table required (run once in Supabase SQL editor):
 *   CREATE TABLE medical_qa_sessions (
 *       id          uuid DEFAULT gen_random_uuid() PRIMARY KEY,
 *       user_id     uuid REFERENCES auth.users(id) ON DELETE CASCADE,
 *       doc_id      text,
 *       filename    text,
 *       question    text,
 *       answer      text,
 *       source      text,
 *       created_at  timestamptz DEFAULT now()
 *   );
 *   ALTER TABLE medical_qa_sessions ENABLE ROW LEVEL SECURITY;
 *   CREATE POLICY "Users see own sessions"
 *       ON medical_qa_sessions FOR ALL
 *       USING (auth.uid() = user_id);
 *
 * DISCLAIMER: For educational purposes only. Not a substitute for medical advice.
 */
public class MedicalQAActivity extends AppCompatActivity {

    // -----------------------------------------------------------------------
    // Media types
    // -----------------------------------------------------------------------
    private static final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType MEDIA_PDF  = MediaType.parse("application/pdf");

    // -----------------------------------------------------------------------
    // Views
    // -----------------------------------------------------------------------
    private TextView       tvFileName;
    private TextView       tvUploadStatus;
    private MaterialButton btnChoosePdf;
    private MaterialButton btnUpload;
    private MaterialButton btnAsk;
    private EditText       etQuestion;
    private ProgressBar    progressBar;
    private CardView       cardAnswer;
    private TextView       tvAnswer;
    private TextView       tvSource;

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private Uri    selectedPdfUri  = null;  // URI chosen by the user
    private String selectedPdfName = null;  // display name of the chosen file
    private String currentDocId    = null;  // doc_id returned by the RAG backend

    // Supabase + session (loaded from Secrets / SessionManager)
    private String supabaseUrl;
    private String supabaseAnonKey;
    private String authToken;   // user's JWT from SessionManager
    private String userId;      // user's UUID from SessionManager

    // RAG backend base URL (loaded from local.properties via Secrets)
    private String backendBaseUrl;

    // OkHttp — single instance, reused for all requests
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60,  java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180,    java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120,   java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // Single background thread — keeps network calls off the main thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // -----------------------------------------------------------------------
    // File picker
    // -----------------------------------------------------------------------
    private final ActivityResultLauncher<Intent> pdfPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            selectedPdfUri = result.getData().getData();
                            if (selectedPdfUri != null) {
                                selectedPdfName = getFileNameFromUri(selectedPdfUri);
                                tvFileName.setText(selectedPdfName != null ? selectedPdfName : "Selected PDF");
                                tvFileName.setTextColor(getResources().getColor(R.color.pinkPrimary));
                                btnUpload.setEnabled(true);
                                // Reset previous session state
                                currentDocId = null;
                                btnAsk.setEnabled(false);
                                cardAnswer.setVisibility(View.GONE);
                                tvUploadStatus.setVisibility(View.GONE);
                            }
                        }
                    });

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_qa);

        getWindow().setStatusBarColor(getResources().getColor(R.color.pink));

        // Load credentials from Secrets (reads assets/local.properties)
        Secrets.load(this);
        supabaseUrl     = Secrets.getSupabaseUrl();
        supabaseAnonKey = Secrets.getSupabaseAnonKey();
        backendBaseUrl  = Secrets.getMedicalQaBackendUrl();

        // Load user session
        SessionManager session = new SessionManager(this);
        authToken = session.getToken();
        userId    = session.getUserId();

        // Bind views
        tvFileName     = findViewById(R.id.tvFileName);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);
        btnChoosePdf   = findViewById(R.id.btnChoosePdf);
        btnUpload      = findViewById(R.id.btnUpload);
        btnAsk         = findViewById(R.id.btnAsk);
        etQuestion     = findViewById(R.id.etQuestion);
        progressBar    = findViewById(R.id.progressBar);
        cardAnswer     = findViewById(R.id.cardAnswer);
        tvAnswer       = findViewById(R.id.tvAnswer);
        tvSource       = findViewById(R.id.tvSource);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnChoosePdf.setOnClickListener(v -> openFilePicker());
        btnUpload.setOnClickListener(v -> uploadPdf());
        btnAsk.setOnClickListener(v -> askQuestion());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // -----------------------------------------------------------------------
    // File picker helpers
    // -----------------------------------------------------------------------

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pdfPickerLauncher.launch(Intent.createChooser(intent, "Select a PDF"));
    }

    /** Resolve a human-readable filename from a content URI. */
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }

    /** Copy a content URI to a temp file so OkHttp can stream it. */
    private File copyUriToTempFile(Uri uri) throws IOException {
        String name = getFileNameFromUri(uri);
        if (name == null) name = "upload.pdf";
        File tmp = File.createTempFile("medqa_", "_" + name, getCacheDir());
        try (InputStream  in  = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("Cannot open input stream for URI");
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
        return tmp;
    }

    // -----------------------------------------------------------------------
    // Step 1 — Upload PDF to RAG backend
    // -----------------------------------------------------------------------

    private void uploadPdf() {
        if (selectedPdfUri == null) {
            Toast.makeText(this, "Please choose a PDF first.", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        tvUploadStatus.setVisibility(View.VISIBLE);
        tvUploadStatus.setTextColor(getResources().getColor(R.color.colorTextSecondary));
        tvUploadStatus.setText("⏳ Connecting to server… (first request may take ~30s to wake up)");
        cardAnswer.setVisibility(View.GONE);

        executor.execute(() -> {
            File tmp = null;
            try {
                tmp = copyUriToTempFile(selectedPdfUri);

                RequestBody multipart = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", tmp.getName(),
                                RequestBody.create(tmp, MEDIA_PDF))
                        .build();

                Request req = new Request.Builder()
                        .url(backendBaseUrl + "/upload")
                        .post(multipart)
                        .build();

                try (Response resp = httpClient.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        JsonObject json   = JsonParser.parseString(body).getAsJsonObject();
                        String     docId  = json.get("doc_id").getAsString();
                        int        chunks = json.get("chunks_stored").getAsInt();
                        currentDocId = docId;

                        runOnUiThread(() -> {
                            setLoading(false);
                            tvUploadStatus.setText("✅ Processed! " + chunks
                                    + " chunks stored. You can now ask questions.");
                            tvUploadStatus.setTextColor(
                                    getResources().getColor(R.color.green_dark));
                            tvUploadStatus.setVisibility(View.VISIBLE);
                            btnAsk.setEnabled(true);
                        });
                    } else {
                        String err = parseError(body, "Upload failed (HTTP " + resp.code() + ")");
                        runOnUiThread(() -> { setLoading(false); showUploadError(err); });
                    }
                }
            } catch (Exception e) {
                String msg = friendlyNetworkError(e);
                runOnUiThread(() -> { setLoading(false); showUploadError(msg); });
            } finally {
                if (tmp != null && tmp.exists()) //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
            }
        });
    }

    private void showUploadError(String message) {
        tvUploadStatus.setText("❌ " + message);
        tvUploadStatus.setTextColor(getResources().getColor(R.color.red_dark));
        tvUploadStatus.setVisibility(View.VISIBLE);
    }

    // -----------------------------------------------------------------------
    // Step 2 — Ask question, get answer, save to Supabase
    // -----------------------------------------------------------------------

    private void askQuestion() {
        String question = etQuestion.getText().toString().trim();
        if (question.isEmpty()) {
            etQuestion.setError("Please enter a question.");
            return;
        }
        if (currentDocId == null) {
            Toast.makeText(this, "Please upload a document first.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        cardAnswer.setVisibility(View.GONE);

        executor.execute(() -> {
            String answer = "I could not find a reliable answer in the document. "
                          + "Please consult a medical professional.";
            String source = "N/A";

            try {
                // — Call RAG backend /ask —
                JsonObject payload = new JsonObject();
                payload.addProperty("question", question);
                payload.addProperty("doc_id", currentDocId);

                Request req = new Request.Builder()
                        .url(backendBaseUrl + "/ask")
                        .post(RequestBody.create(payload.toString(), MEDIA_JSON))
                        .build();

                try (Response resp = httpClient.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("answer")) answer = json.get("answer").getAsString();
                    if (json.has("source")) source  = json.get("source").getAsString();
                }
            } catch (Exception e) {
                source = "Network error: " + friendlyNetworkError(e);
            }

            // — Save session to Supabase (fire-and-forget, never blocks the UI) —
            saveSessionToSupabase(question, answer, source);

            final String finalAnswer = answer;
            final String finalSource = source;
            runOnUiThread(() -> {
                setLoading(false);
                showAnswer(finalAnswer, finalSource);
            });
        });
    }

    // -----------------------------------------------------------------------
    // Supabase — persist Q&A session
    // -----------------------------------------------------------------------

    /**
     * Inserts one row into the medical_qa_sessions table.
     * Called on the background executor thread — never on the main thread.
     * Failures are logged but never surfaced to the user (non-critical).
     */
    private void saveSessionToSupabase(String question, String answer, String source) {
        if (supabaseUrl == null || supabaseUrl.isEmpty()
                || authToken == null || authToken.isEmpty()) {
            return; // not logged in or secrets not loaded — skip silently
        }

        try {
            JsonObject row = new JsonObject();
            if (userId != null && !userId.isEmpty()) {
                row.addProperty("user_id", userId);
            }
            row.addProperty("doc_id",   currentDocId   != null ? currentDocId   : "");
            row.addProperty("filename", selectedPdfName != null ? selectedPdfName : "");
            row.addProperty("question", question);
            row.addProperty("answer",   answer);
            row.addProperty("source",   source);

            Request req = new Request.Builder()
                    .url(supabaseUrl + "/rest/v1/medical_qa_sessions")
                    .addHeader("apikey",        supabaseAnonKey)
                    .addHeader("Authorization", "Bearer " + authToken)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .post(RequestBody.create(row.toString(), MEDIA_JSON))
                    .build();

            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    android.util.Log.w("MedicalQA",
                            "Supabase insert failed: " + resp.code() + " " + body);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MedicalQA", "saveSessionToSupabase error", e);
        }
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnChoosePdf.setEnabled(!loading);
        btnUpload.setEnabled(!loading && selectedPdfUri != null);
        btnAsk.setEnabled(!loading && currentDocId != null);
    }

    private void showAnswer(String answer, String source) {
        tvAnswer.setText(answer);
        tvSource.setText(source);
        cardAnswer.setVisibility(View.VISIBLE);
        android.widget.ScrollView sv = findViewById(R.id.scrollView);
        if (sv != null) sv.post(() -> sv.smoothScrollTo(0, cardAnswer.getTop()));
    }

    // -----------------------------------------------------------------------
    // Error helpers
    // -----------------------------------------------------------------------

    /** Extract the "error" field from a JSON response body, or return fallback. */
    private String parseError(String body, String fallback) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("error")) return obj.get("error").getAsString();
        } catch (Exception ignored) {}
        return fallback;
    }

    /** Turn a raw network exception into a user-friendly message. */
    private String friendlyNetworkError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("ECONNREFUSED") || msg.contains("connect")
                || msg.contains("Unable to resolve"))) {
            return "Cannot reach the backend.\n"
                 + "• Make sure backend.py is running.\n"
                 + "• Check MEDICAL_QA_BACKEND_URL in assets/local.properties.\n"
                 + "  Emulator → http://10.0.2.2:5000\n"
                 + "  Physical device → http://<your-PC-IP>:5000\n"
                 + "  Deployed → https://your-app.onrender.com";
        }
        return msg != null ? msg : "Unknown network error.";
    }
}
