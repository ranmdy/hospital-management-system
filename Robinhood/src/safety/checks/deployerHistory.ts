import { db } from '../../core/db.js';
import { SAFETY_PARAMS } from '../../config/params.js';
import { createModuleLogger } from '../../core/logger.js';
import type { CheckResult } from '../types.js';

const log = createModuleLogger('deployerHistory');

const BLOCKSCOUT_API = 'https://robinhoodchain.blockscout.com/api/v2';

export async function checkDeployerHistory(tokenAddress: string): Promise<CheckResult> {
  const token = db.prepare('SELECT deployer, created_at FROM tokens WHERE address = ?').get(tokenAddress) as any;

  if (!token?.deployer) {
    // Try to find deployer from Blockscout
    const deployer = await fetchDeployer(tokenAddress);
    if (!deployer) {
      return {
        check: 'deployerHistory',
        verdict: 'UNKNOWN',
        reason: 'Deployer address unknown',
      };
    }
    db.prepare('UPDATE tokens SET deployer = ? WHERE address = ?').run(deployer, tokenAddress);
    token.deployer = deployer;
  }

  const deployer = token.deployer;

  // Check our own DB for prior tokens by this deployer
  const priorTokens = db.prepare(
    `SELECT address, symbol, outcome, status FROM tokens
     WHERE deployer = ? AND address != ?
     ORDER BY created_at DESC`
  ).all(deployer, tokenAddress) as any[];

  const rugCount = priorTokens.filter(t => t.outcome === 'rug' || t.status === 'vetoed').length;
  const totalPrior = priorTokens.length;

  // Check if deployer wallet is fresh (funded recently)
  const walletRow = db.prepare('SELECT first_seen, tags FROM wallets WHERE address = ?').get(deployer) as any;
  let isFreshDeployer = false;

  if (!walletRow) {
    // New wallet we haven't seen before — check creation time
    const deployerAge = await fetchWalletAge(deployer);
    const now = Math.floor(Date.now() / 1000);

    db.prepare(
      `INSERT OR IGNORE INTO wallets (address, tags, first_seen, stats) VALUES (?, ?, ?, ?)`
    ).run(deployer, JSON.stringify(['deployer']), deployerAge || now, JSON.stringify({ tokensDeployed: totalPrior + 1 }));

    if (deployerAge && (now - deployerAge) < 3600) {
      isFreshDeployer = true;
    }
  }

  // Serial rugger check
  if (rugCount >= SAFETY_PARAMS.SERIAL_RUG_MIN_COUNT) {
    return {
      check: 'deployerHistory',
      verdict: 'VETO',
      reason: `Serial rugger: ${rugCount} prior rugs out of ${totalPrior} tokens`,
      data: { deployer, rugCount, totalPrior, isFreshDeployer },
    };
  }

  return {
    check: 'deployerHistory',
    verdict: 'PASS',
    reason: totalPrior > 0
      ? `Deployer has ${totalPrior} prior tokens (${rugCount} rugs)`
      : `First token from this deployer${isFreshDeployer ? ' (fresh wallet)' : ''}`,
    data: { deployer, rugCount, totalPrior, isFreshDeployer },
  };
}

async function fetchDeployer(tokenAddress: string): Promise<string | null> {
  try {
    const res = await fetch(`${BLOCKSCOUT_API}/addresses/${tokenAddress}`);
    if (!res.ok) return null;
    const data = await res.json() as any;
    return data.creator_address_hash || null;
  } catch {
    return null;
  }
}

async function fetchWalletAge(address: string): Promise<number | null> {
  try {
    const res = await fetch(`${BLOCKSCOUT_API}/addresses/${address}/transactions?limit=1&sort=asc`);
    if (!res.ok) return null;
    const data = await res.json() as any;
    const items = data.items || [];
    if (items.length === 0) return null;
    return Math.floor(new Date(items[0].timestamp).getTime() / 1000);
  } catch {
    return null;
  }
}
