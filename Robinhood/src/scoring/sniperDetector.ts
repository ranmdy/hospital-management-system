import { type Address } from 'viem';
import { httpClient } from '../core/client.js';
import { db } from '../core/db.js';
import { bus } from '../core/eventBus.js';
import { createModuleLogger } from '../core/logger.js';

const log = createModuleLogger('sniperDetector');

const SNIPER_TAG_THRESHOLD = 5; // Wallets appearing in first-block buys on ≥5 launches
const FIRST_BLOCKS_WINDOW = 3; // Classify buys in first 3 blocks after launch

interface FirstBlockBuyer {
  wallet: string;
  block: number;
  amountWeth: string;
  txHash: string;
}

/**
 * Analyze a newly launched token's first blocks for sniper activity.
 * Called after a pool is launched and initial swaps are recorded.
 */
export async function analyzeSnipersForToken(tokenAddress: string, launchBlock: number): Promise<void> {
  // Get buys in the first N blocks
  const firstBuyers = db.prepare(
    `SELECT wallet, block, amount_weth as amountWeth, tx_hash as txHash
     FROM swaps
     WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND is_buy = 1
     AND block <= ?
     ORDER BY block ASC, id ASC`
  ).all(tokenAddress, launchBlock + FIRST_BLOCKS_WINDOW) as FirstBlockBuyer[];

  if (firstBuyers.length === 0) return;

  log.debug({ tokenAddress, buyerCount: firstBuyers.length }, 'Analyzing first-block buyers');

  const now = Math.floor(Date.now() / 1000);

  for (const buyer of firstBuyers) {
    // Upsert wallet record
    const existing = db.prepare('SELECT tags, stats FROM wallets WHERE address = ?').get(buyer.wallet) as any;

    if (existing) {
      const stats = JSON.parse(existing.stats || '{}');
      const firstBlockAppearances = (stats.firstBlockAppearances || 0) + 1;
      stats.firstBlockAppearances = firstBlockAppearances;
      stats.lastFirstBlock = { token: tokenAddress, block: buyer.block, ts: now };

      // Auto-tag as sniper if threshold met
      let tags: string[] = JSON.parse(existing.tags || '[]');
      if (firstBlockAppearances >= SNIPER_TAG_THRESHOLD && !tags.includes('sniper')) {
        tags.push('sniper');
        log.info({ wallet: buyer.wallet, appearances: firstBlockAppearances }, 'Wallet auto-tagged as sniper');
      }

      db.prepare('UPDATE wallets SET tags = ?, stats = ? WHERE address = ?')
        .run(JSON.stringify(tags), JSON.stringify(stats), buyer.wallet);
    } else {
      const stats = { firstBlockAppearances: 1, lastFirstBlock: { token: tokenAddress, block: buyer.block, ts: now } };
      db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
        .run(buyer.wallet, '[]', now, JSON.stringify(stats));
    }
  }

  // Bundle detection: check if multiple first-block buyers were funded from a common source
  await detectBundles(firstBuyers, tokenAddress);

  // Record sniper cohort data for this token
  const sniperWallets = firstBuyers.map(b => b.wallet);
  const totalTokensBought = db.prepare(
    `SELECT SUM(CAST(amount_token AS REAL)) as total
     FROM swaps
     WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND wallet IN (${sniperWallets.map(() => '?').join(',')})
     AND is_buy = 1`
  ).get(tokenAddress, ...sniperWallets) as any;

  const tokenSupply = db.prepare('SELECT total_supply FROM tokens WHERE address = ?').get(tokenAddress) as any;

  if (totalTokensBought?.total && tokenSupply?.total_supply) {
    const supplyPct = (totalTokensBought.total / Number(tokenSupply.total_supply)) * 100;

    // Store sniper cohort info in system_state
    const cohortData = {
      tokenAddress,
      launchBlock,
      sniperCount: firstBuyers.length,
      wallets: sniperWallets,
      supplyPct,
      analyzedAt: now,
    };

    db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
      .run(`sniper_cohort:${tokenAddress}`, JSON.stringify(cohortData));
  }
}

/**
 * Detect if first-block buyers are part of a bundle (funded from common source).
 */
async function detectBundles(buyers: FirstBlockBuyer[], tokenAddress: string): Promise<void> {
  if (buyers.length < 2) return;

  // Group buyers by block to find simultaneous buys
  const byBlock = new Map<number, string[]>();
  for (const b of buyers) {
    const list = byBlock.get(b.block) || [];
    list.push(b.wallet);
    byBlock.set(b.block, list);
  }

  // If 3+ wallets buy in the same block, likely a bundle
  for (const [block, wallets] of byBlock) {
    if (wallets.length >= 3) {
      log.info({
        tokenAddress,
        block,
        walletCount: wallets.length,
      }, 'Possible sniper bundle detected (3+ buys in same block)');

      // Tag all as potential bundle members
      for (const wallet of wallets) {
        const existing = db.prepare('SELECT tags, stats FROM wallets WHERE address = ?').get(wallet) as any;
        if (existing) {
          const stats = JSON.parse(existing.stats || '{}');
          const bundleEvents = stats.bundleEvents || [];
          bundleEvents.push({ token: tokenAddress, block, peers: wallets.length });
          stats.bundleEvents = bundleEvents;
          db.prepare('UPDATE wallets SET stats = ? WHERE address = ?')
            .run(JSON.stringify(stats), wallet);
        }
      }
    }
  }
}

/**
 * Check the sniper cohort's exit status for a token.
 * Returns the percentage of the first-block cohort that has exited.
 */
export function getSniperExitPct(tokenAddress: string): number {
  const cohortRow = db.prepare('SELECT value FROM system_state WHERE key = ?')
    .get(`sniper_cohort:${tokenAddress}`) as any;

  if (!cohortRow) return 0;

  const cohort = JSON.parse(cohortRow.value);
  const wallets: string[] = cohort.wallets || [];
  if (wallets.length === 0) return 0;

  // Count how many snipers have sold
  let exitedCount = 0;
  for (const wallet of wallets) {
    const sells = db.prepare(
      `SELECT COUNT(*) as cnt FROM swaps
       WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
       AND wallet = ? AND is_buy = 0`
    ).get(tokenAddress, wallet) as any;

    if (sells?.cnt > 0) exitedCount++;
  }

  return (exitedCount / wallets.length) * 100;
}

/**
 * Check if sniper cohort still dominates supply.
 */
export function isSniperDominated(tokenAddress: string): boolean {
  const cohortRow = db.prepare('SELECT value FROM system_state WHERE key = ?')
    .get(`sniper_cohort:${tokenAddress}`) as any;

  if (!cohortRow) return false;

  const cohort = JSON.parse(cohortRow.value);
  // Snipers captured >30% of supply AND still hold >half of it (exit < 50%)
  const exitPct = getSniperExitPct(tokenAddress);
  return cohort.supplyPct > 30 && exitPct < 50;
}

/**
 * Check if sniper flush is complete (>90% exited AND holders still growing).
 */
export function isSniperFlushComplete(tokenAddress: string): boolean {
  const exitPct = getSniperExitPct(tokenAddress);
  if (exitPct < 90) return false;

  // Check holder growth
  const snapshots = db.prepare(
    'SELECT holder_count FROM holder_snapshots WHERE token_address = ? ORDER BY ts DESC LIMIT 2'
  ).all(tokenAddress) as any[];

  if (snapshots.length < 2) return false;

  // Holders still growing after sniper exit
  return snapshots[0].holder_count > snapshots[1].holder_count;
}

/**
 * Start sniper detection — listens for pool:launched events and analyzes after a delay.
 */
export function startSniperDetector() {
  log.info('Starting sniper detector');

  bus.on('pool:launched', (data) => {
    // Wait 30 seconds for swaps to accumulate, then analyze
    setTimeout(() => {
      analyzeSnipersForToken(data.tokenAddress, data.block).catch(err => {
        log.error({ tokenAddress: data.tokenAddress, err }, 'Sniper analysis failed');
      });
    }, 30_000);
  });

  log.info('Sniper detector active');
}
