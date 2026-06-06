"""
rag_pipeline.py
---------------
Core RAG pipeline for the Medical Document Q&A module.

Memory-efficient design for free-tier hosting (512 MB RAM):
  - Embeddings via HuggingFace Inference API (no torch/sentence-transformers in memory)
  - ChromaDB in-memory (EphemeralClient)
  - LLM via HuggingFace Inference API (flan-t5-large, free)

Set HF_TOKEN env var on Render for higher rate limits (free account token).
NOTE: For educational purposes only. Not a substitute for medical advice.
"""

import os
import fitz          # PyMuPDF
import chromadb

from huggingface_hub import InferenceClient

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

EMBEDDING_MODEL    = "sentence-transformers/all-MiniLM-L6-v2"
COLLECTION_NAME    = "medical_docs"
CHUNK_SIZE         = 800
CHUNK_OVERLAP      = 100
TOP_K              = 4
SIMILARITY_THRESHOLD = 0.20   # lowered slightly so more results come through

FALLBACK_ANSWER = (
    "I could not find a reliable answer in the document. "
    "Please consult a medical professional."
)

# ---------------------------------------------------------------------------
# Singletons
# ---------------------------------------------------------------------------

_chroma_client    = None
_collection       = None
_inference_client = None


def _get_hf_client() -> InferenceClient:
    global _inference_client
    if _inference_client is None:
        token = os.environ.get("HF_TOKEN", None)
        _inference_client = InferenceClient(token=token)
    return _inference_client


def _get_collection():
    global _chroma_client, _collection
    if _collection is None:
        _chroma_client = chromadb.EphemeralClient()
        _collection = _chroma_client.get_or_create_collection(
            name=COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
    return _collection


# ---------------------------------------------------------------------------
# Embedding — robust numpy/list handling
# ---------------------------------------------------------------------------

def _to_flat_vector(raw) -> list[float]:
    """
    Convert whatever feature_extraction returns into a flat Python list of floats.

    HuggingFace feature_extraction can return:
      - numpy ndarray of shape (seq_len, dim)  → mean over seq_len axis
      - numpy ndarray of shape (1, seq_len, dim) → squeeze first, then mean
      - list of lists (legacy)
    We handle all cases defensively.
    """
    # Convert numpy arrays to nested Python lists once, then handle uniformly
    try:
        import numpy as np
        if isinstance(raw, np.ndarray):
            raw = raw.tolist()
    except ImportError:
        pass  # numpy not installed — raw must already be a list

    # Now raw is a nested list (or already was)
    # Possible shapes after tolist():
    #   [dim]                  → already a flat vector
    #   [[d0, d1, ...], ...]   → (seq_len, dim) → mean over rows
    #   [[[...], ...], ...]    → (1, seq_len, dim) → unwrap first dim

    if not raw:
        raise ValueError("feature_extraction returned empty result")

    # Shape: flat vector [float, float, ...]
    if isinstance(raw[0], float) or isinstance(raw[0], int):
        return [float(x) for x in raw]

    # Shape: (1, seq_len, dim) — unwrap outer list
    if isinstance(raw[0][0], list):
        raw = raw[0]   # now (seq_len, dim)

    # Shape: (seq_len, dim) — mean-pool over seq_len
    seq_len = len(raw)
    dim     = len(raw[0])
    vec = [sum(raw[t][d] for t in range(seq_len)) / seq_len for d in range(dim)]
    return vec


def _embed_texts(texts: list[str]) -> list[list[float]]:
    """
    Embed a list of texts via HuggingFace Inference API.
    Calls the API once per text (rate limit friendly with small chunk counts).
    """
    client = _get_hf_client()
    embeddings = []
    for i, text in enumerate(texts):
        try:
            raw = client.feature_extraction(text, model=EMBEDDING_MODEL)
            vec = _to_flat_vector(raw)
            embeddings.append(vec)
        except Exception as e:
            print(f"[RAG] Embedding failed for chunk {i}: {e}")
            raise RuntimeError(
                f"HuggingFace embedding API error: {e}. "
                "Make sure HF_TOKEN is set on Render for better rate limits."
            ) from e
    return embeddings


# ---------------------------------------------------------------------------
# PDF parsing & chunking
# ---------------------------------------------------------------------------

def parse_pdf(pdf_path: str) -> list[dict]:
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
# Ingestion
# ---------------------------------------------------------------------------

def embed_and_store(chunks: list[dict], doc_id: str) -> int:
    collection = _get_collection()
    texts     = [c["text"] for c in chunks]
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
    pages = parse_pdf(pdf_path)
    if not pages:
        raise ValueError("The PDF appears to be empty or contains no extractable text.")
    chunks = chunk_pages(pages)
    return embed_and_store(chunks, doc_id)


# ---------------------------------------------------------------------------
# Retrieval
# ---------------------------------------------------------------------------

def retrieve_chunks(question: str, doc_id: str | None = None) -> list[dict]:
    collection  = _get_collection()
    query_vec   = _embed_texts([question])
    where_filter = {"doc_id": doc_id} if doc_id else None

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
                "score":  round(1.0 - dist, 4),
            })

    print(f"[RAG] Retrieved {len(retrieved)} chunks for: '{question[:60]}'")
    return retrieved


# ---------------------------------------------------------------------------
# Answer generation
# ---------------------------------------------------------------------------

def generate_answer(question: str, doc_id: str | None = None) -> dict:
    """
    RAG query: retrieve relevant chunks and return them directly as the answer.

    No LLM is called — the retrieved text IS the answer.
    This is 100% reliable, needs no external API beyond embedding,
    and is more trustworthy (word-for-word from the document, no hallucination).
    """
    chunks = retrieve_chunks(question, doc_id=doc_id)

    if not chunks or chunks[0]["score"] < SIMILARITY_THRESHOLD:
        return {
            "answer":      FALLBACK_ANSWER,
            "source":      "N/A — no sufficiently relevant content found in the document.",
            "chunks_used": [],
        }

    # Combine the top chunks into a single answer
    answer_parts = []
    for c in chunks:
        answer_parts.append(c["text"].strip())

    answer = "\n\n".join(answer_parts)

    best   = chunks[0]
    source = (
        f"Page {best['page']} of document '{best['doc_id']}' "
        f"(relevance score: {best['score']})"
    )

    return {
        "answer":      answer,
        "source":      source,
        "chunks_used": chunks,
    }
