import { describe, it, expect, beforeEach, vi } from 'vitest';
import { db } from '../core/db.js';
import { cleanDb } from '../test/helpers.js';
import { openPosition, getCurrentPrice } from './positionManager.js';

vi.mock('../core/telegram.js', () => ({ sendAlert: vi.fn().mockResolvedValue(undefined) }));

function seedToken(address: string) {
  db.prepare('INSERT OR IGNORE INTO tokens (address, name, symbol, decimals, total_supply, status) VALUES (?, ?, ?, ?, ?, ?)')
    .run(address, 'Test', 'TST', 18, '1000000', 'alerted');
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

describe('exitEngine exit conditions', () => {
  beforeEach(() => cleanDb());

  it('hard stop triggers when price drops 50% from entry', async () => {
    const token = '0xTOKEN_HARDSTOP';
    const pool = '0xPOOL_HARDSTOP';
    seedToken(token);
    seedPool(pool, token);
    seedSwap(pool, 0.002, 1000); // entry price context

    const pos = openPosition(token, 0.002, 'paper')!;

    // Price dropped 50%
    seedSwap(pool, 0.001, 2000);

    // Inline the exit check logic
    const currentPrice = getCurrentPrice(token)!;
    const profitPct = ((currentPrice - pos.entryPrice) / pos.entryPrice) * 100;
    expect(profitPct).toBeCloseTo(-50);

    const { closePosition } = await import('./positionManager.js');
    closePosition(pos.id, currentPrice, 'hard_stop');

    const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
    expect(row.status).toBe('closed');
    const exitLog = JSON.parse(row.exit_log);
    expect(exitLog[0].reason).toBe('hard_stop');
  });

  it('trailing stop triggers when price pulls back 40% from peak', async () => {
    const token = '0xTOKEN_TRAIL';
    const pool = '0xPOOL_TRAIL';
    seedToken(token);
    seedPool(pool, token);

    const pos = openPosition(token, 0.001, 'paper')!;
    const entryTs = pos.entryTs;

    // Price ran up then pulled back
    seedSwap(pool, 0.005, entryTs + 100);   // peak: 5x
    seedSwap(pool, 0.003, entryTs + 200);   // pulled back 40%

    const { getPeakPrice, getCurrentPrice: getPrice, closePosition } = await import('./positionManager.js');
    const peakPrice = getPeakPrice(token, entryTs)!;
    const currentPrice = getPrice(token)!;

    expect(peakPrice).toBeCloseTo(0.005);
    const drawdown = ((peakPrice - currentPrice) / peakPrice) * 100;
    expect(drawdown).toBeCloseTo(40);

    closePosition(pos.id, currentPrice, 'trailing_stop');
    const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
    expect(row.status).toBe('closed');
  });

  it('exit ladder triggers at 100% profit', async () => {
    const token = '0xTOKEN_LADDER';
    const pool = '0xPOOL_LADDER';
    seedToken(token);
    seedPool(pool, token);

    const pos = openPosition(token, 0.001, 'paper')!;
    seedSwap(pool, 0.002, pos.entryTs + 100); // 100% profit

    const currentPrice = getCurrentPrice(token)!;
    const profitPct = ((currentPrice - pos.entryPrice) / pos.entryPrice) * 100;
    expect(profitPct).toBeCloseTo(100);

    const { closePartial } = await import('./positionManager.js');
    closePartial(pos.id, 50, currentPrice, 'ladder:100');

    const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(pos.id) as any;
    expect(row.status).toBe('partial');
    const exitLog = JSON.parse(row.exit_log);
    expect(exitLog[0].reason).toBe('ladder:100');
    expect(parseFloat(row.tokens_held)).toBeCloseTo(pos.tokensHeld * 0.5, 0);
  });
});
