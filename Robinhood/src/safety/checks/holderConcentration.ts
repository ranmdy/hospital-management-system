import { db } from '../../core/db.js';
import { SAFETY_PARAMS } from '../../config/params.js';
import type { CheckResult } from '../types.js';

export function checkHolderConcentration(tokenAddress: string): CheckResult {
  // Get the latest holder snapshot
  const snapshot = db.prepare(
    'SELECT top10_pct, deployer_pct FROM holder_snapshots WHERE token_address = ? ORDER BY ts DESC LIMIT 1'
  ).get(tokenAddress) as any;

  if (!snapshot) {
    return {
      check: 'holderConcentration',
      verdict: 'UNKNOWN',
      reason: 'No holder data available yet',
    };
  }

  const { top10_pct, deployer_pct } = snapshot;

  if (top10_pct > SAFETY_PARAMS.MAX_TOP10_HOLDER_PCT) {
    return {
      check: 'holderConcentration',
      verdict: 'VETO',
      reason: `Top-10 holders control ${top10_pct.toFixed(1)}% (max: ${SAFETY_PARAMS.MAX_TOP10_HOLDER_PCT}%)`,
      data: { top10Pct: top10_pct, deployerPct: deployer_pct },
    };
  }

  if (deployer_pct > SAFETY_PARAMS.MAX_DEPLOYER_HOLD_PCT) {
    return {
      check: 'holderConcentration',
      verdict: 'VETO',
      reason: `Deployer holds ${deployer_pct.toFixed(1)}% (max: ${SAFETY_PARAMS.MAX_DEPLOYER_HOLD_PCT}%)`,
      data: { top10Pct: top10_pct, deployerPct: deployer_pct },
    };
  }

  return {
    check: 'holderConcentration',
    verdict: 'PASS',
    reason: `Top-10: ${top10_pct.toFixed(1)}%, Deployer: ${deployer_pct.toFixed(1)}%`,
    data: { top10Pct: top10_pct, deployerPct: deployer_pct },
  };
}
