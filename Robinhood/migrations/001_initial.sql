CREATE TABLE tokens (
  address TEXT PRIMARY KEY,
  name TEXT,
  symbol TEXT,
  decimals INTEGER,
  total_supply TEXT,
  deployer TEXT,
  created_block INTEGER,
  created_at INTEGER,
  status TEXT DEFAULT 'new',
  outcome TEXT,
  outcome_labeled_at INTEGER
);

CREATE TABLE pools (
  address TEXT PRIMARY KEY,
  token_address TEXT REFERENCES tokens(address),
  quote_token TEXT,
  amm TEXT,
  fee_tier INTEGER,
  created_block INTEGER,
  launched_at INTEGER,
  initial_liquidity_weth TEXT,
  lp_provider TEXT,
  lp_status TEXT
);

CREATE TABLE swaps (
  id INTEGER PRIMARY KEY,
  pool_address TEXT,
  block INTEGER,
  tx_hash TEXT,
  wallet TEXT,
  is_buy INTEGER,
  amount_weth TEXT,
  amount_token TEXT,
  price TEXT,
  ts INTEGER
);

CREATE TABLE holder_snapshots (
  id INTEGER PRIMARY KEY,
  token_address TEXT,
  ts INTEGER,
  holder_count INTEGER,
  top10_pct REAL,
  deployer_pct REAL
);

CREATE TABLE wallets (
  address TEXT PRIMARY KEY,
  tags TEXT DEFAULT '[]',
  first_seen INTEGER,
  stats TEXT DEFAULT '{}'
);

CREATE TABLE safety_reports (
  id INTEGER PRIMARY KEY,
  token_address TEXT,
  ts INTEGER,
  overall TEXT,
  report TEXT
);

CREATE TABLE score_snapshots (
  id INTEGER PRIMARY KEY,
  token_address TEXT,
  ts INTEGER,
  total REAL,
  breakdown TEXT
);

CREATE TABLE positions (
  id INTEGER PRIMARY KEY,
  token_address TEXT,
  mode TEXT,
  entry_ts INTEGER,
  entry_price TEXT,
  size_eth TEXT,
  tokens_held TEXT,
  status TEXT,
  realized_pnl_eth TEXT,
  exit_log TEXT
);

CREATE TABLE events (
  id INTEGER PRIMARY KEY,
  ts INTEGER,
  module TEXT,
  token_address TEXT,
  kind TEXT,
  payload TEXT
);

CREATE TABLE system_state (
  key TEXT PRIMARY KEY,
  value TEXT
);

-- Indexes
CREATE INDEX idx_swaps_pool_block ON swaps(pool_address, block);
CREATE INDEX idx_swaps_wallet ON swaps(wallet);
CREATE INDEX idx_holder_snapshots_token_ts ON holder_snapshots(token_address, ts);
CREATE INDEX idx_events_token_ts ON events(token_address, ts);
CREATE INDEX idx_score_snapshots_token_ts ON score_snapshots(token_address, ts);
