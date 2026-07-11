import { describe, it, expect, beforeEach, vi } from 'vitest';
import { db } from '../core/db.js';
import { cleanDb } from '../test/helpers.js';

// Mock async checks that hit external APIs
vi.mock('./checks/honeypotSim.js', () => ({
  checkHoneypotSim: vi.fn().mockResolvedValue({
    check: 'honeypotSim', verdict: 'PASS', reason: 'Tax 3%', data: { roundTripTaxPct: 3 },
  }),
}));

vi.mock('./checks/contractInspection.js', () => ({
  checkContractInspection: vi.fn().mockResolvedValue({
    check: 'contractInspection', verdict: 'PASS', reason: 'Verified, clean',
  }),
}));

vi.mock('./checks/lpStatus.js', () => ({
  checkLpStatus: vi.fn().mockResolvedValue({
    check: 'lpStatus', verdict: 'PASS', reason: 'LP burned', data: { status: 'burned' },
  }),
}));

vi.mock('./checks/deployerHistory.js', () => ({
  checkDeployerHistory: vi.fn().mockResolvedValue({
    check: 'deployerHistory', verdict: 'PASS', reason: 'First token', data: { rugCount: 0 },
  }),
}));

import { runSafetyPipeline } from './pipeline.js';

beforeEach(() => cleanDb());

describe('safety pipeline', () => {
  it('runs all 7 checks for a valid token and does not VETO', async () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xgood', 'GoodToken', 'GOOD', 18, '1000000000000000000000');

    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool', '0xgood', 1000, '5000000000000000000');

    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`
    ).run('0xgood', 1000, 100, 20, 5);

    const report = await runSafetyPipeline('0xgood');
    // Should run all 7 checks (no early VETO)
    expect(report.checks.length).toBe(7);
    // Should never be VETO with valid metadata + sufficient liquidity + good holders
    expect(report.overall).not.toBe('VETO');
    // Sync checks that use only DB should PASS
    expect(report.checks.find(c => c.check === 'metadataSanity')?.verdict).toBe('PASS');
    expect(report.checks.find(c => c.check === 'liquidityFloor')?.verdict).toBe('PASS');
    expect(report.checks.find(c => c.check === 'holderConcentration')?.verdict).toBe('PASS');
  });

  it('returns VETO on metadata failure and short-circuits', async () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xbad', 'Fake', 'WETH', 18, '1000000000000000000');

    const report = await runSafetyPipeline('0xbad');
    expect(report.overall).toBe('VETO');
    // Should stop after metadataSanity
    expect(report.checks.length).toBe(1);
    expect(report.checks[0].check).toBe('metadataSanity');
  });

  it('returns VETO on liquidity failure', async () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xdust', 'DustToken', 'DUST', 18, '1000000000000000000000');

    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool', '0xdust', 1000, '100000000000000000'); // 0.1 ETH

    const report = await runSafetyPipeline('0xdust');
    expect(report.overall).toBe('VETO');
    expect(report.checks.length).toBe(2);
    expect(report.checks[1].check).toBe('liquidityFloor');
  });

  it('persists report to database', async () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xpersist', 'PersistToken', 'PER', 18, '1000000000000000000000');

    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool', '0xpersist', 1000, '5000000000000000000');

    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`
    ).run('0xpersist', 1000, 100, 20, 5);

    await runSafetyPipeline('0xpersist');

    const row = db.prepare('SELECT * FROM safety_reports WHERE token_address = ?').get('0xpersist') as any;
    expect(row).toBeDefined();
    expect(row.overall).toBe('PASS');
    expect(JSON.parse(row.report).checks.length).toBe(7);
  });

  it('updates token status to vetoed on VETO', async () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply, status) VALUES (?, ?, ?, ?, ?, ?)`
    ).run('0xvetoed', '', 'BAD', 18, '1000000', 'watching');

    await runSafetyPipeline('0xvetoed');

    const token = db.prepare('SELECT status FROM tokens WHERE address = ?').get('0xvetoed') as any;
    expect(token.status).toBe('vetoed');
  });
});
