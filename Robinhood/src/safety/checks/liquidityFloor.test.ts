import { describe, it, expect, beforeEach } from 'vitest';
import { db } from '../../core/db.js';
import { cleanDb } from '../../test/helpers.js';
import { checkLiquidityFloor } from './liquidityFloor.js';

beforeEach(() => cleanDb());

describe('liquidityFloor', () => {
  it('returns PASS when liquidity meets minimum (2 ETH)', () => {
    db.prepare('INSERT INTO tokens (address) VALUES (?)').run('0xtoken1');
    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool1', '0xtoken1', 1000, '3000000000000000000'); // 3 ETH

    const result = checkLiquidityFloor('0xtoken1');
    expect(result.verdict).toBe('PASS');
  });

  it('VETOs when liquidity is below minimum', () => {
    db.prepare('INSERT INTO tokens (address) VALUES (?)').run('0xtoken1');
    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool1', '0xtoken1', 1000, '500000000000000000'); // 0.5 ETH

    const result = checkLiquidityFloor('0xtoken1');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('below minimum');
  });

  it('returns UNKNOWN when no launched pool exists', () => {
    db.prepare('INSERT INTO tokens (address) VALUES (?)').run('0xtoken1');
    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool1', '0xtoken1', null, null);

    const result = checkLiquidityFloor('0xtoken1');
    expect(result.verdict).toBe('UNKNOWN');
  });

  it('VETOs exactly at boundary (1.99 ETH)', () => {
    db.prepare('INSERT INTO tokens (address) VALUES (?)').run('0xtoken1');
    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool1', '0xtoken1', 1000, '1990000000000000000');

    const result = checkLiquidityFloor('0xtoken1');
    expect(result.verdict).toBe('VETO');
  });

  it('PASSES exactly at boundary (2 ETH)', () => {
    db.prepare('INSERT INTO tokens (address) VALUES (?)').run('0xtoken1');
    db.prepare(
      `INSERT INTO pools (address, token_address, launched_at, initial_liquidity_weth) VALUES (?, ?, ?, ?)`
    ).run('0xpool1', '0xtoken1', 1000, '2000000000000000000');

    const result = checkLiquidityFloor('0xtoken1');
    expect(result.verdict).toBe('PASS');
  });
});
