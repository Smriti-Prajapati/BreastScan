"""
rag_pipeline.py
---------------
Core RAG pipeline for the Medical Document Q&A module.

Architecture:
  - PDF parsing:  PyMuPDF
  - Embeddings:   HuggingFace Inference API (all-MiniLM-L6-v2)
  - Vector store: ChromaDB (in-memory)
  - LLM:          HuggingFace Inference API (multiple models tried in order)

Set HF_TOKEN on Render for authenticated access (free account token).
NOTE: For educational purposes only. Not a substitute for medical advice.
"""

import os
import fitz
import chromadb
from huggingface_hub import InferenceClient

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

EMBEDDING_MODEL   = "sentence-transformers/all-MiniLM-L6-v2"
COLLECTION_NAME   = "medical_docs"
CHUNK_SIZE        = 800
CHUNK_OVERLAP     = 100
TOP_K             = 4
SIMILARITY_THRESHOLD = 0.10  # very low — short docs always get results

# Try these models in order until one works
LLM_CANDIDATES = [
    "google/flan-t5-xxl",
    "google/flan-t5-large",
    "google/flan-t5-base",
    "facebook/bart-large-cnn",
]

FALLBACK_ANSWER = (
    "I could not find a reliable answer in the document. "
    "Please consult a medical professional."
)

# ---------------------------------------------------------------------------
# Singletons
# ---------------------------------------------------------------------------

_chroma_client    = None
_collection       = None
_hf_client        = None


def _get_hf_client() -> InferenceClient:
    global _hf_client
    if _hf_client is None:
        token = os.environ.get("HF_TOKEN", None)
        _hf_client = InferenceClient(token=token)
    return _hf_client


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
# Embedding
# ---------------------------------------------------------------------------

def _to_flat_vector(raw) -> list[float]:
    """Convert HF feature_extraction output to a flat float list."""
    try:
        import numpy as np
        if isinstance(raw, np.ndarray):
            if raw.ndim == 3:
                raw = raw[0]
            if raw.ndim == 2:
                raw = raw.mean(axis=0)
            return raw.tolist()
    except ImportError:
        pass

    if not raw:
        raise ValueError("feature_extraction returned empty result")
    if isinstance(raw[0], (float, int)):
        return [float(x) for x in raw]
    if isinstance(raw[0][0], list):
        raw = raw[0]
    seq_len = len(raw)
    dim     = len(raw[0])
    return [sum(raw[t][d] for t in range(seq_len)) / seq_len for d in range(dim)]


def _embed_texts(texts: list[str]) -> list[list[float]]:
    client = _get_hf_client()
    embeddings = []
    for i, text in enumerate(texts):
        try:
            raw = client.feature_extraction(text, model=EMBEDDING_MODEL)
            embeddings.append(_to_flat_vector(raw))
        except Exception as e:
            raise RuntimeError(f"Embedding error on chunk {i}: {e}") from e
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
    print(f"[RAG] Parsed {len(pages)} pages")
    return pages


def chunk_pages(pages: list[dict]) -> list[dict]:
    chunks = []
    for p in pages:
        text = p["text"]
        idx, ci = 0, 0
        while idx < len(text):
            chunk_text = text[idx: idx + CHUNK_SIZE].strip()
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

    print(f"[RAG] Embedding {len(texts)} chunks …")
    embeddings = _embed_texts(texts)
    collection.upsert(ids=ids, embeddings=embeddings,
                      documents=texts, metadatas=metadatas)
    print(f"[RAG] Stored {len(chunks)} chunks for '{doc_id}'")
    return len(chunks)


def process_pdf(pdf_path: str, doc_id: str) -> int:
    pages = parse_pdf(pdf_path)
    if not pages:
        raise ValueError("The PDF is empty or contains no extractable text.")
    chunks = chunk_pages(pages)
    return embed_and_store(chunks, doc_id)


# ---------------------------------------------------------------------------
# Retrieval
# ---------------------------------------------------------------------------

def retrieve_chunks(question: str, doc_id: str | None = None) -> list[dict]:
    collection   = _get_collection()
    query_vec    = _embed_texts([question])
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
    print(f"[RAG] Retrieved {len(retrieved)} chunks")
    return retrieved


# ---------------------------------------------------------------------------
# LLM call — tries multiple models until one succeeds
# ---------------------------------------------------------------------------

def _call_llm(prompt: str) -> str:
    """
    Try each LLM candidate in order.
    Returns the first successful response, or raises if all fail.
    """
    client = _get_hf_client()
    last_error = None

    for model in LLM_CANDIDATES:
        try:
            print(f"[RAG] Trying model: {model}")
            result = client.text_generation(
                prompt,
                model=model,
                max_new_tokens=300,
                temperature=0.1,
                repetition_penalty=1.3,
                do_sample=False,
            )
            text = result.strip() if isinstance(result, str) else str(result).strip()
            if text:
                print(f"[RAG] Success with model: {model}")
                return text
        except Exception as e:
            print(f"[RAG] Model {model} failed: {e}")
            last_error = e
            continue

    raise RuntimeError(f"All LLM models failed. Last error: {last_error}")


# ---------------------------------------------------------------------------
# Answer generation
# ---------------------------------------------------------------------------

def generate_answer(question: str, doc_id: str | None = None) -> dict:
    """
    Full RAG pipeline: retrieve → LLM → answer.
    Falls back to returning document text directly if all LLMs fail.
    """
    chunks = retrieve_chunks(question, doc_id=doc_id)

    if not chunks or chunks[0]["score"] < SIMILARITY_THRESHOLD:
        return {
            "answer":      FALLBACK_ANSWER,
            "source":      "N/A — no relevant content found in the document.",
            "chunks_used": [],
        }

    context = "\n\n".join(
        f"[Page {c['page']}]: {c['text']}" for c in chunks
    )

    # Flan-T5 prompt format (instruction-answer style)
    prompt = (
        f"Answer the question based only on the following document excerpt.\n\n"
        f"Document:\n{context}\n\n"
        f"Question: {question}\n\n"
        f"Answer:"
    )

    best   = chunks[0]
    source = (
        f"Page {best['page']} of document '{best['doc_id']}' "
        f"(relevance score: {best['score']})"
    )

    try:
        answer = _call_llm(prompt)

        uncertain = [
            "i don't know", "i do not know", "not mentioned",
            "not provided", "cannot find", "no information",
            "not available", "not stated",
        ]
        if any(p in answer.lower() for p in uncertain):
            return {
                "answer":      FALLBACK_ANSWER,
                "source":      "N/A — answer not found in document.",
                "chunks_used": chunks,
            }

        return {
            "answer":      answer,
            "source":      source,
            "chunks_used": chunks,
        }

    except Exception as exc:
        print(f"[RAG] All LLMs failed, falling back to raw text. Error: {exc}")
        # Last resort: return the most relevant chunk directly
        return {
            "answer": (
                f"Based on the document:\n\n{chunks[0]['text']}"
            ),
            "source":      source,
            "chunks_used": chunks,
        }
