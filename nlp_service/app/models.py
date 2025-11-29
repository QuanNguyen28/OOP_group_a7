from typing import List, Literal, Optional
from pydantic import BaseModel, Field
from datetime import datetime

Label = Literal["pos", "neg", "neu"]

class TextItem(BaseModel):
    id: str
    text: str
    lang: Optional[str] = "vi"  # ignored (preprocess đã dịch/chuẩn hoá)

class SentimentOut(BaseModel):
    id: str
    label: Label
    score: float  # [-1..1]

class BatchRequest(BaseModel):
    items: List[TextItem] = Field(default_factory=list)

class BatchResponse(BaseModel):
    model_id: str
    items: List[SentimentOut] = Field(default_factory=list)