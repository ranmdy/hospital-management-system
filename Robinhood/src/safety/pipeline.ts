import { db } from '../core/db.js';
import { bus } from '../core/eventBus.js';
import { createModuleLogger } from '../core/logger.js';
import { checkMetadataSanity } from './checks/metadataSanity.js';
import { checkLiquidityFloor } from './checks/liquidityFloor.js';
import { checkHoneypotSim } from './checks/honeypotSim.js';
import { checkContractInspection } from './checks/contractInspection.js';
import { checkLpStatus } from './checks/lpStatus.js';
import { checkHolderConcentration } from './checks/holderConcentration.js';
import { checkDeployerHistory } from './checks/deployerHistory.js';
import type { SafetyReport, CheckResult, SafetyVerdict } from './types.js';

const log = createModuleLogger('safety');

export async function runSafetyPipeline(tokenAddress: string): Promise<SafetyReport> {
  log.info({ tokenAddress }, 'Running safety pipeline');

  const checks: CheckResult[] = [];

  // Run checks in order (cheap -> expensive)
  // 1. Metadata sanity (sync, cheap)
  checks.push(checkMetadataSanity(tokenAddress));
  if (checks[checks.length - 1].verdict === 'VETO') {
    return finalize(tokenAddress, checks);
  }

  // 2. Liquidity floor (sync, cheap)
  checks.push(checkLiquidityFloor(tokenAddress));
  if (checks[checks.length - 1].verdict === 'VETO') {
    return finalize(tokenAddress, checks);
  }

  // 3. Honeypot simulation (async, expensive — on-chain calls)
  checks.push(await checkHoneypotSim(tokenAddress));
  if (checks[checks.length - 1].verdict === 'VETO') {
    return finalize(tokenAddress, checks);
  }

  // 4. Contract inspection (async, moderate — Blockscout API)
  checks.push(await checkContractInspection(tokenAddress));
  if (checks[checks.length - 1].verdict === 'VETO') {
    return finalize(tokenAddress, checks);
  }

  // 5. LP status (async, moderate — on-chain reads)
  checks.push(await checkLpStatus(tokenAddress));

  // 6. Holder concentration (sync, cheap — reads from DB snapshot)
  checks.push(checkHolderConcentration(tokenAddress));
  if (checks[checks.length - 1].verdict === 'VETO') {
    return finalize(tokenAddress, checks);
  }

  // 7. Deployer history (async, moderate — Blockscout + DB)
  checks.push(await checkDeployerHistory(tokenAddress));

  return finalize(tokenAddress, checks);
}

function finalize(tokenAddress: string, checks: CheckResult[]): SafetyReport {
  // Any VETO = overall VETO
  // Any UNKNOWN (on critical checks) = overall UNKNOWN
  // All PASS = overall PASS
  let overall: SafetyVerdict = 'PASS';

  for (const check of checks) {
    if (check.verdict === 'VETO') {
      overall = 'VETO';
      break;
    }
    if (check.verdict === 'UNKNOWN') {
      overall = 'UNKNOWN';
    }
  }

  const now = Math.floor(Date.now() / 1000);
  const report: SafetyReport = { tokenAddress, overall, checks, timestamp: now };

  // Persist to DB
  db.prepare(
    `INSERT INTO safety_reports (token_address, ts, overall, report) VALUES (?, ?, ?, ?)`
  ).run(tokenAddress, now, overall, JSON.stringify(report));

  // Update token status if vetoed
  if (overall === 'VETO') {
    db.prepare(`UPDATE tokens SET status = 'vetoed' WHERE address = ?`).run(tokenAddress);
    bus.emit('token:vetoed', {
      tokenAddress: tokenAddress as `0x${string}`,
      reason: checks.find(c => c.verdict === 'VETO')?.reason || 'Safety veto',
    });
  }

  log.info({
    tokenAddress,
    overall,
    checks: checks.map(c => `${c.check}:${c.verdict}`).join(', '),
  }, 'Safety pipeline complete');

  return report;
}
