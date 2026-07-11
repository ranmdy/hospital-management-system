import { describe, it, expect, beforeEach } from 'vitest';
import { db } from '../../core/db.js';
import { cleanDb } from '../../test/helpers.js';
import { checkMetadataSanity } from './metadataSanity.js';

beforeEach(() => cleanDb());

describe('metadataSanity', () => {
  it('returns PASS for valid metadata', () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xabc', 'TestToken', 'TEST', 18, '1000000000000000000000');

    const result = checkMetadataSanity('0xabc');
    expect(result.verdict).toBe('PASS');
  });

  it('VETOs invalid decimals > 18', () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xabc', 'TestToken', 'TEST', 24, '1000000');

    const result = checkMetadataSanity('0xabc');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('decimals');
  });

  it('VETOs zero total supply', () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xabc', 'TestToken', 'TEST', 18, '0');

    const result = checkMetadataSanity('0xabc');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('supply');
  });

  it('VETOs impersonation symbol', () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xabc', 'Fake WETH', 'WETH', 18, '1000000000000000000');

    const result = checkMetadataSanity('0xabc');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('impersonates');
  });

  it('VETOs empty name', () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply) VALUES (?, ?, ?, ?, ?)`
    ).run('0xabc', '', 'TEST', 18, '1000000');

    const result = checkMetadataSanity('0xabc');
    expect(result.verdict).toBe('VETO');
    expect(result.reason).toContain('empty');
  });

  it('returns UNKNOWN for missing token', () => {
    const result = checkMetadataSanity('0xnonexistent');
    expect(result.verdict).toBe('UNKNOWN');
  });
});
