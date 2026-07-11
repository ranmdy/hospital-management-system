# HoodScan Test Documentation

## Running Tests

```bash
npm test          # Run all tests once
npm run test:watch  # Watch mode (re-runs on file changes)
npx vitest run --reporter=verbose  # Verbose output
```

## Test Summary

| File | Tests | Description |
|------|-------|-------------|
| `src/core/db.test.ts` | 5 | Database utilities & schema verification |
| `src/core/eventBus.test.ts` | 4 | Typed event bus emit/subscribe/unsubscribe |
| `src/scoring/scorer.test.ts` | 6 | Pure scoring function with weight calculations |
| `src/scoring/featureCollector.test.ts` | 7 | Feature extraction from DB state |
| `src/safety/checks/metadataSanity.test.ts` | 6 | Token metadata validation & impersonation detection |
| `src/safety/checks/liquidityFloor.test.ts` | 5 | Minimum liquidity threshold enforcement |
| `src/safety/checks/holderConcentration.test.ts` | 6 | Top-holder & deployer concentration checks |
| `src/safety/pipeline.test.ts` | 5 | Full safety pipeline integration (mocked async checks) |

**Total: 44 tests, 8 files**

---

## Test Details

### Core: Database (`src/core/db.test.ts`)

Tests the SQLite layer and migration results:
- `setState` and `getState` round-trip correctly
- `getState` returns undefined for missing keys
- `setState` overwrites existing values
- All expected tables exist after migration (tokens, pools, swaps, wallets, etc.)
- All expected indexes exist (swaps_pool_block, swaps_wallet, etc.)

### Core: Event Bus (`src/core/eventBus.test.ts`)

Tests the typed internal event system:
- Events emit and are received by handlers with correct payload
- Multiple handlers on the same event all fire
- `once` handlers fire exactly once then auto-detach
- `off` properly removes handlers

### Scoring: Scorer (`src/scoring/scorer.test.ts`)

Tests the pure scoring function — no I/O, deterministic:
- Returns 0 for no active signals
- Applies correct positive weight (+30 for smartMoneyConfluence)
- Applies correct negative weight (-25 for sniperDominated)
- Correctly sums multiple signals (30+20+15+10-10 = 65)
- Calculates max possible score (all positives, no negatives = 110)
- Calculates min possible score (all negatives, no positives = -45)

### Scoring: Feature Collector (`src/scoring/featureCollector.test.ts`)

Tests feature extraction from real DB state:
- Returns all-false when no enrichment data exists
- Detects smart money confluence (2+ smart wallets buying within 15 min)
- Detects holder growth acceleration (3 snapshots with increasing growth rate)
- Does NOT detect false positives (decelerating growth)
- Detects meta tag matches in token name/symbol
- Detects LP locked/burned status from pool record
- Detects "stealth no socials" for tokens >2h old without Dexscreener data

### Safety: Metadata Sanity (`src/safety/checks/metadataSanity.test.ts`)

Tests token metadata validation:
- PASS for valid metadata (name, symbol, decimals 0-18, supply > 0)
- VETO for decimals > 18
- VETO for zero total supply
- VETO for blocklisted symbols (WETH, AAPL, etc. — impersonation)
- VETO for empty name or symbol
- UNKNOWN for tokens not in DB

### Safety: Liquidity Floor (`src/safety/checks/liquidityFloor.test.ts`)

Tests minimum liquidity enforcement (default: 2 ETH):
- PASS when liquidity >= 2 ETH (tested with 3 ETH)
- VETO when liquidity < 2 ETH (tested with 0.5 ETH)
- UNKNOWN when no launched pool exists
- VETO at boundary (1.99 ETH)
- PASS at exact boundary (2.0 ETH)

### Safety: Holder Concentration (`src/safety/checks/holderConcentration.test.ts`)

Tests holder distribution checks:
- UNKNOWN when no holder snapshot data exists
- PASS when top-10 holders < 40% and deployer < 20%
- VETO when top-10 holders > 40%
- VETO when deployer holds > 20%
- Uses most recent snapshot (ignores stale data)
- PASS at exact boundaries (40% top-10, 20% deployer)

### Safety: Pipeline Integration (`src/safety/pipeline.test.ts`)

Tests the full safety pipeline with mocked async checks (honeypot, contract inspection, LP status, deployer history):
- Returns overall PASS when all 7 checks pass
- Short-circuits on metadata VETO (only runs 1 check)
- Short-circuits on liquidity VETO (runs 2 checks)
- Persists full report JSON to `safety_reports` table
- Updates token status to 'vetoed' in DB on VETO

---

## Test Architecture

- **Framework:** Vitest 2.x with globals enabled
- **Database:** Tests share a single SQLite file (`hoodscan.db`). Each test file cleans all tables in `beforeEach` using `cleanDb()` helper (disables FK checks during cleanup).
- **Mocking:** Async checks that hit external APIs (Blockscout, on-chain calls) are mocked with `vi.mock()` in integration tests. Unit tests for individual checks use only DB state.
- **Environment:** Test env vars set in `vitest.config.ts` (MODE=scanner, CHAIN=testnet, placeholder API keys).

## Adding New Tests

1. Import `cleanDb` from `src/test/helpers.ts` in `beforeEach`
2. For pure logic (scoring, parsing): test directly, no mocks needed
3. For checks that read from DB: insert test data, call the check
4. For checks that hit external APIs: mock the module with `vi.mock()`
5. Run `npx tsc --noEmit` to verify types before testing

## Coverage Goals

| Layer | Unit Tests | Integration Tests |
|-------|-----------|-------------------|
| Config/Params | Via scorer tests | — |
| Core (DB, Bus) | Yes | — |
| Scoring | Yes (scorer + featureCollector) | — |
| Safety checks | Yes (metadata, liquidity, holders) | Pipeline test (mocked) |
| Ingestion | — | Live chain test (`scripts/test-scanner.ts`) |
| Execution | Phase 3 | Phase 3 |
| Evaluation | Phase 3 | Phase 3 |

## Live Integration Test

The script `scripts/test-scanner.ts` runs against the live Robinhood Chain (no API key needed — uses public RPC). It verifies:
- V2 Factory event detection (PairCreated)
- V3 Factory event detection (PoolCreated, both factories)
- Token metadata fetching (name, symbol, decimals, totalSupply)
- Quote token identification (WETH or VIRTUAL)

```bash
npx tsx scripts/test-scanner.ts
```
