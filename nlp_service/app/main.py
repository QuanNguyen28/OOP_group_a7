from fastapi import FastAPI
from app.models import BatchRequest, BatchResponse
from app.sentiment import analyze_one

app = FastAPI(title="Offline NLP API", version="1.0.0")
MODEL_ID = "rule-local-v2"

@app.get("/health")
def health():
    return {"status": "ok", "model_id": MODEL_ID}

@app.post("/v1/sentiment/batch", response_model=BatchResponse)
def sentiment_batch(req: BatchRequest):
    out = []
    for it in req.items:
        res = analyze_one(it.id, it.text, it.lang or "und")
        out.append(res)
    return BatchResponse(model_id=MODEL_ID, items=out)