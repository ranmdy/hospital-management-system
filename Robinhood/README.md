# HoodScan — Memecoin Detection & Trading System for Robinhood Chain

A TypeScript system that detects, scores, and (eventually) trades memecoins on Robinhood Chain, the Arbitrum Orbit L2 launched by Robinhood on July 1, 2026. Built in strictly ordered phases: **scanner → paper trader → live trader**. No phase begins until the previous phase's exit criteria are met.

> **Instruction to Claude Code:** Read this entire document before writing any code. Build phases in order. Do not implement live trading (Phase 4) until explicitly instructed by the operator, even if earlier phases are complete. When any on-chain address or ABI in this document cannot be verified against the live chain, verify it via the block explorer or official docs before using it — do not guess addresses.

---

## 1. Project Philosophy & Honest Constraints

These constraints shape every design decision. Do not "optimize" them away.

1. **There is no perfect prediction.** Memecoin outcomes are driven by attention, insiders, and reflexivity. The system targets positive expected value across many small trades, not high per-trade accuracy. Expected hit rate on entries: 30–40% at best. The system makes money (if at all) by winners being larger than losers and by *not dying*.
2. **Safety vetoes are absolute.** No score, no signal, no operator FOMO overrides a failed safety check. Vetoes are implemented as hard gates in code, not as score penalties.
3. **We do not win the speed race.** Professional snipers will reach block one before us. Our edge is *classification* (organic vs. sniped, clean vs. honeypot, fresh dev vs. serial rugger) and *timing entries at moments pros create* (post-snipe flushes). Latency matters, but only enough to not be embarrassingly slow.
4. **The evaluation loop is the product.** Every event, alert, decision, and trade is logged with a full feature snapshot. Signal weights are learned from this chain's own data, re-fit weekly. Launch-day heuristics are placeholders.
5. **Capital safety:** dedicated hot wallet, funded only with loss-tolerable amounts, no unlimited approvals, global kill switch. The private key never appears in code, logs, or the database.

---

## 2. Chain Facts (verified from official docs, July 2026)

| Property | Value |
|---|---|
| Chain name | Robinhood Chain (Arbitrum Orbit L2 on Ethereum, blob DA) |
| Chain ID (mainnet) | `4663` |
| Chain ID (testnet) | `46630` |
| Native gas token | ETH |
| Block explorer (mainnet) | `https://robinhoodchain.blockscout.com` (Blockscout — has a public REST/JSON-RPC API, use it) |
| Block explorer (testnet) | `https://explorer.testnet.chain.robinhood.com` |
| Recommended RPC | Alchemy: `https://robinhood-mainnet.g.alchemy.com/v2/{API_KEY}`, WS: `wss://robinhood-mainnet.g.alchemy.com/v2/{API_KEY}` |
| Other providers | QuickNode (`https://{ENDPOINT}.robinhood-mainnet.quiknode.pro/{TOKEN}`), Blockdaemon, dRPC, Validation Cloud |
| Public RPC (rate-limited, fallback only) | `https://rpc.mainnet.chain.robinhood.com` |
| Sequencer feed (WS) | `wss://feed.mainnet.chain.robinhood.com` |
| Sequencer endpoint | `https://sequencer.mainnet.chain.robinhood.com` |
| WETH (L2) | `0x0Bd7D308f8E1639FAb988df18A8011f41EAcAD73` |
| L2 Multicall | `0x2cAC2D899eCC914d704FeaAE33ac1bF36277DaD1` |
| Permit2 | `0x000000000022D473030F116dDEE9F6B43aC78BA3` |
| Testnet faucet | `https://faucet.testnet.chain.robinhood.com` |
| Sequencing model | **First-come-first-served** — tx order is strictly arrival time at the sequencer. No priority gas auction; no classic public mempool. Latency to sequencer is the only speed lever. |

### Contracts to discover in Phase 0 (NOT in official chain docs — must be found on-chain)

- **Uniswap v3 Factory, v3 SwapRouter/UniversalRouter, NonfungiblePositionManager, QuoterV2** on Robinhood Chain (Uniswap deployed day one — check Uniswap's official deployments registry and the Blockscout verified-contracts list).
- **Uniswap v2-style factory/router** if one exists (Dexscreener listings show both `v2` and `v3` pools on this chain, so identify which AMM contracts emit those pools — possibly Uniswap v2 deployment or a fork; Pleiades runs a proprietary AMM and may or may not be indexable the same way).
- Record every discovered address + ABI source in `config/contracts.ts` with a comment stating how it was verified. Write a `scripts/discover-contracts.ts` script that validates these addresses on startup (e.g., calls `factory.feeAmountTickSpacing(3000)` or checks bytecode exists).

---

## 3. Tech Stack (fixed — do not substitute)

- **Language:** TypeScript (strict mode, `"strict": true`), Node.js ≥ 20. No plain JS. No floats for token amounts — **BigInt everywhere** for on-chain values; human-readable derived values may be stored as strings/REAL in DB but never used for tx construction.
- **Chain library:** `viem` (preferred over ethers — native BigInt, typed ABIs). Use `createPublicClient` with WebSocket transport for subscriptions, HTTP transport with fallback for reads (`fallback([alchemy, quicknode, publicRpc])`).
- **Database:** SQLite via `better-sqlite3` (synchronous, fast, zero-ops). Schema migrations as numbered SQL files in `/migrations`, applied on startup. Design so a later move to Postgres is a driver swap, not a rewrite.
- **Alerts:** Telegram via `grammy`. Bot token + chat ID from env.
- **Scheduler/queues:** in-process. `setInterval`-driven scanners plus an internal event bus (typed `EventEmitter` wrapper). No Redis/BullMQ in v1.
- **Config:** `.env` via `dotenv`, validated at startup with `zod`. Missing/invalid env = refuse to start with a clear error.
- **Logging:** `pino` structured JSON logs to stdout + rotating file. Every log line includes `module`, `tokenAddress` where applicable.
- **Testing:** `vitest`. Unit tests for all pure logic (scoring, safety parsing, sizing math). Integration tests run against **testnet** (chain ID 46630) or a local anvil fork.
- **Process management:** designed to run under `pm2` or systemd; must survive crashes via auto-restart and resume from DB state (persist last-processed block per listener).

### External data sources

- **Dexscreener public API** (`https://api.dexscreener.com`) — token profiles, pair data, boosts. Respect published rate limits (~60 req/min for most endpoints, 300/min for pairs endpoints — verify current limits in their docs at build time). Chain slug on Dexscreener: `robinhood`. Use as *enrichment*, never as the primary/fastest detection path.
- **Blockscout API** (`https://robinhoodchain.blockscout.com/api/v2/...`) — verified source code, holder lists, address txs, token counters. Primary source for holder counts and contract verification status.
- **GoPlus Security API** (`https://api.gopluslabs.io`) — token security scan, IF it supports chain ID 4663 by build time. Treat as supplementary; our own simulation is authoritative.

---

## 4. System Architecture

Five layers. Each is a directory under `src/`. Layers communicate only via the event bus and the database — no cross-layer imports of internals.

```
src/
  config/          # env validation, chain config, contract addresses, tunable params
  core/            # viem clients, event bus, db access, logger, telegram
  ingestion/       # Layer 1 — listeners & scanners (senses)
  safety/          # Layer 2 — enrichment & hard vetoes (immune system)
  scoring/         # Layer 3 — composite scoring (instinct)
  execution/       # Layer 4 — paper & live trading, position mgmt (hands)
  evaluation/      # Layer 5 — outcome labeling, weekly reports (learning)
  index.ts         # entrypoint: mode = scanner | paper | live (env-gated)
migrations/
scripts/           # discover-contracts, backfill, report generators
```

### 4.1 Layer 1 — Ingestion

All ingestion writes timestamped events to DB and emits typed events on the bus. Every listener persists `lastProcessedBlock` and backfills missed blocks via `getLogs` on restart.

**Modules:**

1. **`newPoolListener`** — WS subscription to Uniswap v3 factory `PoolCreated` (and v2 `PairCreated` if a v2 factory exists). On event: insert into `pools`, fetch token metadata (name, symbol, decimals, totalSupply via multicall), insert into `tokens`, emit `pool:created`.
2. **`liquidityListener`** — watches new pools (for their first 24h) for `Mint`/`IncreaseLiquidity` events. First liquidity add = the real launch. Records liquidity size in WETH terms, the LP-adding wallet, and emits `pool:launched`. This is the primary trigger for the safety pipeline.
3. **`swapScanner`** — periodic (every N blocks, default 5) `getLogs` sweep of `Swap` events across tracked pools. Maintains per-pool rolling stats: volume, buy/sell counts, **unique buyer wallets** (the anti-wash-trading signal), price. Uses Multicall (`0x2cAC...DaD1`) aggressively to stay within RPC budgets.
4. **`holderScanner`** — periodic (default every 10 min per tracked token, staggered) Blockscout API poll for holder count + top-10 holder concentration. Stores time series in `holder_snapshots`.
5. **`walletTracker`** — for wallets tagged `smart` or `sniper` in the `wallets` table, watch their txs (Blockscout address-tx polling + swap-log matching). Emits `wallet:activity` with buy/sell classification.
6. **`dexscreenerEnricher`** — on `pool:launched` + periodically for hot tokens: pull Dexscreener pair data and token profile (socials present? boosted? trending?). Store in `token_meta`.

**Robustness requirements (non-negotiable):** every WS subscription wrapped in auto-reconnect with exponential backoff; heartbeat check (if no new block seen in 60s, force reconnect + alert operator); all handlers wrapped so one thrown error never kills the process; global `unhandledRejection` handler that logs and alerts.

### 4.2 Layer 2 — Safety (hard vetoes)

Runs as a pipeline on `pool:launched` and re-runs on demand. Each check returns `PASS | VETO | UNKNOWN` with a reason string. **Any VETO ⇒ token status `vetoed`, never tradeable, but keep observing it** (vetoed tokens that pump anyway are valuable training data). `UNKNOWN` counts as a soft failure: token can be alerted on but never auto-traded.

Checks, in order (cheap → expensive):

1. **`metadataSanity`** — decimals in [0,18], totalSupply > 0, name/symbol non-empty and not impersonating Stock Tokens or major assets (blocklist of symbols like `NVDA`, `AAPL`, `WETH`, `USDG` etc. — impersonation is a scam tell).
2. **`liquidityFloor`** — initial liquidity ≥ configurable min (default 2 WETH). Dust-liquidity launches are auto-skip.
3. **`honeypotSim`** — the core check. Using `eth_call` state-override simulation (or a local anvil fork of the live chain if state overrides prove unreliable): simulate `swapExactETHForTokens`-equivalent buy of a small amount, then simulate the sell of received tokens in the same simulated context. VETO if: sell reverts, round-trip loss > configurable max implied tax (default 15%), or received token amount is zero. Record measured buy tax and sell tax.
4. **`contractInspection`** — pull verified source from Blockscout. If unverified: `UNKNOWN` (soft-fail). If verified: static scan for owner-privileged mint, blacklist/whitelist mappings, settable fees above cap, trading-pause switches, max-tx/max-wallet honeypot patterns, proxy/upgradeable pattern. Owner renounced (owner == zero address) is a positive flag, not a requirement.
5. **`lpStatus`** — where did LP tokens/NFT position go? Burned (zero address) or sent to a recognizable locker = PASS with flag `lp_locked`. Held by deployer = flag `lp_unlocked` (soft veto for auto-trading; alert-only allowed).
6. **`holderConcentration`** — VETO if top-10 non-pool, non-burn holders control > 40% of supply (configurable), or deployer holds > 20%.
7. **`deployerHistory`** — look up deployer wallet in our `wallets` table + Blockscout history: prior token deployments and their outcomes. Serial-rug deployer (≥2 prior tokens that hit `rugged` status) = VETO. Fresh wallet funded minutes before deploy = flag `fresh_deployer` (neutral-to-negative weight, not veto).

Persist full results as one `safety_reports` row (JSON) per run, per token.

### 4.3 Layer 3 — Scoring

A pure, deterministic, unit-testable function: `score(features: TokenFeatures): ScoreBreakdown`. Features are assembled from DB by a collector; the scorer itself does no I/O.

**Signals (v1 weights are placeholders; Layer 5 re-fits them):**

| Signal | Description | v1 weight |
|---|---|---|
| `smartMoneyConfluence` | ≥2 wallets tagged `smart` bought within a 15-min window | +30 |
| `holderGrowthAccel` | holder-count growth rate rising across last 3 snapshots, and holders/volume ratio above wash-trading floor | +20 |
| `buyPressure` | buy/sell ratio > 65% over trailing window, weighted by unique buyers (≥25 unique buyers min) | +15 |
| `sniperFlushComplete` | first-block sniper cohort ≥90% exited AND holder count still growing (the "survived the flush" entry) | +15 |
| `metaMatch` | token name/symbol matches currently-hot meta tags (operator-maintained list in DB, e.g. `cat`, `robin`) | +10 |
| `socialPresence` | Dexscreener profile has website + X + TG | +5 |
| `dexscreenerBoost` | token has active paid boost / trending placement | +5 |
| `lpLocked` | LP burned or locked | +10 |
| `freshDeployer` | deployer wallet < 1h old at deploy | −10 |
| `sniperDominated` | first-block cohort captured > 30% of supply and still holds > half of it | −25 |
| `stealthNoSocials` | no socials 2h after launch | −10 |

Thresholds (configurable in `config/params.ts`): `ALERT_THRESHOLD = 40`, `TRADE_THRESHOLD = 60`. Trading additionally requires: zero VETOs, zero `UNKNOWN` on honeypotSim, and `lp_unlocked` absent.

**Sniper fingerprinting sub-module (`scoring/sniperDetector.ts`):** for each launch, classify buys in the first 3 blocks: wallets funded from a common source within 24h (bundle pattern), wallet age, historical first-block appearance count across other launches on this chain. Wallets appearing in first-block buys on ≥5 launches get auto-tagged `sniper` in `wallets`. Their aggregate exit % per token is a live feature.

**Smart-money list builder (`scripts/build-smart-wallets.ts`):** retroactive job — for every token whose peak was ≥10x its post-launch base price, walk swap history; wallets that bought in the bottom quartile of the run AND realized profit get `smart` candidate points; ≥3 qualifying tokens ⇒ tag `smart`. Re-run weekly; decay tags for wallets with 3 consecutive losers.

### 4.4 Layer 4 — Execution

Two implementations of one `ExecutionEngine` interface: `PaperEngine` and `LiveEngine`. Which one runs is set by env `MODE` (`scanner` = neither; `paper`; `live`). `LiveEngine` refuses to construct unless `MODE=live` **and** `LIVE_TRADING_ACK=I_UNDERSTAND_THE_RISKS` are both set.

**Position rules (identical in paper and live — hardcode the invariants, parametrize the numbers):**

- Bankroll = wallet ETH balance at engine start (paper: `PAPER_BANKROLL_ETH`).
- Position size = `POSITION_PCT` of current bankroll (default 2%), hard-capped by `MAX_POSITION_ETH` (default 0.05 ETH).
- Max concurrent positions: `MAX_POSITIONS` (default 4). Max new entries per 24h: `MAX_DAILY_ENTRIES` (default 6).
- **Exit ladder:** at +100% sell 50% (initial recouped); at +200% sell 25%; trail the remainder with a 40% drawdown-from-peak stop. Hard stop-loss at −50% from entry. All percentages configurable.
- **Rug reflex (fastest code path in the system):** on detecting, for a held token, any of — deployer/team-tagged wallet selling > 2% of supply, `Burn`/`DecreaseLiquidity` removing > 20% of pool liquidity, tax parameter change, or trading-pause invocation ⇒ immediate full market exit with slippage cap raised to `PANIC_SLIPPAGE` (default 25%). This handler subscribes directly to the relevant logs for held tokens only; it must not go through the scoring pipeline.
- **Kill switch:** realized+unrealized drawdown > `MAX_DRAWDOWN_PCT` (default 20%) of starting bankroll in a rolling 7 days ⇒ close all positions (live) / freeze (paper), set `system_halted=true` in DB, alert operator, refuse new entries until operator runs `scripts/reset-halt.ts`.

**Live-engine specifics:** swaps via Uniswap router `exactInputSingle`/v2 `swapExactETHForTokens` with `amountOutMinimum` from QuoterV2 minus `SLIPPAGE_BPS` (default 300), tx `deadline` = now + 60s. Token approvals: exact amount per trade, never `MaxUint256`. Nonce managed locally with on-chain resync on error. Pre-signed-and-ready pattern for the rug reflex: keep a prepared exit tx template per position, fill amounts and fire on trigger. Wallet key from env only (`HOT_WALLET_PRIVATE_KEY`), never logged; startup prints the address only.

**Telegram command surface (operator control):** `/status` (positions, PnL, bankroll, halted?), `/pause` and `/resume` (new entries only), `/exit <token>` (manual close), `/veto <token>` (permanent blocklist), `/score <token>` (current breakdown). No command can raise position sizes or disable the kill switch.

### 4.5 Layer 5 — Evaluation

- **Outcome labeler (`evaluation/labeler.ts`)** — daily job. For every token first seen ≥72h ago, assign outcome: `rug` (liquidity pulled / sell-disabled / −95% within 48h with LP removal), `dead` (−80%+ and volume < $1k/day), `survivor` (alive, between −80% and +100%), `runner_3x`, `runner_10x` (peak multiples vs. 1h-post-launch base). Store peak price, time-to-peak, and the full feature snapshot as of launch+1h.
- **Signal report (`scripts/weekly-report.ts`)** — for each scoring signal: precision/recall against `runner_3x+` vs `rug/dead`, per week. Output a markdown report + Telegram summary. Include: what the current TRADE_THRESHOLD would have caught/missed, veto save-count (vetoed tokens that rugged = confirmed saves), and paper/live PnL attribution per signal.
- **Weight re-fit** — v1: operator manually adjusts `config/params.ts` from the report. v2 (later): logistic regression over feature snapshots → outcomes, exported to a weights JSON the scorer loads. Never auto-deploy new weights without operator confirmation.

---

## 5. Database Schema (SQLite, migration 001 — extend as needed)

```sql
CREATE TABLE tokens (
  address TEXT PRIMARY KEY, name TEXT, symbol TEXT, decimals INTEGER,
  total_supply TEXT, deployer TEXT, created_block INTEGER, created_at INTEGER,
  status TEXT DEFAULT 'new',           -- new|watching|vetoed|alerted|traded|archived
  outcome TEXT,                        -- NULL|rug|dead|survivor|runner_3x|runner_10x
  outcome_labeled_at INTEGER
);
CREATE TABLE pools (
  address TEXT PRIMARY KEY, token_address TEXT REFERENCES tokens(address),
  quote_token TEXT, amm TEXT,          -- 'univ3'|'univ2'|...
  fee_tier INTEGER, created_block INTEGER,
  launched_at INTEGER, initial_liquidity_weth TEXT, lp_provider TEXT, lp_status TEXT
);
CREATE TABLE swaps (                    -- rolling window; prune > 14 days for non-held tokens
  id INTEGER PRIMARY KEY, pool_address TEXT, block INTEGER, tx_hash TEXT,
  wallet TEXT, is_buy INTEGER, amount_weth TEXT, amount_token TEXT, price TEXT, ts INTEGER
);
CREATE TABLE holder_snapshots (
  id INTEGER PRIMARY KEY, token_address TEXT, ts INTEGER,
  holder_count INTEGER, top10_pct REAL, deployer_pct REAL
);
CREATE TABLE wallets (
  address TEXT PRIMARY KEY, tags TEXT DEFAULT '[]',   -- JSON: ["smart","sniper","deployer"]
  first_seen INTEGER, stats TEXT DEFAULT '{}'          -- JSON: win/loss counts, notes
);
CREATE TABLE safety_reports (
  id INTEGER PRIMARY KEY, token_address TEXT, ts INTEGER,
  overall TEXT,                        -- pass|veto|unknown
  report TEXT                          -- full JSON of all check results
);
CREATE TABLE score_snapshots (
  id INTEGER PRIMARY KEY, token_address TEXT, ts INTEGER,
  total REAL, breakdown TEXT           -- JSON per-signal values
);
CREATE TABLE positions (
  id INTEGER PRIMARY KEY, token_address TEXT, mode TEXT,  -- paper|live
  entry_ts INTEGER, entry_price TEXT, size_eth TEXT, tokens_held TEXT,
  status TEXT,                         -- open|partial|closed|panic_closed
  realized_pnl_eth TEXT, exit_log TEXT -- JSON array of exit events
);
CREATE TABLE events (                  -- append-only audit log of every decision
  id INTEGER PRIMARY KEY, ts INTEGER, module TEXT, token_address TEXT,
  kind TEXT, payload TEXT
);
CREATE TABLE system_state (key TEXT PRIMARY KEY, value TEXT);  -- lastBlock per listener, halted flag, meta tags
```

Indexes: `swaps(pool_address, block)`, `swaps(wallet)`, `holder_snapshots(token_address, ts)`, `events(token_address, ts)`, `score_snapshots(token_address, ts)`.

---

## 6. Configuration (.env.example — create this file)

```
MODE=scanner                     # scanner | paper | live
CHAIN=mainnet                    # mainnet | testnet
ALCHEMY_API_KEY=
QUICKNODE_URL=                   # optional fallback
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
PAPER_BANKROLL_ETH=1.0
POSITION_PCT=2
MAX_POSITION_ETH=0.05
MAX_POSITIONS=4
MAX_DAILY_ENTRIES=6
SLIPPAGE_BPS=300
PANIC_SLIPPAGE=2500
MAX_DRAWDOWN_PCT=20
ALERT_THRESHOLD=40
TRADE_THRESHOLD=60
MIN_INITIAL_LIQUIDITY_WETH=2
# Live mode only — both required, key never committed:
HOT_WALLET_PRIVATE_KEY=
LIVE_TRADING_ACK=
```

Rules: `.env` in `.gitignore` from the first commit. Zod-validate on boot. If `MODE=live`, additionally require key + ack and print a red warning banner with the wallet address and bankroll.

---

## 7. Build Phases & Exit Criteria

**Phase 0 — Foundation (target: day 1–2)**
Scaffold repo (TS strict, eslint, vitest, pino, migrations runner). `core/` clients with reconnect logic. Contract discovery script; verify factory/router/WETH on Blockscout. Run everything against **testnet first**, then point at mainnet read-only.
*Exit:* `npm run discover` prints verified contract addresses; WS client survives a forced disconnect; DB migrations apply cleanly.

**Phase 1 — Scanner + Safety + Alerts (target: week 1)**
Ingestion modules 1–4 + 6, full safety pipeline, Telegram alerts for every `pool:launched` with safety summary, and score-threshold alerts using placeholder weights. Runs 24/7 in `MODE=scanner`.
*Exit:* 7 consecutive days uptime with < 1% missed pools (spot-check against Dexscreener new-pairs page); every launch has a safety_report; alerts arriving < 30s after liquidity add.

**Phase 2 — Wallet intelligence (target: week 2)**
Sniper fingerprinting live on every launch; smart-wallet retroactive builder run over Phase 1 data + Blockscout backfill; walletTracker streaming tagged-wallet activity; `smartMoneyConfluence` and sniper signals go live in scoring.
*Exit:* ≥20 tagged sniper wallets and ≥5 candidate smart wallets with documented rationale in `wallets.stats`.

**Phase 3 — Paper trading (target: weeks 3–4, minimum 2 weeks of runtime)**
`PaperEngine` fully implements entries, exit ladder, rug reflex (simulated), kill switch. Labeler + weekly report live.
*Exit:* two consecutive weekly reports; operator has re-fit weights at least once; paper PnL and per-signal precision documented. **Do not proceed on negative-EV paper results — iterate here instead.**

**Phase 4 — Live trading (only on explicit operator instruction)**
`LiveEngine` with tiny caps (`MAX_POSITIONS=1`, `MAX_POSITION_ETH=0.02`) for the first week regardless of config. Verify the rug reflex on testnet with a self-deployed dummy token + LP pull before any mainnet position.
*Exit criteria to scale up:* 2 weeks live without an execution bug (failed exits, nonce errors, slippage blowouts), kill switch tested by simulated drawdown.

---

## 8. Testing Requirements

- Unit: scorer (golden-file breakdowns), sizing math, exit-ladder state machine, safety-report parsing, sniper bundle detection (synthetic fixtures).
- Integration (testnet/anvil fork): honeypotSim against (a) a normal ERC20, (b) a deliberately deployed honeypot test token, (c) a 10%-tax token — all three deployed by our own test script.
- Chaos: kill the WS mid-stream and assert backfill catches the gap; feed a malformed Dexscreener response and assert the enricher degrades gracefully.
- End-to-end paper: replay a recorded day of mainnet logs through the full pipeline deterministically (`scripts/replay.ts`).

## 9. Operational Runbook (write as it's built)

`docs/runbook.md` must cover: start/stop/upgrade procedure, what each Telegram alert means, how to respond to a HALT, how to rotate the hot wallet, weekly report review checklist, and the meta-tags maintenance process (`system_state` key `meta_tags`).

## 10. Security Checklist (enforced in code review)

- [ ] Private key only from env; grep CI check that it never appears in logs/DB.
- [ ] Exact-amount approvals only.
- [ ] Live mode double-gated (MODE + ACK).
- [ ] No external input (token names, Dexscreener strings) ever interpolated into SQL (parameterized queries only) or shell commands.
- [ ] Telegram commands authenticated to `TELEGRAM_CHAT_ID` only.
- [ ] Dependencies pinned; `npm audit` in CI.

---

## 11. Known Risks & Open Questions (revisit before Phase 4)

- **Regulatory/ToS:** operator is responsible for confirming that automated trading of these tokens is lawful in their jurisdiction and consistent with any applicable platform terms.
- **Chain is 10 days old:** RPC reliability, Blockscout indexing lag, and incentive-driven volume (watch published incentive step-down dates — expect regime change) are all unproven. Signal weights learned this month may decay fast.
- **Dexscreener API terms/limits** and **GoPlus chain support** for chain 4663 need verification at build time.
- **Pleiades AMM** may not be indexable like Uniswap; v1 scope is Uniswap pools only.
- **Expected outcome honesty:** the most likely result of this project is a modest loss that purchases a working system and real data. Position sizing assumes this.
