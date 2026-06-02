# BreastScan-An AI- Powered Mobile Application for Breast Cancer Prediction

An AI-powered Android app for early breast cancer risk assessment. It combines on-device machine learning, OCR-based report analysis, image classification, and a RAG-powered document Q&A system — all in one clean, dark-mode-ready interface.

> This app is for educational purposes only. It is not a substitute for professional medical advice.

---

## Features

### Quick Self Check
Answer a short series of yes/no questions about your symptoms. The app scores your responses and gives you a risk level — Low, Moderate, or High — along with a recommendation.

### Form-Based Prediction
Enter 10 clinical measurements (radius, texture, perimeter, area, etc.) and the app runs them through an on-device TensorFlow Lite model to predict whether a tumour is likely Benign or Malignant, with a confidence score.

### Image Prediction
Upload a scan image from your gallery or take one with the camera. A CNN model running on-device classifies the image and returns a result with confidence percentage.

### OCR Report Analysis
Photograph or upload a printed medical report. Google ML Kit reads the text, automatically extracts the relevant values, and feeds them into the prediction model — no manual typing needed.

### Medical Document Q&A
Upload any medical PDF — a research paper, discharge summary, or clinical report. Ask questions about it in plain language and get answers grounded in the document's actual content, with a citation showing exactly which page the answer came from. If the system isn't confident, it tells you so instead of guessing. Every session is saved to your account history.

### User Profile
Store your personal health details — age, blood group, BMI, and medical history. Upload a profile photo. All data is securely stored in Supabase.

### Authentication
Sign up and log in with email and password via Supabase Auth. Your session persists across app restarts.

### Educational Content
Read about breast cancer, browse the Privacy Policy and Terms & Conditions, and find nearby hospitals directly from the app.

---

## Tech Stack

| Area | Technology |
|---|---|
| Android | Java, XML, Material Components |
| On-device ML | TensorFlow Lite |
| OCR | Google ML Kit |
| Networking | OkHttp, Gson |
| Auth & Database | Supabase |
| RAG Backend | Python, Flask, ChromaDB |
| Embeddings | sentence-transformers (all-MiniLM-L6-v2) |
| LLM | HuggingFace Inference API (flan-t5-large) |
| PDF Parsing | PyMuPDF |
| Web UI | Streamlit |
| Build | Gradle (Kotlin DSL) |

---

## Project Structure

```
BreastScan2/
├── app/src/main/
│   ├── java/com/example/breastscan/   ← all Android activities
│   ├── assets/                         ← TFLite models + local.properties
│   └── res/                            ← layouts, drawables, themes
│
└── medical_qa/                         ← Python RAG backend
    ├── app.py                          ← Streamlit web UI
    ├── backend.py                      ← Flask REST API
    ├── rag_pipeline.py                 ← chunking, embedding, retrieval, LLM
    └── requirements.txt
```

---

## Getting Started

### Android App

1. Clone the repo and open it in Android Studio
2. Sync Gradle
3. Add your credentials to `app/src/main/assets/local.properties`:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
MEDICAL_QA_BACKEND_URL=https://your-backend.onrender.com
```

4. Run on a device or emulator (API 24+)

---

### Medical Q&A Backend

```bash
cd medical_qa
pip install -r requirements.txt

# Start the Flask API
python backend.py

# Optionally start the Streamlit web UI
streamlit run app.py
```

The Flask API runs at `http://localhost:5000`.
The Streamlit UI runs at `http://localhost:8501`.

To use with a physical Android device, deploy the backend to [Render](https://render.com) (free tier):

- Root directory: `medical_qa`
- Build command: `pip install -r requirements.txt`
- Start command: `python backend.py`

Then paste the Render URL into `local.properties` as `MEDICAL_QA_BACKEND_URL`.


---

## Responsible AI

- All predictions include a confidence score
- Q&A answers always cite the source page and relevance score
- When the model cannot find a reliable answer it returns: *"I could not find a reliable answer in the document. Please consult a medical professional."*
- The LLM is instructed never to fabricate information not present in the uploaded document
- Every screen includes a medical disclaimer

---

## Developed By

Smriti Prajapati
