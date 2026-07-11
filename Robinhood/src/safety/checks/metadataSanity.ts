import { db } from '../../core/db.js';
import { BLOCKLISTED_SYMBOLS } from '../../config/params.js';
import type { CheckResult } from '../types.js';

export function checkMetadataSanity(tokenAddress: string): CheckResult {
  const token = db.prepare(
    'SELECT name, symbol, decimals, total_supply FROM tokens WHERE address = ?'
  ).get(tokenAddress) as any;

  if (!token) {
    return { check: 'metadataSanity', verdict: 'UNKNOWN', reason: 'Token not found in DB' };
  }

  // Check decimals
  if (token.decimals === null || token.decimals < 0 || token.decimals > 18) {
    return {
      check: 'metadataSanity',
      verdict: 'VETO',
      reason: `Invalid decimals: ${token.decimals}`,
    };
  }

  // Check totalSupply
  if (!token.total_supply || BigInt(token.total_supply) <= 0n) {
    return {
      check: 'metadataSanity',
      verdict: 'VETO',
      reason: 'Total supply is zero or missing',
    };
  }

  // Check name/symbol non-empty
  if (!token.name || !token.symbol) {
    return {
      check: 'metadataSanity',
      verdict: 'VETO',
      reason: 'Name or symbol is empty',
    };
  }

  // Check impersonation
  const upperSymbol = token.symbol.toUpperCase();
  if (BLOCKLISTED_SYMBOLS.includes(upperSymbol as any)) {
    return {
      check: 'metadataSanity',
      verdict: 'VETO',
      reason: `Symbol "${token.symbol}" impersonates a known asset`,
    };
  }

  return {
    check: 'metadataSanity',
    verdict: 'PASS',
    reason: 'Metadata valid',
    data: { name: token.name, symbol: token.symbol, decimals: token.decimals },
  };
}
