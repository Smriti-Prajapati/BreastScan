"""
backend.py
----------
Flask REST API for the Medical Document Q&A RAG module.

Endpoints:
  GET  /health  — health check
  POST /upload  — upload a PDF; parse, chunk, embed, store in ChromaDB
  POST /ask     — ask a question; retrieve chunks, call LLM, return answer

Deployment (Render.com — free tier, no credit card):
  1. Push the medical_qa/ folder to a GitHub repo.
  2. Create a new "Web Service" on https://render.com
  3. Set Build Command:  pip install -r requirements.txt
  4. Set Start Command:  python backend.py
  5. Copy the deployed HTTPS URL into assets/local.properties:
       MEDICAL_QA_BACKEND_URL=https://your-app.onrender.com

Local testing (Android emulator):
  python backend.py
  → http://10.0.2.2:5000  (emulator alias for localhost)

NOTE: For educational purposes only. Not a substitute for medical advice.
"""

import os
import uuid
import tempfile

from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename

from rag_pipeline import process_pdf, generate_answer

# ---------------------------------------------------------------------------
# App setup
# ---------------------------------------------------------------------------

app = Flask(__name__)
CORS(app)  # allow requests from any origin (needed for mobile clients)

app.config["MAX_CONTENT_LENGTH"] = 50 * 1024 * 1024  # 50 MB max upload

ALLOWED_EXTENSIONS = {"pdf"}


def _allowed_file(filename: str) -> bool:
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_EXTENSIONS


# In-memory doc registry (doc_id → original filename).
# On Render's free tier the server restarts periodically, so this is intentionally
# lightweight. For persistence, swap this dict for a Supabase/Postgres table.
_doc_registry: dict[str, str] = {}


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "message": "Medical Q&A backend is running."}), 200


@app.route("/upload", methods=["POST"])
def upload_pdf():
    """
    Upload and process a medical PDF.

    Request  : multipart/form-data  →  field "file" (PDF, max 50 MB)
    Response : { doc_id, filename, chunks_stored, message }
    """
    if "file" not in request.files:
        return jsonify({"error": "No file part. Use field name 'file'."}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"error": "No file selected."}), 400
    if not _allowed_file(file.filename):
        return jsonify({"error": "Only PDF files are accepted."}), 400

    safe_name = secure_filename(file.filename)
    doc_id    = f"{os.path.splitext(safe_name)[0]}_{uuid.uuid4().hex[:8]}"
    tmp_path  = None

    try:
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
            tmp_path = tmp.name
            file.save(tmp_path)

        chunks_stored = process_pdf(tmp_path, doc_id)
        _doc_registry[doc_id] = safe_name

        return jsonify({
            "doc_id":        doc_id,
            "filename":      safe_name,
            "chunks_stored": chunks_stored,
            "message":       "Document processed. You can now ask questions.",
        }), 200

    except ValueError as ve:
        return jsonify({"error": str(ve)}), 400
    except Exception as exc:
        app.logger.error(f"Upload error: {exc}", exc_info=True)
        return jsonify({"error": "Internal error while processing the PDF."}), 500
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)


@app.route("/ask", methods=["POST"])
def ask_question():
    """
    Ask a question about an uploaded document.

    Request  : { "question": "...", "doc_id": "..." (optional) }
    Response : { question, answer, source, disclaimer }
    """
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"error": "Request body must be JSON."}), 400

    question = data.get("question", "").strip()
    if not question:
        return jsonify({"error": "'question' field is required."}), 400

    doc_id = data.get("doc_id", None)

    try:
        result = generate_answer(question, doc_id=doc_id)
        return jsonify({
            "question":   question,
            "answer":     result["answer"],
            "source":     result["source"],
            "disclaimer": (
                "This answer is generated from the uploaded document for educational "
                "purposes only and is NOT a substitute for professional medical advice."
            ),
        }), 200

    except Exception as exc:
        app.logger.error(f"Ask error: {exc}", exc_info=True)
        return jsonify({
            "answer": (
                "I could not find a reliable answer in the document. "
                "Please consult a medical professional."
            ),
            "source": "N/A",
        }), 500


@app.route("/documents", methods=["GET"])
def list_documents():
    docs = [{"doc_id": k, "filename": v} for k, v in _doc_registry.items()]
    return jsonify({"documents": docs}), 200


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    # Render sets the PORT environment variable automatically.
    # Locally it defaults to 5000.
    port = int(os.environ.get("PORT", 5000))
    print(f"Starting Medical Q&A backend on port {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
