PRAGMA journal_mode=WAL;

CREATE TABLE IF NOT EXISTS runs(
  run_id TEXT PRIMARY KEY,
  started_at TEXT NOT NULL,
  params_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS posts(
  id TEXT PRIMARY KEY,
  platform TEXT,
  text TEXT,
  lang TEXT,
  ts TEXT,
  geo TEXT,
  run_id TEXT,
  FOREIGN KEY(run_id) REFERENCES runs(run_id)
);

CREATE INDEX IF NOT EXISTS idx_posts_ts ON posts(ts);
CREATE INDEX IF NOT EXISTS idx_posts_run ON posts(run_id);

-- Kết quả NLP: sentiment từng post
CREATE TABLE IF NOT EXISTS sentiments(
  id TEXT PRIMARY KEY,       
  label TEXT NOT NULL,        
  score REAL NOT NULL,     
  ts TEXT NOT NULL,           
  run_id TEXT NOT NULL,
  FOREIGN KEY(run_id) REFERENCES runs(run_id)
);

-- Tổng hợp cho dashboard: sentiment theo ngày (UTC)
CREATE TABLE IF NOT EXISTS overall_sentiment(
  run_id TEXT NOT NULL,
  bucket_start TEXT NOT NULL,  
  pos INTEGER NOT NULL,
  neg INTEGER NOT NULL,
  neu INTEGER NOT NULL,
  PRIMARY KEY(run_id, bucket_start)
);

CREATE INDEX IF NOT EXISTS idx_sentiments_run ON sentiments(run_id);
CREATE INDEX IF NOT EXISTS idx_overall_run ON overall_sentiment(run_id);