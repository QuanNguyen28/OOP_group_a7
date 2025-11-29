from typing import List
from unidecode import unidecode

VI_NEGATORS = {"khong","chang","chẳng","chả","cha","deo","khg","k","ko","k0","kh"}
BOOSTERS = {"rat","rất","very","qua","too","cuc","cực","kha","quite"}
DAMPENERS = {"hoi","slightly","a-bit","abit","it","slight"}

VI_POS = {"tot","tuyet","vui","ung ho","cam on","giup","ho tro","co ich","an toan"}
VI_NEG = {"te","toi","tuc","khong tot","xau","ngap","ngap lut","thiet hai","nguy hiem"}

def normalize(text: str) -> str:
    return unidecode(text or "").lower().strip()

def tokenize(text: str) -> List[str]:
    import re
    toks = re.split(r"\s+", text)
    out = []
    for w in toks:
        w = re.sub(r"[^\w#]+","", w)
        if w: out.append(w)
    return out

def count_any(s: str, arr) -> int:
    return sum(1 for x in arr if x in s)

def emoji_delta(text: str) -> float:
    plus = count_any(text, ["🙂","😊","❤️","👍","💪","✨","🎉"])
    minus = count_any(text, ["🙁","😢","😞","😡","💔","👎"])
    return min(1.0, plus*0.15) - min(1.0, minus*0.2)

def lexical_score_vi(toks: List[str]) -> float:
    p = n = 0
    for i, t in enumerate(toks):
        hit = (1 if t in VI_POS else 0) - (1 if t in VI_NEG else 0)
        if hit == 0: continue
        w = 1.0
        if i > 0:
            prev = toks[i-1]
            if prev in BOOSTERS: w *= 1.25
            if prev in DAMPENERS: w *= 0.8
        negated = any(toks[k] in VI_NEGATORS for k in range(max(0, i-3), i))
        signed = (1.0 if hit > 0 else -1.0) * w * (-1.0 if negated else 1.0)
        if signed > 0: p += 1
        else: n += 1
    return p - n

def analyze_one(id: str, text: str, lang: str = "vi"):
    t_raw = text or ""
    t = normalize(t_raw)
    toks = tokenize(t)
    score = lexical_score_vi(toks)
    exclam = t_raw.count("!")
    if exclam >= 1:
        score *= (1.0 + min(0.5, exclam * 0.1))
    score += emoji_delta(t_raw)
    norm = max(-1.0, min(1.0, score / 3.0))
    label = "pos" if norm > 0.05 else ("neg" if norm < -0.05 else "neu")
    return {"id": id, "label": label, "score": float(norm)}