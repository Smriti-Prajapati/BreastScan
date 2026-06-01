"""
rag_pipeline.py
---------------
Core RAG pipeline for the Medical Document Q&A module.

Memory-efficient design for free-tier hosting (512 MB RAM):
  - Embeddings are generated via the HuggingFace Inference API (remote call)
    so torch and sentence-transformers are NEVER loaded into server memory.
  - ChromaDB runs in-memory (no disk persistence needed on stateless servers).
  - The LLM (flan-t5-large) is also called via the HuggingFace Inference API.

All models used are free and open-source. No paid API keys required.
Set the HF_TOKEN environment variable for higher rate limits (free account).

NOTE: For educational purposes only. Not a substitute for medical advice.
"""

import os
import fitz          # PyMuPDF — PDF text extraction
import chromadb

from huggingface_hub import InferenceClient

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# Embedding model hosted on HuggingFace (called via API, not loaded locally)
EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2"

# LLM for answer generation (free HuggingFace Inference API)
LLM_MODEL = "google/flan-t5-large"

# ChromaDB collection name
COLLECTION_NAME = "medical_docs"

# Chunk settings
CHUNK_SIZE    = 800   # characters per chunk (~200 words)
CHUNK_OVERLAP = 100   # overlap between consecutive chunks

# Retrieval
TOP_K = 4

# Minimum cosine similarity to consider a chunk relevant
SIMILARITY_THRESHOLD = 0.25

# Shown when the model cannot find a reliable answer
FALLBACK_ANSWER = (
    "I could not find a reliable answer in the document. "
    "Please consult a medical professional."
)

# ---------------------------------------------------------------------------
# Singletons — created once per process
# ---------------------------------------------------------------------------

_chroma_client     = None
_collection        = None
_inference_client  = None   # single InferenceClient reused for all calls


def _get_hf_client() -> InferenceClient:
    """Return a shared HuggingFace InferenceClient (lazy init)."""
    global _inference_client
    if _inference_client is None:
        token = os.environ.get("HF_TOKEN", None)
        # No model set here — we pass the model per-call so one client
        # can be reused for both embedding and text-generation.
        _inference_client = InferenceClient(token=token)
    return _inference_client


def _get_collection():
    """Return (or create) the in-memory ChromaDB collection."""
    global _chroma_client, _collection
    if _collection is None:
        # EphemeralClient keeps everything in RAM — no disk writes needed
        # on a stateless server. Documents are re-uploaded each session.
        _chroma_client = chromadb.EphemeralClient()
        _collection = _chroma_client.get_or_create_collection(
            name=COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
    return _collection


# ---------------------------------------------------------------------------
# Embedding via HuggingFace Inference API
# (no torch, no sentence-transformers loaded in memory)
# ---------------------------------------------------------------------------

def _embed_texts(texts: list[str]) -> list[list[float]]:
    """
    Embed a list of texts using the HuggingFace feature-extraction API.
    Returns a list of float vectors (one per input text).
    """
    client = _get_hf_client()
    embeddings = []
    for text in texts:
        # feature_extraction returns a nested list; we take the mean-pooled
        # sentence vector (first element when the model returns [1, seq, dim])
        result = client.feature_extraction(text, model=EMBEDDING_MODEL)
        # result shape can be (seq_len, dim) or (1, seq_len, dim)
        # We need a single 1-D vector — mean-pool over the token dimension
        if isinstance(result[0][0], list):
            # shape (1, seq_len, dim) → take [0] then mean over seq_len
            token_vecs = result[0]
        else:
            # shape (seq_len, dim)
            token_vecs = result
        vec = [
            sum(token_vecs[t][d] for t in range(len(token_vecs))) / len(token_vecs)
            for d in range(len(token_vecs[0]))
        ]
        embeddings.append(vec)
    return embeddings


# ---------------------------------------------------------------------------
# PDF parsing & chunking
# ---------------------------------------------------------------------------

def parse_pdf(pdf_path: str) -> list[dict]:
    """
    Extract text from every page of a PDF.

    Returns:
        [{"page": 1, "text": "..."}, ...]
    """
    pages = []
    doc = fitz.open(pdf_path)
    for i in range(len(doc)):
        text = doc[i].get_text("text").strip()
        if text:
            pages.append({"page": i + 1, "text": text})
    doc.close()
    print(f"[RAG] Parsed {len(pages)} pages from '{pdf_path}'")
    return pages


def chunk_pages(pages: list[dict]) -> list[dict]:
    """
    Split each page into overlapping fixed-size chunks.

    Returns:
        [{"chunk_id": "p1_c0", "page": 1, "text": "..."}, ...]
    """
    chunks = []
    for p in pages:
        text = p["text"]
        idx  = 0
        ci   = 0
        while idx < len(text):
            chunk_text = text[idx : idx + CHUNK_SIZE].strip()
            if chunk_text:
                chunks.append({
                    "chunk_id": f"p{p['page']}_c{ci}",
                    "page":     p["page"],
                    "text":     chunk_text,
                })
                ci += 1
            idx += CHUNK_SIZE - CHUNK_OVERLAP
    print(f"[RAG] Created {len(chunks)} chunks")
    return chunks


# ---------------------------------------------------------------------------
# Ingestion pipeline
# ---------------------------------------------------------------------------

def embed_and_store(chunks: list[dict], doc_id: str) -> int:
    """
    Embed all chunks via HuggingFace API and upsert into ChromaDB.
    Returns the number of chunks stored.
    """
    collection = _get_collection()
    texts     = [c["text"]     for c in chunks]
    ids       = [f"{doc_id}__{c['chunk_id']}" for c in chunks]
    metadatas = [{"page": c["page"], "doc_id": doc_id} for c in chunks]

    print(f"[RAG] Embedding {len(texts)} chunks via HuggingFace API …")
    embeddings = _embed_texts(texts)

    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=texts,
        metadatas=metadatas,
    )
    print(f"[RAG] Stored {len(chunks)} chunks for doc_id='{doc_id}'")
    return len(chunks)


def process_pdf(pdf_path: str, doc_id: str) -> int:
    """Full ingestion: parse → chunk → embed → store. Returns chunk count."""
    pages = parse_pdf(pdf_path)
    if not pages:
        raise ValueError("The PDF appears to be empty or contains no extractable text.")
    chunks = chunk_pages(pages)
    return embed_and_store(chunks, doc_id)


# ---------------------------------------------------------------------------
# Retrieval
# ---------------------------------------------------------------------------

def retrieve_chunks(question: str, doc_id: str | None = None) -> list[dict]:
    """
    Embed the question and retrieve the top-K most similar chunks.

    Returns:
        [{"text": "...", "page": 1, "score": 0.87, "doc_id": "..."}, ...]
    """
    collection = _get_collection()

    query_vec     = _embed_texts([question])
    where_filter  = {"doc_id": doc_id} if doc_id else None

    results = collection.query(
        query_embeddings=query_vec,
        n_results=TOP_K,
        where=where_filter,
        include=["documents", "metadatas", "distances"],
    )

    retrieved = []
    if results and results["documents"]:
        for text, meta, dist in zip(
            results["documents"][0],
            results["metadatas"][0],
            results["distances"][0],
        ):
            retrieved.append({
                "text":   text,
                "page":   meta.get("page", "?"),
                "doc_id": meta.get("doc_id", "?"),
                "score":  round(1.0 - dist, 4),   # cosine distance → similarity
            })

    print(f"[RAG] Retrieved {len(retrieved)} chunks for: '{question[:60]}'")
    return retrieved


# ---------------------------------------------------------------------------
# Answer generation via HuggingFace Inference API
# ---------------------------------------------------------------------------

def _build_prompt(question: str, chunks: list[dict]) -> str:
    """Build a grounded prompt that forbids fabrication."""
    context = "\n\n".join(f"[Page {c['page']}]: {c['text']}" for c in chunks)
    return (
        "You are a helpful medical document assistant. "
        "Answer the question using ONLY the context below. "
        "If the answer is not in the context, say you don't know.\n\n"
        f"Context:\n{context}\n\n"
        f"Question: {question}\n\n"
        "Answer:"
    )


def generate_answer(question: str, doc_id: str | None = None) -> dict:
    """
    Full RAG query: retrieve → prompt → LLM → return answer + source.

    Returns:
        {"answer": "...", "source": "...", "chunks_used": [...]}
    """
    # 1. Retrieve
    chunks = retrieve_chunks(question, doc_id=doc_id)

    # 2. Confidence gate
    if not chunks or chunks[0]["score"] < SIMILARITY_THRESHOLD:
        return {
            "answer":      FALLBACK_ANSWER,
            "source":      "N/A — no sufficiently relevant content found.",
            "chunks_used": [],
        }

    # 3. Generate
    prompt = _build_prompt(question, chunks)
    client = _get_hf_client()

    try:
        raw = client.text_generation(
            prompt,
            model=LLM_MODEL,
            max_new_tokens=256,
            temperature=0.2,
            repetition_penalty=1.2,
        ).strip()
    except Exception as exc:
        print(f"[RAG] LLM call failed: {exc}")
        return {
            "answer":      FALLBACK_ANSWER,
            "source":      "N/A — LLM inference error.",
            "chunks_used": chunks,
        }

    # 4. Uncertainty check
    uncertain = [
        "i don't know", "i do not know", "not mentioned",
        "not provided", "cannot find", "no information",
    ]
    if not raw or any(p in raw.lower() for p in uncertain):
        return {
            "answer":      FALLBACK_ANSWER,
            "source":      "N/A — model indicated insufficient context.",
            "chunks_used": chunks,
        }

    # 5. Source citation
    best   = chunks[0]
    source = (
        f"Page {best['page']} of document '{best['doc_id']}' "
        f"(relevance score: {best['score']})"
    )

    return {
        "answer":      raw,
        "source":      source,
        "chunks_used": chunks,
    }
