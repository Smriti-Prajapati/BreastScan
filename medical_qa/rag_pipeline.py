"""
rag_pipeline.py
---------------
Core RAG (Retrieval-Augmented Generation) pipeline for the Medical Document Q&A module.

Responsibilities:
  - Parse and chunk PDF documents using PyMuPDF
  - Embed text chunks using HuggingFace sentence-transformers (all-MiniLM-L6-v2)
  - Store and retrieve embeddings from a ChromaDB vector database
  - Query the HuggingFace Inference API (google/flan-t5-large) for answer generation

NOTE: This module is for educational purposes only and is NOT a substitute
      for professional medical advice.
"""

import os
import fitz  # PyMuPDF
import chromadb
from chromadb.config import Settings
from sentence_transformers import SentenceTransformer
from huggingface_hub import InferenceClient

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# ChromaDB will persist data in this local directory
CHROMA_PERSIST_DIR = os.path.join(os.path.dirname(__file__), "chroma_store")

# Collection name inside ChromaDB
COLLECTION_NAME = "medical_docs"

# Embedding model (free, runs locally, no API key needed)
EMBEDDING_MODEL_NAME = "all-MiniLM-L6-v2"

# HuggingFace Inference API model (free tier)
LLM_MODEL_ID = "google/flan-t5-large"

# Number of top chunks to retrieve for each question
TOP_K = 4

# Maximum characters per chunk (roughly ~200 words)
CHUNK_SIZE = 800

# Overlap between consecutive chunks to preserve context across boundaries
CHUNK_OVERLAP = 100

# Confidence threshold: if the best similarity score is below this value,
# we consider the context insufficient and return a safe fallback message.
SIMILARITY_THRESHOLD = 0.30

# Fallback message shown when the model cannot find a reliable answer
FALLBACK_ANSWER = (
    "I could not find a reliable answer in the document. "
    "Please consult a medical professional."
)

# ---------------------------------------------------------------------------
# Singleton helpers (loaded once per process)
# ---------------------------------------------------------------------------

_embedding_model = None
_chroma_client = None
_collection = None


def _get_embedding_model() -> SentenceTransformer:
    """Load the sentence-transformer model once and reuse it."""
    global _embedding_model
    if _embedding_model is None:
        print(f"[RAG] Loading embedding model: {EMBEDDING_MODEL_NAME}")
        _embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
    return _embedding_model


def _get_collection():
    """Return (or create) the ChromaDB collection, persisting to disk."""
    global _chroma_client, _collection
    if _collection is None:
        print(f"[RAG] Initialising ChromaDB at: {CHROMA_PERSIST_DIR}")
        _chroma_client = chromadb.PersistentClient(path=CHROMA_PERSIST_DIR)
        _collection = _chroma_client.get_or_create_collection(
            name=COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},  # cosine similarity
        )
    return _collection


# ---------------------------------------------------------------------------
# PDF parsing & chunking
# ---------------------------------------------------------------------------

def parse_pdf(pdf_path: str) -> list[dict]:
    """
    Extract text from every page of a PDF file.

    Returns a list of dicts:
        [{"page": 1, "text": "..."}, {"page": 2, "text": "..."}, ...]
    """
    pages = []
    doc = fitz.open(pdf_path)
    for page_num in range(len(doc)):
        page = doc[page_num]
        text = page.get_text("text").strip()
        if text:  # skip blank pages
            pages.append({"page": page_num + 1, "text": text})
    doc.close()
    print(f"[RAG] Parsed {len(pages)} non-empty pages from '{pdf_path}'")
    return pages


def chunk_pages(pages: list[dict]) -> list[dict]:
    """
    Split each page's text into overlapping chunks.

    Returns a list of dicts:
        [{"chunk_id": "p1_c0", "page": 1, "text": "..."}, ...]
    """
    chunks = []
    for page_info in pages:
        page_num = page_info["page"]
        text = page_info["text"]

        start = 0
        chunk_index = 0
        while start < len(text):
            end = start + CHUNK_SIZE
            chunk_text = text[start:end].strip()
            if chunk_text:
                chunks.append({
                    "chunk_id": f"p{page_num}_c{chunk_index}",
                    "page": page_num,
                    "text": chunk_text,
                })
                chunk_index += 1
            # Move forward by (CHUNK_SIZE - CHUNK_OVERLAP) to create overlap
            start += CHUNK_SIZE - CHUNK_OVERLAP

    print(f"[RAG] Created {len(chunks)} chunks from {len(pages)} pages")
    return chunks


# ---------------------------------------------------------------------------
# Embedding & storage
# ---------------------------------------------------------------------------

def embed_and_store(chunks: list[dict], doc_id: str) -> int:
    """
    Embed all chunks and upsert them into ChromaDB.

    Parameters
    ----------
    chunks : list of chunk dicts (from chunk_pages)
    doc_id : a unique identifier for this document (e.g. filename stem)

    Returns the number of chunks stored.
    """
    model = _get_embedding_model()
    collection = _get_collection()

    texts = [c["text"] for c in chunks]
    ids = [f"{doc_id}__{c['chunk_id']}" for c in chunks]
    metadatas = [{"page": c["page"], "doc_id": doc_id} for c in chunks]

    print(f"[RAG] Embedding {len(texts)} chunks …")
    embeddings = model.encode(texts, show_progress_bar=False).tolist()

    # Upsert so re-uploading the same document replaces old chunks cleanly
    collection.upsert(
        ids=ids,
        embeddings=embeddings,
        documents=texts,
        metadatas=metadatas,
    )
    print(f"[RAG] Stored {len(chunks)} chunks for doc_id='{doc_id}'")
    return len(chunks)


def process_pdf(pdf_path: str, doc_id: str) -> int:
    """
    Full ingestion pipeline: parse → chunk → embed → store.

    Returns the number of chunks stored.
    """
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

    Parameters
    ----------
    question : the user's plain-language question
    doc_id   : if provided, restrict search to chunks from this document

    Returns a list of result dicts:
        [{"text": "...", "page": 1, "score": 0.87, "doc_id": "..."}, ...]
    """
    model = _get_embedding_model()
    collection = _get_collection()

    query_embedding = model.encode([question]).tolist()

    where_filter = {"doc_id": doc_id} if doc_id else None

    results = collection.query(
        query_embeddings=query_embedding,
        n_results=TOP_K,
        where=where_filter,
        include=["documents", "metadatas", "distances"],
    )

    retrieved = []
    if results and results["documents"]:
        for doc_text, meta, distance in zip(
            results["documents"][0],
            results["metadatas"][0],
            results["distances"][0],
        ):
            # ChromaDB cosine distance → similarity = 1 - distance
            similarity = 1.0 - distance
            retrieved.append({
                "text": doc_text,
                "page": meta.get("page", "?"),
                "doc_id": meta.get("doc_id", "?"),
                "score": round(similarity, 4),
            })

    print(f"[RAG] Retrieved {len(retrieved)} chunks for question: '{question[:60]}…'")
    return retrieved


# ---------------------------------------------------------------------------
# Answer generation via HuggingFace Inference API
# ---------------------------------------------------------------------------

def build_prompt(question: str, context_chunks: list[dict]) -> str:
    """
    Construct a prompt that instructs the model to answer ONLY from the
    provided context and to avoid fabricating information.
    """
    context_text = "\n\n".join(
        f"[Page {c['page']}]: {c['text']}" for c in context_chunks
    )
    prompt = (
        "You are a helpful medical document assistant. "
        "Answer the question below using ONLY the context provided. "
        "If the answer is not present in the context, say you don't know.\n\n"
        f"Context:\n{context_text}\n\n"
        f"Question: {question}\n\n"
        "Answer:"
    )
    return prompt


def generate_answer(question: str, doc_id: str | None = None) -> dict:
    """
    Full RAG query pipeline: retrieve → build prompt → call LLM → return answer.

    Returns a dict:
        {
            "answer": "...",
            "source": "Page X (chunk score: 0.87)",
            "chunks_used": [...]   # for debugging / transparency
        }
    """
    # 1. Retrieve relevant chunks
    chunks = retrieve_chunks(question, doc_id=doc_id)

    # 2. Check if we have any sufficiently relevant context
    if not chunks or chunks[0]["score"] < SIMILARITY_THRESHOLD:
        return {
            "answer": FALLBACK_ANSWER,
            "source": "N/A — no sufficiently relevant content found in the document.",
            "chunks_used": [],
        }

    # 3. Build the prompt
    prompt = build_prompt(question, chunks)

    # 4. Call HuggingFace Inference API (no API key required for public models
    #    on the free tier; set HF_TOKEN env var for higher rate limits)
    hf_token = os.environ.get("HF_TOKEN", None)
    try:
        client = InferenceClient(model=LLM_MODEL_ID, token=hf_token)
        raw_answer = client.text_generation(
            prompt,
            max_new_tokens=256,
            temperature=0.2,       # low temperature → more factual
            repetition_penalty=1.2,
        ).strip()
    except Exception as exc:
        print(f"[RAG] LLM call failed: {exc}")
        return {
            "answer": FALLBACK_ANSWER,
            "source": "N/A — LLM inference error.",
            "chunks_used": chunks,
        }

    # 5. Validate the answer — if the model says it doesn't know, use fallback
    uncertainty_phrases = [
        "i don't know", "i do not know", "not mentioned",
        "not provided", "cannot find", "no information",
    ]
    if not raw_answer or any(p in raw_answer.lower() for p in uncertainty_phrases):
        return {
            "answer": FALLBACK_ANSWER,
            "source": "N/A — model indicated insufficient context.",
            "chunks_used": chunks,
        }

    # 6. Build source citation from the best-scoring chunk
    best = chunks[0]
    source = f"Page {best['page']} of document '{best['doc_id']}' (relevance score: {best['score']})"

    return {
        "answer": raw_answer,
        "source": source,
        "chunks_used": chunks,
    }
