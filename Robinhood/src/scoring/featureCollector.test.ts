import { describe, it, expect, beforeEach } from 'vitest';
import { db } from '../core/db.js';
import { cleanDb } from '../test/helpers.js';
import { collectFeatures } from './featureCollector.js';

beforeEach(() => cleanDb());

describe('featureCollector', () => {
  const setupToken = () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).run('0xtoken1', 'TestCat', 'TCAT', 18, '1000000000000000000000', 'watching', Math.floor(Date.now() / 1000) - 3600);

    db.prepare(
      `INSERT INTO pools (address, token_address, quote_token, amm, launched_at)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xpool1', '0xtoken1', '0xWETH', 'univ2', Math.floor(Date.now() / 1000) - 3600);
  };

  it('returns all false when no data exists', () => {
    setupToken();
    const features = collectFeatures('0xtoken1');

    expect(features.smartMoneyConfluence).toBe(false);
    expect(features.holderGrowthAccel).toBe(false);
    expect(features.buyPressure).toBe(false);
    expect(features.sniperFlushComplete).toBe(false);
    expect(features.metaMatch).toBe(false);
    expect(features.socialPresence).toBe(false);
    expect(features.dexscreenerBoost).toBe(false);
    expect(features.lpLocked).toBe(false);
    expect(features.freshDeployer).toBe(false);
    expect(features.sniperDominated).toBe(false);
  });

  it('detects smart money confluence', () => {
    setupToken();
    const now = Math.floor(Date.now() / 1000);

    // Add smart wallets
    db.prepare(`INSERT INTO wallets (address, tags, first_seen) VALUES (?, ?, ?)`).run('0xsmart1', '["smart"]', now - 1000);
    db.prepare(`INSERT INTO wallets (address, tags, first_seen) VALUES (?, ?, ?)`).run('0xsmart2', '["smart"]', now - 1000);

    // Add buys from smart wallets within 15 min
    db.prepare(`INSERT INTO swaps (pool_address, block, tx_hash, wallet, is_buy, amount_weth, amount_token, price, ts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`).run('0xpool1', 100, '0xtx1', '0xsmart1', 1, '100000000000000000', '1000000', '0.0001', now - 300);
    db.prepare(`INSERT INTO swaps (pool_address, block, tx_hash, wallet, is_buy, amount_weth, amount_token, price, ts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`).run('0xpool1', 101, '0xtx2', '0xsmart2', 1, '200000000000000000', '2000000', '0.0001', now - 200);

    const features = collectFeatures('0xtoken1');
    expect(features.smartMoneyConfluence).toBe(true);
  });

  it('detects holder growth acceleration', () => {
    setupToken();
    const now = Math.floor(Date.now() / 1000);

    // 3 snapshots with accelerating growth: 10 -> 20 -> 35
    db.prepare(`INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`).run('0xtoken1', now - 600, 10, 30, 5);
    db.prepare(`INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`).run('0xtoken1', now - 300, 20, 28, 5);
    db.prepare(`INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`).run('0xtoken1', now, 35, 25, 4);

    const features = collectFeatures('0xtoken1');
    expect(features.holderGrowthAccel).toBe(true);
  });

  it('does not detect holder growth without acceleration', () => {
    setupToken();
    const now = Math.floor(Date.now() / 1000);

    // Decelerating growth: 10 -> 25 -> 30 (growth slowing: 15 then 5)
    db.prepare(`INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`).run('0xtoken1', now - 600, 10, 30, 5);
    db.prepare(`INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`).run('0xtoken1', now - 300, 25, 28, 5);
    db.prepare(`INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct) VALUES (?, ?, ?, ?, ?)`).run('0xtoken1', now, 30, 25, 4);

    const features = collectFeatures('0xtoken1');
    expect(features.holderGrowthAccel).toBe(false);
  });

  it('detects meta match', () => {
    setupToken();
    db.prepare(`INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)`).run('meta_tags', '["cat", "robin", "dog"]');

    const features = collectFeatures('0xtoken1');
    expect(features.metaMatch).toBe(true); // token name is "TestCat"
  });

  it('detects lp locked status', () => {
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).run('0xtoken2', 'LockedToken', 'LCK', 18, '1000000', 'watching', 1000);

    db.prepare(
      `INSERT INTO pools (address, token_address, quote_token, amm, launched_at, lp_status)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).run('0xpool2', '0xtoken2', '0xWETH', 'univ2', 1000, 'burned');

    const features = collectFeatures('0xtoken2');
    expect(features.lpLocked).toBe(true);
  });

  it('detects stealth no socials after 2h', () => {
    const twoHoursAgo = Math.floor(Date.now() / 1000) - 7201;
    db.prepare(
      `INSERT INTO tokens (address, name, symbol, decimals, total_supply, status, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).run('0xtoken3', 'Ghost', 'GHOST', 18, '1000000', 'watching', twoHoursAgo);

    db.prepare(
      `INSERT INTO pools (address, token_address, quote_token, amm, launched_at)
       VALUES (?, ?, ?, ?, ?)`
    ).run('0xpool3', '0xtoken3', '0xWETH', 'univ2', twoHoursAgo);

    // No dexscreener meta stored = no socials
    const features = collectFeatures('0xtoken3');
    expect(features.stealthNoSocials).toBe(true);
  });
});
