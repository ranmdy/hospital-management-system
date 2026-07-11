import { db } from '../core/db.js';
import { getTokenMeta } from '../ingestion/dexscreenerEnricher.js';
import { isSniperDominated, isSniperFlushComplete } from './sniperDetector.js';
import type { TokenFeatures } from './scorer.js';

export function collectFeatures(tokenAddress: string): TokenFeatures {
  const token = db.prepare('SELECT * FROM tokens WHERE address = ?').get(tokenAddress) as any;
  const pool = db.prepare(
    'SELECT * FROM pools WHERE token_address = ? AND launched_at IS NOT NULL ORDER BY launched_at DESC LIMIT 1'
  ).get(tokenAddress) as any;

  // Smart money confluence: check if >= 2 smart-tagged wallets bought within 15 min
  const smartMoneyConfluence = checkSmartMoneyConfluence(tokenAddress);

  // Holder growth acceleration
  const holderGrowthAccel = checkHolderGrowth(tokenAddress);

  // Buy pressure
  const buyPressure = checkBuyPressure(tokenAddress);

  // Sniper flush complete (Phase 2: live)
  const sniperFlushComplete = isSniperFlushComplete(tokenAddress);

  // Meta match
  const metaMatch = checkMetaMatch(token?.name, token?.symbol);

  // Social presence (from Dexscreener)
  const meta = getTokenMeta(tokenAddress);
  const socialPresence = !!(meta?.hasSocials && meta?.hasWebsite);

  // Dexscreener boost
  const dexscreenerBoost = !!(meta?.boostActive && meta.boostActive > 0);

  // LP locked
  const lpLocked = pool?.lp_status === 'burned' || pool?.lp_status === 'locked';

  // Fresh deployer
  const safetyReport = db.prepare(
    'SELECT report FROM safety_reports WHERE token_address = ? ORDER BY ts DESC LIMIT 1'
  ).get(tokenAddress) as any;
  let freshDeployer = false;
  if (safetyReport?.report) {
    const parsed = JSON.parse(safetyReport.report);
    const deployerCheck = parsed.checks?.find((c: any) => c.check === 'deployerHistory');
    freshDeployer = deployerCheck?.data?.isFreshDeployer || false;
  }

  // Sniper dominated (Phase 2: live)
  const sniperDominated = isSniperDominated(tokenAddress);

  // Stealth no socials (2h after launch, no socials)
  const launchedAt = pool?.launched_at || 0;
  const now = Math.floor(Date.now() / 1000);
  const twoHoursOld = (now - launchedAt) > 7200;
  const stealthNoSocials = twoHoursOld && !meta?.hasSocials;

  return {
    smartMoneyConfluence,
    holderGrowthAccel,
    buyPressure,
    sniperFlushComplete,
    metaMatch,
    socialPresence,
    dexscreenerBoost,
    lpLocked,
    freshDeployer,
    sniperDominated,
    stealthNoSocials,
  };
}

function checkSmartMoneyConfluence(tokenAddress: string): boolean {
  // Check if >= 2 smart-tagged wallets bought this token within a 15-min window
  const smartBuys = db.prepare(
    `SELECT s.wallet, s.ts FROM swaps s
     JOIN wallets w ON w.address = s.wallet
     WHERE s.pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND s.is_buy = 1
     AND w.tags LIKE '%smart%'
     ORDER BY s.ts ASC`
  ).all(tokenAddress) as any[];

  if (smartBuys.length < 2) return false;

  // Check if any 2 are within 15 minutes of each other
  for (let i = 0; i < smartBuys.length - 1; i++) {
    if (smartBuys[i + 1].ts - smartBuys[i].ts <= 900) {
      return true;
    }
  }
  return false;
}

function checkHolderGrowth(tokenAddress: string): boolean {
  // Check if holder count is accelerating across last 3 snapshots
  const snapshots = db.prepare(
    'SELECT holder_count FROM holder_snapshots WHERE token_address = ? ORDER BY ts DESC LIMIT 3'
  ).all(tokenAddress) as any[];

  if (snapshots.length < 3) return false;

  // Reverse to chronological order
  snapshots.reverse();
  const growth1 = snapshots[1].holder_count - snapshots[0].holder_count;
  const growth2 = snapshots[2].holder_count - snapshots[1].holder_count;

  // Acceleration: second growth period is bigger than first
  return growth2 > growth1 && growth2 > 0 && snapshots[2].holder_count >= 25;
}

function checkBuyPressure(tokenAddress: string): boolean {
  // Buy/sell ratio > 65% with >= 25 unique buyers in trailing window
  const stats = db.prepare(
    `SELECT
       COUNT(CASE WHEN is_buy = 1 THEN 1 END) as buys,
       COUNT(CASE WHEN is_buy = 0 THEN 1 END) as sells,
       COUNT(DISTINCT CASE WHEN is_buy = 1 THEN wallet END) as unique_buyers
     FROM swaps
     WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND ts > ?`
  ).get(tokenAddress, Math.floor(Date.now() / 1000) - 3600) as any; // Last hour

  if (!stats || (stats.buys + stats.sells) === 0) return false;

  const buyRatio = stats.buys / (stats.buys + stats.sells);
  return buyRatio > 0.65 && stats.unique_buyers >= 25;
}

function checkMetaMatch(name?: string, symbol?: string): boolean {
  if (!name && !symbol) return false;

  // Get meta tags from system_state
  const tagsRow = db.prepare('SELECT value FROM system_state WHERE key = ?').get('meta_tags') as any;
  if (!tagsRow) return false;

  const tags: string[] = JSON.parse(tagsRow.value);
  const combined = ((name || '') + ' ' + (symbol || '')).toLowerCase();

  return tags.some(tag => combined.includes(tag.toLowerCase()));
}
