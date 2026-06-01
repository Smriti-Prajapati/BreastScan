"""
app.py
------
Streamlit web UI for the Medical Document Q&A module.

This frontend communicates with the Flask backend (backend.py) via HTTP.
Make sure the backend is running before launching this UI:

    # Terminal 1 — start the Flask backend
    python backend.py

    # Terminal 2 — start the Streamlit UI
    streamlit run app.py

NOTE: This tool is for educational purposes only and is NOT a substitute
      for professional medical advice.
"""

import requests
import streamlit as st

# ---------------------------------------------------------------------------
# Configuration — update if your backend runs on a different host/port
# ---------------------------------------------------------------------------
BACKEND_URL   = "http://localhost:5000"
UPLOAD_URL    = f"{BACKEND_URL}/upload"
ASK_URL       = f"{BACKEND_URL}/ask"
HEALTH_URL    = f"{BACKEND_URL}/health"

# ---------------------------------------------------------------------------
# Page setup
# ---------------------------------------------------------------------------
st.set_page_config(
    page_title="Medical Document Q&A",
    page_icon="🩺",
    layout="centered",
)

# ---------------------------------------------------------------------------
# Custom CSS — clean, readable styling
# ---------------------------------------------------------------------------
st.markdown("""
<style>
    /* Page title */
    .main-title  { font-size:2rem; font-weight:700; color:#C2185B; margin-bottom:0.2rem; }
    .sub-title   { font-size:1rem; color:#555; margin-bottom:1.5rem; }

    /* Answer box */
    .answer-box  {
        background:#f0f8ff;
        border-left:4px solid #C2185B;
        padding:1rem 1.2rem;
        border-radius:6px;
        margin-top:1rem;
    }

    /* Source citation */
    .source-box  {
        background:#f9f9f9;
        border-left:4px solid #aaa;
        padding:0.6rem 1rem;
        border-radius:6px;
        font-size:0.85rem;
        color:#444;
        margin-top:0.5rem;
    }

    /* Warning banner */
    .warning-banner {
        background:#fff3cd;
        border:1px solid #ffc107;
        border-radius:6px;
        padding:0.7rem 1rem;
        font-size:0.88rem;
        color:#856404;
        margin-bottom:1rem;
    }

    /* Footer disclaimer */
    .disclaimer  {
        font-size:0.78rem;
        color:#888;
        margin-top:2rem;
        border-top:1px solid #ddd;
        padding-top:0.8rem;
        text-align:center;
    }
</style>
""", unsafe_allow_html=True)

# ---------------------------------------------------------------------------
# Header
# ---------------------------------------------------------------------------
st.markdown('<div class="main-title">🩺 Medical Document Q&amp;A</div>', unsafe_allow_html=True)
st.markdown(
    '<div class="sub-title">Upload a medical PDF and ask plain-language questions about its content.</div>',
    unsafe_allow_html=True,
)
st.markdown(
    '<div class="warning-banner">'
    "⚠️ <strong>Important:</strong> This tool uses AI to extract information from documents. "
    "Answers may not be complete or accurate. Always verify with a qualified healthcare professional."
    "</div>",
    unsafe_allow_html=True,
)

# ---------------------------------------------------------------------------
# Session state
# ---------------------------------------------------------------------------
if "doc_id"   not in st.session_state: st.session_state.doc_id   = None
if "filename" not in st.session_state: st.session_state.filename = None
if "answer"   not in st.session_state: st.session_state.answer   = None
if "source"   not in st.session_state: st.session_state.source   = None
if "error"    not in st.session_state: st.session_state.error    = None

# ---------------------------------------------------------------------------
# Step 1 — Upload PDF
# ---------------------------------------------------------------------------
st.subheader("Step 1 — Upload a Medical PDF")

uploaded_file = st.file_uploader(
    label="Choose a PDF file (max 50 MB)",
    type=["pdf"],
    help="Upload a medical report, research paper, or clinical document.",
)

if uploaded_file is not None:
    col1, col2 = st.columns([3, 1])
    with col1:
        st.info(f"Selected: **{uploaded_file.name}** ({uploaded_file.size / 1024:.1f} KB)")
    with col2:
        process_btn = st.button("📤 Process PDF", use_container_width=True)

    if process_btn:
        with st.spinner("Parsing, chunking, and embedding the document… this may take a moment."):
            try:
                files    = {"file": (uploaded_file.name, uploaded_file.getvalue(), "application/pdf")}
                response = requests.post(UPLOAD_URL, files=files, timeout=120)

                if response.status_code == 200:
                    data = response.json()
                    st.session_state.doc_id   = data["doc_id"]
                    st.session_state.filename = data["filename"]
                    st.session_state.answer   = None
                    st.session_state.source   = None
                    st.session_state.error    = None
                    st.success(
                        f"✅ Document processed! "
                        f"**{data['chunks_stored']}** text chunks stored. "
                        f"You can now ask questions below."
                    )
                else:
                    err = response.json().get("error", "Unknown error from backend.")
                    st.error(f"❌ Upload failed: {err}")

            except requests.exceptions.ConnectionError:
                st.error(
                    "❌ Cannot connect to the backend. "
                    "Make sure `python backend.py` is running on port 5000."
                )
            except Exception as exc:
                st.error(f"❌ Unexpected error: {exc}")

# Show active document
if st.session_state.doc_id:
    st.caption(
        f"📄 Active document: **{st.session_state.filename}** "
        f"(ID: `{st.session_state.doc_id}`)"
    )

# ---------------------------------------------------------------------------
# Step 2 — Ask a Question
# ---------------------------------------------------------------------------
st.subheader("Step 2 — Ask a Question")

question = st.text_input(
    label="Enter your question",
    placeholder="e.g. What are the main risk factors mentioned in this report?",
    help="Ask a plain-language question about the uploaded document.",
)

ask_btn = st.button("🔍 Get Answer", type="primary")

if ask_btn:
    if not question.strip():
        st.warning("Please enter a question before clicking 'Get Answer'.")
    elif not st.session_state.doc_id:
        st.warning("Please upload and process a PDF document first (Step 1).")
    else:
        with st.spinner("Searching the document and generating an answer…"):
            try:
                payload  = {
                    "question": question.strip(),
                    "doc_id":   st.session_state.doc_id,
                }
                response = requests.post(ASK_URL, json=payload, timeout=60)

                if response.status_code == 200:
                    data = response.json()
                    st.session_state.answer = data.get("answer", "")
                    st.session_state.source = data.get("source", "")
                    st.session_state.error  = None
                else:
                    st.session_state.error  = response.json().get("error", "Unknown error.")
                    st.session_state.answer = None
                    st.session_state.source = None

            except requests.exceptions.ConnectionError:
                st.session_state.error = (
                    "Cannot connect to the backend. "
                    "Make sure `python backend.py` is running on port 5000."
                )
            except Exception as exc:
                st.session_state.error = str(exc)

# ---------------------------------------------------------------------------
# Step 3 — Display Answer & Source
# ---------------------------------------------------------------------------
if st.session_state.error:
    st.error(f"❌ {st.session_state.error}")

if st.session_state.answer:
    st.subheader("Answer")

    # Answer box
    st.markdown(
        f'<div class="answer-box">{st.session_state.answer}</div>',
        unsafe_allow_html=True,
    )

    # Source citation
    if st.session_state.source:
        st.markdown(
            f'<div class="source-box">📌 <strong>Source:</strong> {st.session_state.source}</div>',
            unsafe_allow_html=True,
        )

    # Copy helper
    with st.expander("📋 Copy answer as plain text"):
        st.code(
            f"Q: {question}\n\nA: {st.session_state.answer}\n\nSource: {st.session_state.source}",
            language=None,
        )

# ---------------------------------------------------------------------------
# Footer disclaimer  ← required by original spec
# ---------------------------------------------------------------------------
st.markdown(
    '<div class="disclaimer">'
    "🩺 <strong>This tool is for educational purposes only and is not a substitute "
    "for professional medical advice.</strong><br>"
    "Always consult a qualified healthcare provider for medical decisions."
    "</div>",
    unsafe_allow_html=True,
)
