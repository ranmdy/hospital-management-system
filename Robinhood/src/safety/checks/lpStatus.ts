import { type Address } from 'viem';
import { httpClient } from '../../core/client.js';
import { db } from '../../core/db.js';
import { contracts, ERC20_ABI } from '../../config/contracts.js';
import { createModuleLogger } from '../../core/logger.js';
import type { CheckResult } from '../types.js';

const log = createModuleLogger('lpStatus');

const BURN_ADDRESSES: Address[] = [
  '0x0000000000000000000000000000000000000000',
  '0x000000000000000000000000000000000000dEaD',
];

// Known locker contracts (add more as discovered)
const KNOWN_LOCKERS: Address[] = [];

export async function checkLpStatus(tokenAddress: string): Promise<CheckResult> {
  const pool = db.prepare(
    'SELECT address, amm, lp_provider FROM pools WHERE token_address = ? AND launched_at IS NOT NULL ORDER BY launched_at DESC LIMIT 1'
  ).get(tokenAddress) as any;

  if (!pool) {
    return { check: 'lpStatus', verdict: 'UNKNOWN', reason: 'No launched pool found' };
  }

  if (pool.amm === 'univ2') {
    return await checkV2LpStatus(pool.address as Address, pool.lp_provider);
  }

  // V3: check if the position NFT was burned or sent to a locker
  // This is harder for V3 — need to check NonfungiblePositionManager positions
  // For now, mark as UNKNOWN for V3 (we'd need to track the specific tokenId)
  return {
    check: 'lpStatus',
    verdict: 'UNKNOWN',
    reason: 'V3 LP position tracking not yet implemented',
    data: { amm: 'univ3', poolAddress: pool.address },
  };
}

async function checkV2LpStatus(pairAddress: Address, lpProvider: string | null): Promise<CheckResult> {
  try {
    // Check LP token balances at burn addresses
    let burnedAmount = 0n;
    let lockedAmount = 0n;
    let totalSupply = 0n;

    try {
      totalSupply = await httpClient.readContract({
        address: pairAddress,
        abi: ERC20_ABI,
        functionName: 'totalSupply',
      });
    } catch {
      return { check: 'lpStatus', verdict: 'UNKNOWN', reason: 'Cannot read LP totalSupply' };
    }

    if (totalSupply === 0n) {
      return { check: 'lpStatus', verdict: 'UNKNOWN', reason: 'LP total supply is 0' };
    }

    for (const burnAddr of BURN_ADDRESSES) {
      try {
        const balance = await httpClient.readContract({
          address: pairAddress,
          abi: ERC20_ABI,
          functionName: 'balanceOf',
          args: [burnAddr],
        });
        burnedAmount += balance;
      } catch {
        // Ignore individual failures
      }
    }

    for (const locker of KNOWN_LOCKERS) {
      try {
        const balance = await httpClient.readContract({
          address: pairAddress,
          abi: ERC20_ABI,
          functionName: 'balanceOf',
          args: [locker],
        });
        lockedAmount += balance;
      } catch {
        // Ignore
      }
    }

    const burnedPct = Number(burnedAmount * 10000n / totalSupply) / 100;
    const lockedPct = Number(lockedAmount * 10000n / totalSupply) / 100;
    const securePct = burnedPct + lockedPct;

    // Update pool record
    let status = 'unknown';
    if (burnedPct > 90) status = 'burned';
    else if (lockedPct > 90) status = 'locked';
    else if (securePct > 90) status = 'secured';
    else status = 'unlocked';

    db.prepare('UPDATE pools SET lp_status = ? WHERE address = ?').run(status, pairAddress);

    if (securePct > 90) {
      return {
        check: 'lpStatus',
        verdict: 'PASS',
        reason: `LP ${status} (${securePct.toFixed(1)}% secured)`,
        data: { burnedPct, lockedPct, status },
      };
    }

    if (securePct < 10) {
      // LP held by deployer — not a veto, but a flag
      return {
        check: 'lpStatus',
        verdict: 'PASS',
        reason: `LP unlocked (${securePct.toFixed(1)}% secured) — deployer holds LP`,
        data: { burnedPct, lockedPct, status: 'unlocked', lpProvider },
      };
    }

    return {
      check: 'lpStatus',
      verdict: 'PASS',
      reason: `LP partially secured (${securePct.toFixed(1)}%)`,
      data: { burnedPct, lockedPct, status },
    };
  } catch (err: any) {
    log.error({ pairAddress, err: err.message }, 'LP status check error');
    return {
      check: 'lpStatus',
      verdict: 'UNKNOWN',
      reason: `LP check error: ${err.message?.slice(0, 100)}`,
    };
  }
}
