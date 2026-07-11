import { describe, it, expect, beforeEach } from 'vitest';
import { db } from '../../core/db.js';
import { cleanDb } from '../../test/helpers.js';
import { checkHolderConcentration } from './holderConcentration.js';

beforeEach(() => cleanDb());

describe('holderConcentration', () => {
  it('returns UNKNOWN when no holder data exists', () => {
    const result = checkHolderConcentration('0xtoken1');
    expect(result.verdict).toBe('UNKNOWN');
  });

  it('PASSES when concentration is within limits', () => {
    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xtoken1', 1000, 100, 25.0, 5.0);

    const result = checkHolderConcentration('0xtoken1');
    expect(result.verdict).toBe('PASS');
  });

  it('VETOs when top-10 holders exceed 40%', () => {
    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xtoken1', 1000, 50, 55.0, 10.0);

    const result = checkHolderConcentration('0xtoken1');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('Top-10');
  });

  it('VETOs when deployer holds more than 20%', () => {
    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xtoken1', 1000, 80, 30.0, 25.0);

    const result = checkHolderConcentration('0xtoken1');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('Deployer');
  });

  it('uses the most recent snapshot', () => {
    // Old snapshot: bad
    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xtoken1', 1000, 20, 80.0, 50.0);

    // Newer snapshot: good
    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xtoken1', 2000, 200, 15.0, 3.0);

    const result = checkHolderConcentration('0xtoken1');
    expect(result.verdict).toBe('PASS');
  });

  it('PASSES at exact boundary (40% top-10, 20% deployer)', () => {
    db.prepare(
      `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xtoken1', 1000, 100, 40.0, 20.0);

    const result = checkHolderConcentration('0xtoken1');
    expect(result.verdict).toBe('PASS');
  });
});
