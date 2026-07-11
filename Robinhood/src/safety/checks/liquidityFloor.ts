import { db } from '../../core/db.js';
import { SAFETY_PARAMS } from '../../config/params.js';
import { contracts } from '../../config/contracts.js';
import { formatEther } from 'viem';
import type { CheckResult } from '../types.js';

// VIRTUAL-paired tokens use a different minimum (VIRTUAL has its own value)
// For now, treat any quote token liquidity > threshold as passing
// since both WETH and VIRTUAL have similar $ value ranges on this chain
const MIN_LIQUIDITY_VIRTUAL = 100n * 10n ** 18n; // 100 VIRTUAL minimum

export function checkLiquidityFloor(tokenAddress: string): CheckResult {
  const pool = db.prepare(
    'SELECT initial_liquidity_weth, quote_token FROM pools WHERE token_address = ? AND launched_at IS NOT NULL ORDER BY launched_at DESC LIMIT 1'
  ).get(tokenAddress) as any;

  if (!pool || !pool.initial_liquidity_weth) {
    return {
      check: 'liquidityFloor',
      verdict: 'UNKNOWN',
      reason: 'No liquidity data available',
    };
  }

  const liquidity = BigInt(pool.initial_liquidity_weth);
  const quoteToken = (pool.quote_token || '').toLowerCase();
  const isVirtualPair = quoteToken === contracts.VIRTUAL.toLowerCase();

  const minLiquidity = isVirtualPair ? MIN_LIQUIDITY_VIRTUAL : SAFETY_PARAMS.MIN_INITIAL_LIQUIDITY_WETH;
  const unit = isVirtualPair ? 'VIRTUAL' : 'ETH';

  if (liquidity < minLiquidity) {
    return {
      check: 'liquidityFloor',
      verdict: 'VETO',
      reason: `Initial liquidity ${formatEther(liquidity)} ${unit} below minimum ${formatEther(minLiquidity)} ${unit}`,
      data: { liquidityWei: liquidity.toString(), minWei: minLiquidity.toString(), quoteToken: unit },
    };
  }

  return {
    check: 'liquidityFloor',
    verdict: 'PASS',
    reason: `Liquidity ${formatEther(liquidity)} ${unit} meets minimum`,
    data: { liquidityWei: liquidity.toString(), quoteToken: unit },
  };
}
