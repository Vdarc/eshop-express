"""Stateless HTTP adapter for sentence-transformers inference.

Run with: uvicorn backend.ai_service:app --host 0.0.0.0 --port 10683
The Java recommendation and RAG services call these batch endpoints.
"""
from functools import lru_cache
from pathlib import Path
from typing import List
import os

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Traslation AI Inference", version="1.0.0")


class EmbedRequest(BaseModel):
    texts: List[str] = Field(min_length=1, max_length=256)
    model: str = "minilm-l12"


class SimilarityRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)
    candidates: List[str] = Field(min_length=1, max_length=1000)
    top_k: int = Field(default=10, ge=1, le=100)


@lru_cache(maxsize=2)
def _load_model(model_name: str):
    from sentence_transformers import SentenceTransformer

    model_root = Path(os.getenv("AI_MODEL_ROOT", Path(__file__).resolve().parent.parent / "huggingface_cache"))
    model_path = model_root / os.getenv("SBERT_MODEL_NAME", "paraphrase-multilingual-MiniLM-L12-v2")
    if not model_path.exists():
        raise FileNotFoundError(f"Sentence embedding model not found: {model_path}")
    return SentenceTransformer(str(model_path))


def _encode(texts: List[str]):
    try:
        return _load_model("minilm-l12").encode(texts, normalize_embeddings=True, convert_to_numpy=True)
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Embedding model is unavailable") from exc


@app.get("/health")
def health():
    return {"status": "ok", "model": os.getenv("SBERT_MODEL_NAME", "paraphrase-multilingual-MiniLM-L12-v2")}


@app.post("/embed")
def embed(request: EmbedRequest):
    vectors = _encode(request.texts)
    return {"vectors": vectors.tolist(), "dim": int(vectors.shape[1])}


@app.post("/similarity")
def similarity(request: SimilarityRequest):
    vectors = _encode([request.query, *request.candidates])
    scores = (vectors[1:] @ vectors[0]).astype(float)
    top_ids = np.argsort(-scores)[: request.top_k].astype(int).tolist()
    return {"scores": scores.tolist(), "top_ids": top_ids}
