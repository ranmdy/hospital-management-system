import { type Address } from 'viem';
import { db } from '../core/db.js';
import { createModuleLogger } from '../core/logger.js';

const log = createModuleLogger('holderScanner');

const BLOCKSCOUT_API = 'https://robinhoodchain.blockscout.com/api/v2';
const SCAN_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
const RATE_LIMIT_DELAY_MS = 2000; // 2s between requests

interface TokenToScan {
  address: string;
  deployer: string | null;
}

async function fetchHolderData(tokenAddress: string): Promise<{
  holderCount: number;
  top10Pct: number;
  deployerPct: number;
} | null> {
  try {
    // Get token counters
    const countersRes = await fetch(`${BLOCKSCOUT_API}/tokens/${tokenAddress}/counters`);
    if (!countersRes.ok) {
      log.debug({ tokenAddress, status: countersRes.status }, 'Blockscout counters request failed');
      return null;
    }
    const counters = await countersRes.json() as any;
    const holderCount = parseInt(counters.token_holders_count || '0', 10);

    // Get top holders
    const holdersRes = await fetch(`${BLOCKSCOUT_API}/tokens/${tokenAddress}/holders?limit=10`);
    if (!holdersRes.ok) {
      return { holderCount, top10Pct: 0, deployerPct: 0 };
    }
    const holdersData = await holdersRes.json() as any;
    const holders = holdersData.items || [];

    // Calculate top-10 concentration (excluding pools and burn addresses)
    const burnAddresses = new Set([
      '0x0000000000000000000000000000000000000000',
      '0x000000000000000000000000000000000000dead',
    ]);

    let top10Total = 0;
    let deployerPct = 0;
    const tokenRow = db.prepare('SELECT deployer, total_supply FROM tokens WHERE address = ?').get(tokenAddress) as any;
    const totalSupply = tokenRow?.total_supply ? BigInt(tokenRow.total_supply) : 0n;

    for (const holder of holders) {
      const addr = (holder.address?.hash || '').toLowerCase();
      if (burnAddresses.has(addr)) continue;

      const pct = parseFloat(holder.value || '0') / (totalSupply > 0n ? Number(totalSupply) : 1) * 100;
      top10Total += pct;

      if (tokenRow?.deployer && addr === tokenRow.deployer.toLowerCase()) {
        deployerPct = pct;
      }
    }

    return { holderCount, top10Pct: top10Total, deployerPct };
  } catch (err) {
    log.error({ tokenAddress, err }, 'Holder scan error');
    return null;
  }
}

function getTokensToScan(): TokenToScan[] {
  return db.prepare(
    `SELECT address, deployer FROM tokens
     WHERE status IN ('watching', 'alerted', 'traded')
     ORDER BY created_at DESC
     LIMIT 50`
  ).all() as TokenToScan[];
}

export async function startHolderScanner() {
  log.info('Starting holder scanner');

  const scan = async () => {
    const tokens = getTokensToScan();
    if (tokens.length === 0) return;

    for (const token of tokens) {
      const data = await fetchHolderData(token.address);
      if (!data) continue;

      const now = Math.floor(Date.now() / 1000);
      db.prepare(
        `INSERT INTO holder_snapshots (token_address, ts, holder_count, top10_pct, deployer_pct)
         VALUES (?, ?, ?, ?, ?)`
      ).run(token.address, now, data.holderCount, data.top10Pct, data.deployerPct);

      log.debug({
        tokenAddress: token.address,
        holders: data.holderCount,
        top10: data.top10Pct.toFixed(1),
      }, 'Holder snapshot recorded');

      // Rate limit
      await new Promise(r => setTimeout(r, RATE_LIMIT_DELAY_MS));
    }
  };

  // Run immediately then on interval
  await scan();
  const interval = setInterval(scan, SCAN_INTERVAL_MS);

  log.info('Holder scanner active');
  return () => clearInterval(interval);
}
