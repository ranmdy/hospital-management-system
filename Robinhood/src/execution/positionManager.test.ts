import { describe, it, expect, beforeEach, vi } from 'vitest';
import { db } from '../core/db.js';
import { cleanDb } from '../test/helpers.js';
import {
  openPosition,
  closePartial,
  closePosition,
  getOpenPositions,
  getCurrentPrice,
  getPeakPrice,
} from './positionManager.js';

vi.mock('../core/telegram.js', () => ({ sendAlert: vi.fn().mockResolvedValue(undefined) }));

function seedToken(address: string, symbol = 'TEST') {
  db.prepare('INSERT OR IGNORE INTO tokens (address, name, symbol, decimals, total_supply, status) VALUES (?, ?, ?, ?, ?, ?)')
    .run(address, symbol, symbol, 18, '1000000', 'alerted');
}

function seedPool(pool: string, token: string) {
  db.prepare('INSERT OR IGNORE INTO pools (address, token_address, quote_token, amm, fee_tier) VALUES (?, ?, ?, ?, ?)')
    .run(pool, token, '0xVIRTUAL', 'v2', 3000);
}

function seedSwap(pool: string, price: number, ts: number) {
  db.prepare(
    `INSERT INTO swaps (pool_address, tx_hash, wallet, is_buy, amount_weth, amount_token, price, block, ts)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(pool, `0x${Math.random().toString(16).slice(2)}`, '0xWALLET', 1, '100', '1000', price.toString(), 100, ts);
}

describe('positionManager', () => {
  beforeEach(() => cleanDb());

  describe('openPosition', () => {
    it('opens a position and records it in the db', () => {
      const token = '0xTOKEN_OPEN';
      seedToken(token);
      const pos = openPosition(token, 0.001, 'paper');
      expect(pos).not.toBeNull();
      expect(pos!.entryPrice).toBe(0.001);
      expect(pos!.tokensHeld).toBeCloseTo(100000); // 100 VIRTUAL / 0.001
      expect(pos!.status).toBe('open');

      const rows = getOpenPositions();
      expect(rows.length).toBe(1);
      expect(rows[0].token_address).toBe(token);
    });

    it('rejects a second position on the same token', () => {
      const token = '0xTOKEN_DUP';
      seedToken(token);
      openPosition(token, 0.001, 'paper');
      const second = openPosition(token, 0.002, 'paper');
      expect(second).toBeNull();
      expect(getOpenPositions().length).toBe(1);
    });

    it('enforces MAX_PAPER_POSITIONS limit', () => {
      for (let i = 0; i < 5; i++) {
        const t = `0xTOKEN_MAX${i}`;
        seedToken(t);
        openPosition(t, 0.001, 'paper');
      }
      const token6 = '0xTOKEN_MAX6';
      seedToken(token6);
      const pos = openPosition(token6, 0.001, 'paper');
      expect(pos).toBeNull();
      expect(getOpenPositions().length).toBe(5);
    });

    it('rejects entry price of zero', () => {
      const token = '0xTOKEN_ZERO';
      seedToken(token);
      const pos = openPosition(token, 0, 'paper');
      expect(pos).toBeNull();
    });
  });

  describe('closePosition', () => {
    it('computes positive PnL on profitable exit', () => {
      const token = '0xTOKEN_WIN';
      seedToken(token);
      const pos = openPosition(token, 0.001, 'paper')!;

      // Price doubled → 100% profit
      closePosition(pos.id, 0.002, 'test_exit');

      const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
      expect(row.status).toBe('closed');
      const pnl = parseFloat(row.realized_pnl_eth);
      expect(pnl).toBeGreaterThan(0);
      expect(pnl).toBeCloseTo(100); // 100 VIRTUAL profit
    });

    it('computes negative PnL on losing exit', () => {
      const token = '0xTOKEN_LOSS';
      seedToken(token);
      const pos = openPosition(token, 0.002, 'paper')!;

      // Price halved → 50% loss
      closePosition(pos.id, 0.001, 'hard_stop');

      const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
      expect(row.status).toBe('closed');
      const pnl = parseFloat(row.realized_pnl_eth);
      expect(pnl).toBeCloseTo(-50); // lost 50 VIRTUAL
    });

    it('is idempotent — second close is a no-op', () => {
      const token = '0xTOKEN_IDEM';
      seedToken(token);
      const pos = openPosition(token, 0.001, 'paper')!;
      closePosition(pos.id, 0.002, 'exit1');
      closePosition(pos.id, 0.003, 'exit2'); // should not change anything

      const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
      const exitLog = JSON.parse(row.exit_log);
      expect(exitLog.length).toBe(1);
    });
  });

  describe('closePartial', () => {
    it('sells a percentage and updates tokens_held', () => {
      const token = '0xTOKEN_PARTIAL';
      seedToken(token);
      const pos = openPosition(token, 0.001, 'paper')!;
      const initialTokens = pos.tokensHeld;

      closePartial(pos.id, 50, 0.002, 'ladder:100');

      const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
      expect(row.status).toBe('partial');
      expect(parseFloat(row.tokens_held)).toBeCloseTo(initialTokens * 0.5, 0);
    });
  });

  describe('getCurrentPrice / getPeakPrice', () => {
    it('returns latest swap price', () => {
      const token = '0xTOKEN_PRICE';
      const pool = '0xPOOL_PRICE';
      seedToken(token);
      seedPool(pool, token);
      seedSwap(pool, 0.001, 1000);
      seedSwap(pool, 0.002, 2000);

      expect(getCurrentPrice(token)).toBeCloseTo(0.002);
    });

    it('returns peak price since entry', () => {
      const token = '0xTOKEN_PEAK';
      const pool = '0xPOOL_PEAK';
      seedToken(token);
      seedPool(pool, token);
      seedSwap(pool, 0.001, 1000);
      seedSwap(pool, 0.005, 2000);
      seedSwap(pool, 0.003, 3000);

      expect(getPeakPrice(token, 500)).toBeCloseTo(0.005);
      expect(getPeakPrice(token, 2500)).toBeCloseTo(0.003); // only swaps after ts=2500
    });
  });
});
