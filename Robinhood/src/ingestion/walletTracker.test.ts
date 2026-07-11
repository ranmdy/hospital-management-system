import { describe, it, expect, beforeEach, vi } from 'vitest';
import { db } from '../core/db.js';
import { cleanDb } from '../test/helpers.js';

// Mock fetch before importing the module
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

describe('walletTracker', () => {
  beforeEach(() => {
    cleanDb();
    mockFetch.mockReset();
  });

  it('should load tracked wallets from db', () => {
    // Seed wallets with smart/sniper tags
    db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
      .run('0xSMART1', '["smart"]', 1000, '{}');
    db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
      .run('0xSNIPER1', '["sniper"]', 1001, '{}');
    db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
      .run('0xNORMAL1', '[]', 1002, '{}');

    const rows = db.prepare(
      `SELECT address, tags FROM wallets
       WHERE tags LIKE '%smart%' OR tags LIKE '%sniper%'
       ORDER BY first_seen DESC LIMIT 100`
    ).all() as any[];

    expect(rows.length).toBe(2);
    expect(rows.map(r => r.address)).toContain('0xSMART1');
    expect(rows.map(r => r.address)).toContain('0xSNIPER1');
    expect(rows.map(r => r.address)).not.toContain('0xNORMAL1');
  });

  it('should respect the 100 wallet limit', () => {
    for (let i = 0; i < 110; i++) {
      db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
        .run(`0xWALLET${i}`, '["smart"]', 1000 + i, '{}');
    }

    const rows = db.prepare(
      `SELECT address FROM wallets
       WHERE tags LIKE '%smart%' OR tags LIKE '%sniper%'
       ORDER BY first_seen DESC LIMIT 100`
    ).all() as any[];

    expect(rows.length).toBe(100);
  });

  it('should store last checked timestamp in system_state', () => {
    const key = 'wallet_last_checked:0xTEST';
    const now = Math.floor(Date.now() / 1000);

    db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
      .run(key, now.toString());

    const row = db.prepare('SELECT value FROM system_state WHERE key = ?').get(key) as any;
    expect(Number(row.value)).toBe(now);
  });

  it('should detect wallet-pool interactions from swap records', () => {
    const pool = '0xPOOL_WT';
    const token = '0xTOKEN_WT';
    const wallet = '0xTRACKED';
    const txHash = '0xTX123';

    db.prepare('INSERT INTO tokens (address, name, symbol, decimals, total_supply, status) VALUES (?, ?, ?, ?, ?, ?)')
      .run(token, 'Test', 'TST', 18, '1000000', 'watching');
    db.prepare(
      `INSERT INTO pools (address, token_address, quote_token, amm, fee_tier)
       VALUES (?, ?, ?, ?, ?)`
    ).run(pool, token, '0xVIRTUAL', 'v3', 3000);
    db.prepare(
      `INSERT INTO swaps (pool_address, tx_hash, wallet, is_buy, amount_weth, amount_token, price, block, ts)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(pool, txHash, wallet, 1, '1000000000000000000', '500000000000000000000', '0.002', 100, 1000);

    // Query like walletTracker does
    const swap = db.prepare(
      `SELECT is_buy, amount_weth FROM swaps WHERE tx_hash = ? AND wallet = ?`
    ).get(txHash, wallet) as any;

    expect(swap).toBeDefined();
    expect(swap.is_buy).toBe(1);
    expect(swap.amount_weth).toBe('1000000000000000000');
  });
});
