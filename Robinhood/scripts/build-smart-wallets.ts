/**
 * Smart-money list builder — retroactive job.
 *
 * For every token whose peak was >=10x its post-launch base price:
 * - Walk swap history
 * - Wallets that bought in the bottom quartile of the run AND realized profit
 *   get `smart` candidate points
 * - >=3 qualifying tokens => tag `smart`
 * - Decay: wallets with 3 consecutive losers lose the tag
 *
 * Run weekly: npx tsx scripts/build-smart-wallets.ts
 */
import { db } from '../src/core/db.js';
import { createModuleLogger } from '../src/core/logger.js';

const log = createModuleLogger('buildSmartWallets');

const SMART_THRESHOLD = 3; // Need 3+ qualifying tokens to earn tag
const CONSECUTIVE_LOSERS_DECAY = 3;

interface TokenWithPeak {
  address: string;
  symbol: string;
  basePrice: number;
  peakPrice: number;
  multiplier: number;
}

function findRunnerTokens(): TokenWithPeak[] {
  // Find tokens with outcome runner_3x or runner_10x, or manually calculate from swap data
  const tokens = db.prepare(
    `SELECT t.address, t.symbol, p.address as poolAddress
     FROM tokens t
     JOIN pools p ON p.token_address = t.address
     WHERE t.status IN ('watching', 'alerted', 'traded', 'archived')
     AND p.launched_at IS NOT NULL`
  ).all() as any[];

  const runners: TokenWithPeak[] = [];

  for (const token of tokens) {
    // Get price range from swaps
    const prices = db.prepare(
      `SELECT CAST(price AS REAL) as price, ts
       FROM swaps
       WHERE pool_address = ? AND price != '0' AND price IS NOT NULL
       ORDER BY ts ASC`
    ).all(token.poolAddress) as any[];

    if (prices.length < 10) continue;

    // Base price = median of first 10 swaps (post-launch)
    const firstPrices = prices.slice(0, 10).map((p: any) => p.price).sort((a: number, b: number) => a - b);
    const basePrice = firstPrices[Math.floor(firstPrices.length / 2)];
    if (basePrice <= 0) continue;

    // Peak price
    const peakPrice = Math.max(...prices.map((p: any) => p.price));
    const multiplier = peakPrice / basePrice;

    if (multiplier >= 10) {
      runners.push({
        address: token.address,
        symbol: token.symbol,
        basePrice,
        peakPrice,
        multiplier,
      });
    }
  }

  return runners;
}

function findSmartBuyers(token: TokenWithPeak): string[] {
  // Get all buys sorted by price
  const buys = db.prepare(
    `SELECT wallet, CAST(price AS REAL) as price, CAST(amount_weth AS REAL) as amountWeth
     FROM swaps
     WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND is_buy = 1 AND price != '0'
     ORDER BY price ASC`
  ).all(token.address) as any[];

  if (buys.length < 4) return [];

  // Bottom quartile = bought at low prices (first 25% of price range)
  const priceThreshold = token.basePrice + (token.peakPrice - token.basePrice) * 0.25;
  const earlyBuyers = buys.filter((b: any) => b.price <= priceThreshold);

  // Check which of these also sold (realized profit)
  const smartWallets: string[] = [];
  for (const buyer of earlyBuyers) {
    const sells = db.prepare(
      `SELECT CAST(price AS REAL) as price, CAST(amount_weth AS REAL) as amountWeth
       FROM swaps
       WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
       AND wallet = ? AND is_buy = 0`
    ).all(token.address, buyer.wallet) as any[];

    if (sells.length > 0) {
      // Check if they sold at a profit (sell price > buy price)
      const avgSellPrice = sells.reduce((s: number, x: any) => s + x.price, 0) / sells.length;
      if (avgSellPrice > buyer.price * 1.5) {
        smartWallets.push(buyer.wallet);
      }
    }
  }

  return [...new Set(smartWallets)];
}

async function main() {
  console.log('=== Smart Wallet Builder ===\n');

  // Find tokens that did 10x+
  const runners = findRunnerTokens();
  console.log(`Found ${runners.length} runner tokens (10x+)`);

  if (runners.length === 0) {
    console.log('No runner tokens found yet. Need more historical data.');
    console.log('Run the scanner for a few days to accumulate swap data.\n');
    process.exit(0);
  }

  // Track candidate points per wallet
  const candidatePoints = new Map<string, { wins: string[]; losses: number }>();

  for (const token of runners) {
    console.log(`  ${token.symbol}: ${token.multiplier.toFixed(1)}x`);
    const smartBuyers = findSmartBuyers(token);

    for (const wallet of smartBuyers) {
      const entry = candidatePoints.get(wallet) || { wins: [], losses: 0 };
      entry.wins.push(token.address);
      candidatePoints.set(wallet, entry);
    }
  }

  // Tag wallets that meet threshold
  let tagged = 0;
  for (const [wallet, data] of candidatePoints) {
    if (data.wins.length >= SMART_THRESHOLD) {
      const existing = db.prepare('SELECT tags, stats FROM wallets WHERE address = ?').get(wallet) as any;

      if (existing) {
        const tags: string[] = JSON.parse(existing.tags || '[]');
        if (!tags.includes('smart')) {
          tags.push('smart');
          const stats = JSON.parse(existing.stats || '{}');
          stats.smartReason = { qualifyingTokens: data.wins, taggedAt: Date.now() };
          db.prepare('UPDATE wallets SET tags = ?, stats = ? WHERE address = ?')
            .run(JSON.stringify(tags), JSON.stringify(stats), wallet);
          tagged++;
          console.log(`  Tagged SMART: ${wallet} (${data.wins.length} qualifying tokens)`);
        }
      } else {
        const stats = { smartReason: { qualifyingTokens: data.wins, taggedAt: Date.now() } };
        db.prepare('INSERT INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)')
          .run(wallet, '["smart"]', Math.floor(Date.now() / 1000), JSON.stringify(stats));
        tagged++;
        console.log(`  Tagged SMART: ${wallet} (${data.wins.length} qualifying tokens)`);
      }
    }
  }

  // Decay: check existing smart wallets for consecutive losers
  const smartWallets = db.prepare(
    `SELECT address, stats FROM wallets WHERE tags LIKE '%smart%'`
  ).all() as any[];

  let decayed = 0;
  for (const sw of smartWallets) {
    const stats = JSON.parse(sw.stats || '{}');
    if (stats.consecutiveLosers >= CONSECUTIVE_LOSERS_DECAY) {
      const tags: string[] = JSON.parse(
        (db.prepare('SELECT tags FROM wallets WHERE address = ?').get(sw.address) as any)?.tags || '[]'
      );
      const newTags = tags.filter(t => t !== 'smart');
      db.prepare('UPDATE wallets SET tags = ? WHERE address = ?')
        .run(JSON.stringify(newTags), sw.address);
      decayed++;
      console.log(`  Decayed SMART: ${sw.address} (${stats.consecutiveLosers} consecutive losers)`);
    }
  }

  // Summary
  const totalSmart = db.prepare(`SELECT COUNT(*) as cnt FROM wallets WHERE tags LIKE '%smart%'`).get() as any;
  const totalSniper = db.prepare(`SELECT COUNT(*) as cnt FROM wallets WHERE tags LIKE '%sniper%'`).get() as any;

  console.log(`\n=== Summary ===`);
  console.log(`  New smart tags: ${tagged}`);
  console.log(`  Decayed smart tags: ${decayed}`);
  console.log(`  Total smart wallets: ${totalSmart.cnt}`);
  console.log(`  Total sniper wallets: ${totalSniper.cnt}`);
}

main().catch(err => {
  console.error('Smart wallet builder failed:', err);
  process.exit(1);
});
