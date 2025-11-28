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