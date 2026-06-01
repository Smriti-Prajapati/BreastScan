# BreastScan — AI Breast Cancer Prediction App

An AI-powered Android application for early breast cancer risk prediction, combining on-device TensorFlow Lite models, OCR report analysis, image-based CNN prediction, a RAG-powered Medical Document Q&A system, and a fully redesigned dark-mode-ready UI.

---

## What's New (Latest Update)

### Medical Document Q&A (RAG Module)
- Upload any medical PDF directly from your phone
- Ask plain-language questions about the document's content
- Receive grounded answers with page-level source citations
- Powered by a Python RAG backend (ChromaDB + HuggingFace flan-t5-large)
- Every Q&A session is saved to Supabase under the user's account
- Responsible AI: fallback message when context is insufficient, no fabrication

### Full UI Redesign + Dark Mode
- Complete UI overhaul across all 14 screens
- Proper dark mode support — all hardcoded hex colors replaced with semantic `@color/` tokens
- `values-night/colors.xml` provides a full dark palette (dark backgrounds, bright pink accents)
- Home screen upgraded with feature cards showing icons, subtitles, and chevron arrows
- All buttons migrated to `MaterialButton` with consistent corner radius and ripple
- All content blocks wrapped in `CardView` with elevation and rounded corners
- Login and Register screens now include the app logo and a cleaner card layout
- Chatbot YES/NO buttons differentiated — YES filled pink, NO outlined

---

## All Features

### Prediction & Analysis
- **Quick Self Check** — symptom-based questionnaire with risk scoring (Low / Moderate / High)
- **Form Prediction** — enter 10 clinical measurements; TFLite tabular model predicts Benign/Malignant
- **Image Prediction** — upload a scan or use the camera; CNN TFLite model classifies the image
- **OCR Report Analysis** — photograph or upload a medical report; Google ML Kit extracts values automatically and feeds them into the prediction model

### Medical Document Q&A (RAG)
- Upload a medical PDF (research paper, discharge summary, clinical report)
- Backend parses, chunks, and embeds the document using `sentence-transformers/all-MiniLM-L6-v2`
- Semantic search retrieves the most relevant chunks via ChromaDB
- `google/flan-t5-large` (free HuggingFace Inference API) generates a grounded answer
- Answer includes the source page number and relevance score
- Sessions stored in Supabase `medical_qa_sessions` table per user

### User & App
- Secure email/password authentication via Supabase Auth
- User profile with avatar, age, blood group, BMI, medical history
- Drawer navigation with Profile, Info, Privacy Policy, Terms, Rate Us, Share, Logout
- Breast Cancer Info screen with educational content
- Privacy Policy and Terms & Conditions screens
- Result screens with confidence score display

---

## Tech Stack

| Layer | Technology |
|---|---|
| Android UI | Java, XML, Material Components 1.12 |
| On-device ML | TensorFlow Lite 2.12 |
| OCR | Google ML Kit Text Recognition |
| Networking | OkHttp 4.12, Gson 2.10 |
| Image loading | Glide 4.15, Picasso 2.8 |
| Auth & Database | Supabase (PostgreSQL + Auth) |
| RAG Backend | Python, Flask, ChromaDB, sentence-transformers |
| LLM | HuggingFace Inference API (google/flan-t5-large) |
| PDF parsing | PyMuPDF |
| Streamlit UI | Streamlit 1.37 (standalone web UI for the RAG module) |
| Build | Gradle (Kotlin DSL) |
| IDE | Android Studio |

---

## Project Structure

```
BreastScan2/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/breastscan/
│   │   │   ├── SplashActivity.java
│   │   │   ├── LoginActivity.java
│   │   │   ├── RegisterActivity.java
│   │   │   ├── HomeActivity.java
│   │   │   ├── ChatbotActivity.java          ← Quick Self Check
│   │   │   ├── FormPredictionActivity.java
│   │   │   ├── ImagePredictionActivity.java
│   │   │   ├── ImageResultActivity.java
│   │   │   ├── OCRReportActivity.java
│   │   │   ├── ResultActivity.java
│   │   │   ├── MedicalQAActivity.java        ← NEW: RAG Q&A
│   │   │   ├── ProfileActivity.java
│   │   │   ├── InfoActivity.java
│   │   │   ├── PrivacyActivity.java
│   │   │   ├── TermsActivity.java
│   │   │   ├── SupabaseClient.java
│   │   │   ├── SessionManager.java
│   │   │   ├── Secrets.java                  ← loads local.properties
│   │   │   ├── TFLiteHelper.java
│   │   │   └── UserSession.java
│   │   ├── assets/
│   │   │   ├── breast_tabular_model.tflite
│   │   │   ├── image_model.tflite
│   │   │   └── local.properties              ← Supabase + backend URLs
│   │   └── res/
│   │       ├── layout/                       ← 14 activity layouts
│   │       ├── drawable/                     ← icons, backgrounds, shapes
│   │       ├── values/colors.xml             ← light mode semantic tokens
│   │       ├── values-night/colors.xml       ← dark mode semantic tokens
│   │       └── values/themes.xml             ← Material theme + button/card styles
│
└── medical_qa/                               ← NEW: Python RAG backend
    ├── app.py                                ← Streamlit web UI
    ├── backend.py                            ← Flask REST API
    ├── rag_pipeline.py                       ← chunking, embedding, retrieval, LLM
    ├── requirements.txt
    └── README.md
```

---

## Setup & Run

### Android App

#### Prerequisites
- Android Studio (latest)
- Android SDK (API 24+)
- Java 11

#### Steps

```bash
git clone https://github.com/Smriti-Prajapati/BreastScan.git
cd BreastScan
```

1. Open the project in Android Studio
2. Sync Gradle
3. Add your credentials to `app/src/main/assets/local.properties`:
   ```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   MEDICAL_QA_BACKEND_URL=https://your-backend.onrender.com
   ```
4. Connect an Android device or start an emulator
5. Run the app

---

### Medical Q&A Python Backend

#### Install dependencies

```bash
cd medical_qa
pip install -r requirements.txt
```

#### Run the Flask backend

```bash
python backend.py
```

Starts at `http://localhost:5000`

#### Run the Streamlit web UI (optional — standalone browser UI)

```bash
streamlit run app.py
```

Opens at `http://localhost:8501`

#### Deploy to Render (free, no credit card)

1. Push the repo to GitHub
2. Go to [render.com](https://render.com) → New Web Service → connect repo
3. Set Root Directory: `medical_qa`
4. Build Command: `pip install -r requirements.txt`
5. Start Command: `python backend.py`
6. Copy the deployed URL into `local.properties` as `MEDICAL_QA_BACKEND_URL`

---

## Supabase Setup

Run this SQL once in your Supabase SQL Editor to enable Q&A session history:

```sql
CREATE TABLE medical_qa_sessions (
    id          uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    doc_id      text,
    filename    text,
    question    text,
    answer      text,
    source      text,
    created_at  timestamptz DEFAULT now()
);
ALTER TABLE medical_qa_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users see own sessions"
    ON medical_qa_sessions FOR ALL
    USING (auth.uid() = user_id);
```

---

## App Workflow

```
User opens app
    │
    ├── Quick Self Check  → symptom questions → risk score
    ├── Form Prediction   → clinical values   → TFLite → Benign/Malignant
    ├── Image Prediction  → scan image        → CNN TFLite → result
    ├── OCR Report        → photo/upload      → ML Kit OCR → auto-fill → predict
    └── Medical Q&A       → upload PDF        → RAG backend → answer + source
                                                    │
                                                    └── saved to Supabase
```

---

## Dark Mode

The app fully supports Android dark mode. Toggle it in your phone's display settings — all screens adapt automatically:

| Element | Light Mode | Dark Mode |
|---|---|---|
| Background | `#FFF1F4` (soft pink-white) | `#0F0F0F` (near black) |
| Cards | `#FFFFFF` | `#1E1E1E` |
| Input fields | `#FFFFFF` | `#2A2A2A` |
| Primary text | `#1A1A1A` | `#F0F0F0` |
| Brand pink | `#C2185B` | `#E91E63` (brighter) |

---

## Responsible AI

- Every prediction includes a confidence score
- Every Q&A answer includes the source page and relevance score
- If the RAG model cannot find a reliable answer: *"I could not find a reliable answer in the document. Please consult a medical professional."*
- The model is instructed never to fabricate information not present in the document
- All screens include a medical disclaimer

---

## Limitations

- Prediction accuracy depends on input data quality and model training
- OCR may fail on low-quality or handwritten reports
- RAG Q&A requires the backend to be running (or deployed)
- Scanned/image-based PDFs are not supported by the RAG module (text PDFs only)
- Not a replacement for professional medical diagnosis

---

## Future Enhancements

- Improve model accuracy with larger datasets
- iOS version
- Multilingual support
- OCR support for scanned PDFs in the RAG module
- Telemedicine integration
- Push notifications for health reminders

---

## Developed By

**Smriti Prajapati**
📧 smritiprajapati15@gmail.com

---

> ⚠️ **Disclaimer:** This application is intended for educational and preliminary assessment purposes only. It is not a substitute for professional medical advice, diagnosis, or treatment. Always consult a qualified healthcare provider.
