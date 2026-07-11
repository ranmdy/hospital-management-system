import { createModuleLogger } from '../../core/logger.js';
import type { CheckResult } from '../types.js';

const log = createModuleLogger('contractInspection');

const BLOCKSCOUT_API = 'https://robinhoodchain.blockscout.com/api/v2';

// Dangerous patterns to look for in source code
const DANGEROUS_PATTERNS = [
  { pattern: /function\s+mint\s*\(/i, flag: 'owner_mint', severity: 'high' as const },
  { pattern: /function\s+_?blacklist|function\s+_?addToBlacklist|mapping.*blacklist/i, flag: 'blacklist', severity: 'high' as const },
  { pattern: /function\s+_?whitelist|mapping.*whitelist/i, flag: 'whitelist', severity: 'medium' as const },
  { pattern: /function\s+set(Fee|Tax|Rate)\s*\(/i, flag: 'settable_fees', severity: 'high' as const },
  { pattern: /function\s+(pause|unpause|toggleTrading|enableTrading)/i, flag: 'trading_pause', severity: 'high' as const },
  { pattern: /maxTransaction|maxTx|_maxTxAmount/i, flag: 'max_tx_limit', severity: 'medium' as const },
  { pattern: /maxWallet|_maxWalletSize/i, flag: 'max_wallet_limit', severity: 'medium' as const },
  { pattern: /delegatecall|upgradeTo|implementation/i, flag: 'proxy_upgradeable', severity: 'high' as const },
];

const POSITIVE_PATTERNS = [
  { pattern: /renounceOwnership.*owner\s*=\s*address\(0\)|_transferOwnership\(address\(0\)\)/i, flag: 'ownership_renounced' },
];

export async function checkContractInspection(tokenAddress: string): Promise<CheckResult> {
  try {
    const res = await fetch(`${BLOCKSCOUT_API}/smart-contracts/${tokenAddress}`);

    if (res.status === 404) {
      return {
        check: 'contractInspection',
        verdict: 'UNKNOWN',
        reason: 'Contract not verified on Blockscout',
        data: { verified: false },
      };
    }

    if (!res.ok) {
      return {
        check: 'contractInspection',
        verdict: 'UNKNOWN',
        reason: `Blockscout API error: ${res.status}`,
      };
    }

    const contract = await res.json() as any;

    if (!contract.source_code) {
      return {
        check: 'contractInspection',
        verdict: 'UNKNOWN',
        reason: 'Contract source code not available',
        data: { verified: false },
      };
    }

    const source = contract.source_code;
    const flags: string[] = [];
    const highSeverityFlags: string[] = [];

    for (const { pattern, flag, severity } of DANGEROUS_PATTERNS) {
      if (pattern.test(source)) {
        flags.push(flag);
        if (severity === 'high') {
          highSeverityFlags.push(flag);
        }
      }
    }

    // Check for positive signals
    const positiveFlags: string[] = [];
    for (const { pattern, flag } of POSITIVE_PATTERNS) {
      if (pattern.test(source)) {
        positiveFlags.push(flag);
      }
    }

    // Check owner status
    const ownerRow = await checkOwnerAddress(tokenAddress);

    const data = {
      verified: true,
      contractName: contract.name,
      flags,
      positiveFlags,
      ownerIsZero: ownerRow,
    };

    // Multiple high-severity flags = VETO
    if (highSeverityFlags.length >= 2) {
      return {
        check: 'contractInspection',
        verdict: 'VETO',
        reason: `Multiple dangerous patterns: ${highSeverityFlags.join(', ')}`,
        data,
      };
    }

    // Single high-severity with no renounce = UNKNOWN
    if (highSeverityFlags.length === 1 && !positiveFlags.includes('ownership_renounced')) {
      return {
        check: 'contractInspection',
        verdict: 'UNKNOWN',
        reason: `Dangerous pattern detected: ${highSeverityFlags[0]} (owner not renounced)`,
        data,
      };
    }

    return {
      check: 'contractInspection',
      verdict: 'PASS',
      reason: flags.length > 0
        ? `Verified. Minor flags: ${flags.join(', ')}`
        : 'Verified, no dangerous patterns detected',
      data,
    };
  } catch (err: any) {
    log.error({ tokenAddress, err: err.message }, 'Contract inspection error');
    return {
      check: 'contractInspection',
      verdict: 'UNKNOWN',
      reason: `Inspection error: ${err.message?.slice(0, 100)}`,
    };
  }
}

async function checkOwnerAddress(tokenAddress: string): Promise<boolean> {
  try {
    // Call owner() on the token - selector 0x8da5cb5b
    const { httpClient } = await import('../../core/client.js');
    const result = await httpClient.call({
      to: tokenAddress as `0x${string}`,
      data: '0x8da5cb5b',
    });

    if (result.data) {
      const owner = '0x' + result.data.slice(26);
      return owner === '0x0000000000000000000000000000000000000000';
    }
    return false;
  } catch {
    return false;
  }
}
