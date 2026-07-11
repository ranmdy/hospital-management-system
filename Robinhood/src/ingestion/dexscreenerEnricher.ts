import { type Address } from 'viem';
import { bus } from '../core/eventBus.js';
import { db } from '../core/db.js';
import { createModuleLogger } from '../core/logger.js';

const log = createModuleLogger('dexscreener');

const DEXSCREENER_API = 'https://api.dexscreener.com';
const RATE_LIMIT_DELAY_MS = 1100; // ~60 req/min
const ENRICH_INTERVAL_MS = 5 * 60 * 1000; // Re-enrich hot tokens every 5 min

interface DexscreenerPair {
  pairAddress: string;
  baseToken: { address: string; name: string; symbol: string };
  quoteToken: { address: string; name: string; symbol: string };
  priceUsd: string | null;
  volume: { h24: number } | null;
  liquidity: { usd: number } | null;
  fdv: number | null;
  pairCreatedAt: number | null;
  info?: {
    websites?: { url: string }[];
    socials?: { type: string; url: string }[];
  };
  boosts?: { active: number };
}

async function fetchPairData(tokenAddress: string): Promise<DexscreenerPair | null> {
  try {
    const res = await fetch(
      `${DEXSCREENER_API}/token-pairs/v1/robinhood/${tokenAddress}`
    );
    if (!res.ok) {
      log.debug({ tokenAddress, status: res.status }, 'Dexscreener request failed');
      return null;
    }

    const pairs = await res.json() as DexscreenerPair[];
    if (!pairs || pairs.length === 0) return null;

    // Return the pair with highest liquidity
    return pairs.sort((a, b) => (b.liquidity?.usd || 0) - (a.liquidity?.usd || 0))[0];
  } catch (err) {
    log.error({ tokenAddress, err }, 'Dexscreener fetch error');
    return null;
  }
}

function storePairData(tokenAddress: string, pair: DexscreenerPair) {
  const now = Math.floor(Date.now() / 1000);

  const hasSocials = !!(pair.info?.socials && pair.info.socials.length > 0);
  const hasWebsite = !!(pair.info?.websites && pair.info.websites.length > 0);
  const boostActive = pair.boosts?.active || 0;

  const meta = {
    priceUsd: pair.priceUsd,
    volume24h: pair.volume?.h24 || 0,
    liquidityUsd: pair.liquidity?.usd || 0,
    fdv: pair.fdv || 0,
    hasSocials,
    hasWebsite,
    socials: pair.info?.socials || [],
    websites: pair.info?.websites || [],
    boostActive,
    fetchedAt: now,
  };

  // Store in system_state as token_meta:<address>
  db.prepare(
    `INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)`
  ).run(`token_meta:${tokenAddress}`, JSON.stringify(meta));

  log.debug({
    tokenAddress,
    priceUsd: pair.priceUsd,
    volume24h: meta.volume24h,
    hasSocials,
    boostActive,
  }, 'Dexscreener data stored');
}

export function getTokenMeta(tokenAddress: string): any | null {
  const row = db.prepare('SELECT value FROM system_state WHERE key = ?').get(
    `token_meta:${tokenAddress}`
  ) as { value: string } | undefined;
  return row ? JSON.parse(row.value) : null;
}

export async function startDexscreenerEnricher() {
  log.info('Starting Dexscreener enricher');

  // Enrich on pool:launched
  bus.on('pool:launched', async (data) => {
    // Small delay to let Dexscreener index the pair
    await new Promise(r => setTimeout(r, 30_000));

    const pair = await fetchPairData(data.tokenAddress);
    if (pair) {
      storePairData(data.tokenAddress, pair);
    }
  });

  // Periodic re-enrichment for hot tokens
  const interval = setInterval(async () => {
    const hotTokens = db.prepare(
      `SELECT address FROM tokens
       WHERE status IN ('watching', 'alerted', 'traded')
       AND created_at > ?
       ORDER BY created_at DESC
       LIMIT 20`
    ).all(Math.floor(Date.now() / 1000) - 86400) as { address: string }[];

    for (const token of hotTokens) {
      const pair = await fetchPairData(token.address);
      if (pair) {
        storePairData(token.address, pair);
      }
      await new Promise(r => setTimeout(r, RATE_LIMIT_DELAY_MS));
    }
  }, ENRICH_INTERVAL_MS);

  log.info('Dexscreener enricher active');
  return () => clearInterval(interval);
}
