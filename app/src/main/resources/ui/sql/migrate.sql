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

CREATE TABLE IF NOT EXISTS sentiments(
  id TEXT PRIMARY KEY,       
  label TEXT NOT NULL,        
  score REAL NOT NULL,     
  ts TEXT NOT NULL,           
  run_id TEXT NOT NULL,
  FOREIGN KEY(run_id) REFERENCES runs(run_id)
);

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

CREATE TABLE IF NOT EXISTS sentiments(
  id TEXT PRIMARY KEY,
  label TEXT NOT NULL,         
  score REAL DEFAULT 1.0,
  ts TEXT NOT NULL,
  run_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS overall_sentiment(
  run_id TEXT NOT NULL,
  bucket_start TEXT NOT NULL,  
  pos INTEGER NOT NULL,
  neg INTEGER NOT NULL,
  neu INTEGER NOT NULL,
  PRIMARY KEY(run_id, bucket_start)
);

CREATE TABLE IF NOT EXISTS damage (
  id      TEXT NOT NULL,
  type    TEXT NOT NULL,
  ts      TEXT NOT NULL,
  run_id  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_damage_run_ts   ON damage(run_id, ts);
CREATE INDEX IF NOT EXISTS idx_damage_type_ts  ON damage(type, ts);

-- Task 3: Relief items mentions per post
CREATE TABLE IF NOT EXISTS relief_items (
  id      TEXT NOT NULL,
  item    TEXT NOT NULL,
  ts      TEXT NOT NULL,
  run_id  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_relief_run_ts  ON relief_items(run_id, ts);
CREATE INDEX IF NOT EXISTS idx_relief_item_ts ON relief_items(item, ts);

-- Task 4: Daily tokens/hashtags (trend) per run
CREATE TABLE IF NOT EXISTS keyword_counts (
  run_id       TEXT NOT NULL,
  bucket_start TEXT NOT NULL,
  token        TEXT NOT NULL,
  cnt          INTEGER NOT NULL,
  PRIMARY KEY (run_id, bucket_start, token)
);

CREATE TABLE IF NOT EXISTS hashtag_counts (
  run_id       TEXT NOT NULL,
  bucket_start TEXT NOT NULL,
  tag          TEXT NOT NULL,
  cnt          INTEGER NOT NULL,
  PRIMARY KEY (run_id, bucket_start, tag)
);
CREATE INDEX IF NOT EXISTS idx_keyword_run_bucket ON keyword_counts(run_id, bucket_start);
CREATE INDEX IF NOT EXISTS idx_hashtag_run_bucket ON hashtag_counts(run_id, bucket_start);