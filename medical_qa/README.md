# Medical Document Q&A — RAG Backend

> ⚠️ **Disclaimer:** For educational purposes only. Not a substitute for professional medical advice.

This is the Python backend for the **Medical Document Q&A** feature inside the BreastScan Android app.  
The Android UI (`MedicalQAActivity.java`) talks to this backend over HTTP.

---

## Architecture

```
Android App (MedicalQAActivity)
        │
        │  POST /upload  (PDF file)
        │  POST /ask     (question + doc_id)
        ▼
  Flask Backend  (this folder — deployed on Render)
        │
        ├── PyMuPDF        → parse PDF pages
        ├── sentence-transformers (all-MiniLM-L6-v2)  → embed chunks locally
        ├── ChromaDB       → store & search embeddings
        └── HuggingFace Inference API (flan-t5-large)  → generate answer
        
        Q&A sessions also saved to Supabase (medical_qa_sessions table)
        by the Android app using the user's existing auth token.
```

---

## Files

| File | Purpose |
|---|---|
| `backend.py` | Flask REST API (`/upload`, `/ask`, `/health`) |
| `rag_pipeline.py` | PDF parsing, chunking, embedding, retrieval, LLM call |
| `requirements.txt` | All Python dependencies |
| `README.md` | This file |

---

## Supabase Table (run once in Supabase SQL Editor)

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

## Deploy to Render (free, no credit card)

1. Push this project to GitHub (the whole `BreastScan2` repo or just `medical_qa/`).
2. Go to [render.com](https://render.com) → **New Web Service** → connect your repo.
3. Set the root directory to `medical_qa`.
4. Set **Build Command:** `pip install -r requirements.txt`
5. Set **Start Command:** `python backend.py`
6. (Optional) Add environment variable `HF_TOKEN` = your HuggingFace token for higher rate limits.
7. Click **Deploy**. Render gives you a URL like `https://breastscan-qa.onrender.com`.
8. Paste that URL into `app/src/main/assets/local.properties`:
   ```
   MEDICAL_QA_BACKEND_URL=https://breastscan-qa.onrender.com
   ```
9. Rebuild and reinstall the Android app.

---

## Local Testing (Android Emulator)

```bash
cd medical_qa
pip install -r requirements.txt
python backend.py
```

In `assets/local.properties` set:
```
MEDICAL_QA_BACKEND_URL=http://10.0.2.2:5000
```
`10.0.2.2` is the Android emulator's alias for your PC's localhost.

For a **physical device on the same Wi-Fi**, use your PC's local IP instead:
```
MEDICAL_QA_BACKEND_URL=http://192.168.x.x:5000
```

---

## API Reference

### `GET /health`
```json
{ "status": "ok" }
```

### `POST /upload`
Multipart form-data, field `file` (PDF ≤ 50 MB).
```json
{ "doc_id": "report_a1b2c3d4", "filename": "report.pdf", "chunks_stored": 47 }
```

### `POST /ask`
```json
// Request
{ "question": "What are the risk factors?", "doc_id": "report_a1b2c3d4" }

// Response
{ "answer": "...", "source": "Page 3 of document '...' (score: 0.87)", "disclaimer": "..." }
```
