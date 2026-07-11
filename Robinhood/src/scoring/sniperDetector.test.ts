import { describe, it, expect, beforeEach } from 'vitest';
import { db } from '../core/db.js';
import { cleanDb } from '../test/helpers.js';
import {
  analyzeSnipersForToken,
  getSniperExitPct,
  isSniperDominated,
  isSniperFlushComplete,
} from './sniperDetector.js';

function seedToken(address: string) {
  db.prepare('INSERT INTO tokens (address, name, symbol, decimals, total_supply, status) VALUES (?, ?, ?, ?, ?, ?)')
    .run(address, 'TestToken', 'TEST', 18, '1000000000000000000000000', 'watching');
}

function seedPool(poolAddress: string, tokenAddress: string, _launchBlock: number) {
  db.prepare(
    `INSERT INTO pools (address, token_address, quote_token, amm, fee_tier, launched_at)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).run(poolAddress, tokenAddress, '0xVIRTUAL', 'v3', 3000, Math.floor(Date.now() / 1000));
}

function seedSwap(poolAddress: string, wallet: string, isBuy: number, block: number, amountWeth = '1000000000000000000', amountToken = '100000000000000000000') {
  db.prepare(
    `INSERT INTO swaps (pool_address, tx_hash, wallet, is_buy, amount_weth, amount_token, price, block, ts)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(poolAddress, `0x${Math.random().toString(16).slice(2)}`, wallet, isBuy, amountWeth, amountToken, '0.01', block, Math.floor(Date.now() / 1000));
}

describe('sniperDetector', () => {
  beforeEach(() => {
    cleanDb();
  });

  describe('analyzeSnipersForToken', () => {
    it('should record first-block buyers in wallets table', async () => {
      const token = '0xTOKEN1';
      const pool = '0xPOOL1';
      seedToken(token);
      seedPool(pool, token, 100);
      seedSwap(pool, '0xBUYER1', 1, 100);
      seedSwap(pool, '0xBUYER2', 1, 101);

      await analyzeSnipersForToken(token, 100);

      const w1 = db.prepare('SELECT stats FROM wallets WHERE address = ?').get('0xBUYER1') as any;
      const w2 = db.prepare('SELECT stats FROM wallets WHERE address = ?').get('0xBUYER2') as any;
      expect(JSON.parse(w1.stats).firstBlockAppearances).toBe(1);
      expect(JSON.parse(w2.stats).firstBlockAppearances).toBe(1);
    });

    it('should auto-tag wallet as sniper after threshold appearances', async () => {
      const pool = '0xPOOL_SNIPER';
      const wallet = '0xSNIPER_WALLET';

      // Pre-seed the wallet with 4 appearances
      db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
        .run(wallet, '[]', 1000, JSON.stringify({ firstBlockAppearances: 4 }));

      // 5th appearance should trigger tag
      const token = '0xTOKEN_5TH';
      seedToken(token);
      seedPool(pool, token, 200);
      seedSwap(pool, wallet, 1, 200);

      await analyzeSnipersForToken(token, 200);

      const row = db.prepare('SELECT tags FROM wallets WHERE address = ?').get(wallet) as any;
      const tags = JSON.parse(row.tags);
      expect(tags).toContain('sniper');
    });

    it('should detect bundles when 3+ wallets buy in same block', async () => {
      const token = '0xTOKEN_BUNDLE';
      const pool = '0xPOOL_BUNDLE';
      seedToken(token);
      seedPool(pool, token, 300);
      seedSwap(pool, '0xB1', 1, 300);
      seedSwap(pool, '0xB2', 1, 300);
      seedSwap(pool, '0xB3', 1, 300);

      await analyzeSnipersForToken(token, 300);

      const w1 = db.prepare('SELECT stats FROM wallets WHERE address = ?').get('0xB1') as any;
      const stats = JSON.parse(w1.stats);
      expect(stats.bundleEvents).toBeDefined();
      expect(stats.bundleEvents[0].peers).toBe(3);
    });

    it('should store sniper cohort data in system_state', async () => {
      const token = '0xTOKEN_COHORT';
      const pool = '0xPOOL_COHORT';
      seedToken(token);
      seedPool(pool, token, 400);
      seedSwap(pool, '0xC1', 1, 400);
      seedSwap(pool, '0xC2', 1, 401);

      await analyzeSnipersForToken(token, 400);

      const row = db.prepare('SELECT value FROM system_state WHERE key = ?').get(`sniper_cohort:${token}`) as any;
      expect(row).toBeDefined();
      const cohort = JSON.parse(row.value);
      expect(cohort.sniperCount).toBe(2);
      expect(cohort.wallets).toContain('0xC1');
      expect(cohort.wallets).toContain('0xC2');
    });
  });

  describe('getSniperExitPct', () => {
    it('should return 0 if no cohort exists', () => {
      expect(getSniperExitPct('0xNONE')).toBe(0);
    });

    it('should calculate exit percentage correctly', () => {
      const token = '0xTOKEN_EXIT';
      const pool = '0xPOOL_EXIT';
      seedToken(token);
      seedPool(pool, token, 500);

      // Seed cohort
      db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
        .run(`sniper_cohort:${token}`, JSON.stringify({ wallets: ['0xE1', '0xE2', '0xE3', '0xE4'], supplyPct: 40 }));

      // 2 of 4 snipers have sold
      seedSwap(pool, '0xE1', 0, 510);
      seedSwap(pool, '0xE2', 0, 511);

      expect(getSniperExitPct(token)).toBe(50);
    });
  });

  describe('isSniperDominated', () => {
    it('should return false if no cohort', () => {
      expect(isSniperDominated('0xNO_COHORT')).toBe(false);
    });

    it('should return true when snipers hold >30% supply and <50% exited', () => {
      const token = '0xTOKEN_DOM';
      const pool = '0xPOOL_DOM';
      seedToken(token);
      seedPool(pool, token, 600);

      db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
        .run(`sniper_cohort:${token}`, JSON.stringify({ wallets: ['0xD1', '0xD2', '0xD3', '0xD4'], supplyPct: 35 }));

      // Only 1 of 4 exited (25%)
      seedSwap(pool, '0xD1', 0, 610);

      expect(isSniperDominated(token)).toBe(true);
    });
  });

  describe('isSniperFlushComplete', () => {
    it('should return false if exit < 90%', () => {
      const token = '0xTOKEN_FLUSH';
      db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
        .run(`sniper_cohort:${token}`, JSON.stringify({ wallets: ['0xF1', '0xF2'], supplyPct: 40 }));

      expect(isSniperFlushComplete(token)).toBe(false);
    });

    it('should return true when >90% exited and holders growing', () => {
      const token = '0xTOKEN_FLUSH2';
      const pool = '0xPOOL_FLUSH2';
      seedToken(token);
      seedPool(pool, token, 700);

      db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
        .run(`sniper_cohort:${token}`, JSON.stringify({ wallets: ['0xG1'], supplyPct: 35 }));

      // Sniper exited
      seedSwap(pool, '0xG1', 0, 710);

      // Holder snapshots showing growth
      const now = Math.floor(Date.now() / 1000);
      db.prepare('INSERT INTO holder_snapshots (token_address, holder_count, ts) VALUES (?, ?, ?)')
        .run(token, 50, now - 60);
      db.prepare('INSERT INTO holder_snapshots (token_address, holder_count, ts) VALUES (?, ?, ?)')
        .run(token, 60, now);

      expect(isSniperFlushComplete(token)).toBe(true);
    });
  });
});
