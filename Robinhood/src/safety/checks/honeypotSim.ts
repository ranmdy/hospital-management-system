import { type Address, encodeFunctionData, decodeFunctionResult, parseEther, formatEther } from 'viem';
import { httpClient } from '../../core/client.js';
import { contracts } from '../../config/contracts.js';
import { SAFETY_PARAMS } from '../../config/params.js';
import { db } from '../../core/db.js';
import { createModuleLogger } from '../../core/logger.js';
import type { CheckResult } from '../types.js';

const log = createModuleLogger('honeypotSim');

// Minimal ABI for simulation
const QUOTER_ABI = [
  {
    type: 'function',
    name: 'quoteExactInputSingle',
    inputs: [{
      type: 'tuple',
      name: 'params',
      components: [
        { name: 'tokenIn', type: 'address' },
        { name: 'tokenOut', type: 'address' },
        { name: 'amountIn', type: 'uint256' },
        { name: 'fee', type: 'uint24' },
        { name: 'sqrtPriceLimitX96', type: 'uint160' },
      ],
    }],
    outputs: [
      { name: 'amountOut', type: 'uint256' },
      { name: 'sqrtPriceX96After', type: 'uint160' },
      { name: 'initializedTicksCrossed', type: 'uint32' },
      { name: 'gasEstimate', type: 'uint256' },
    ],
    stateMutability: 'nonpayable',
  },
] as const;

const SIM_AMOUNT = parseEther('0.01'); // Simulate with 0.01 ETH

export async function checkHoneypotSim(tokenAddress: string): Promise<CheckResult> {
  const pool = db.prepare(
    'SELECT address, fee_tier, amm FROM pools WHERE token_address = ? AND launched_at IS NOT NULL ORDER BY launched_at DESC LIMIT 1'
  ).get(tokenAddress) as any;

  if (!pool) {
    return { check: 'honeypotSim', verdict: 'UNKNOWN', reason: 'No launched pool found' };
  }

  // Only V3 simulation for now (V2 would need router simulation)
  if (pool.amm !== 'univ3') {
    return await simulateV2Honeypot(tokenAddress, pool.address);
  }

  const fee = pool.fee_tier || 500; // Default to 500 if not set

  try {
    // Step 1: Simulate BUY (WETH -> Token)
    const buyCallData = encodeFunctionData({
      abi: QUOTER_ABI,
      functionName: 'quoteExactInputSingle',
      args: [{
        tokenIn: contracts.WETH,
        tokenOut: tokenAddress as Address,
        amountIn: SIM_AMOUNT,
        fee,
        sqrtPriceLimitX96: 0n,
      }],
    });

    const buyResult = await httpClient.call({
      to: contracts.UNISWAP_V3_QUOTER_V2,
      data: buyCallData,
    });

    if (!buyResult.data) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'Buy simulation returned no data (possible honeypot)' };
    }

    const [tokensReceived] = decodeFunctionResult({
      abi: QUOTER_ABI,
      functionName: 'quoteExactInputSingle',
      data: buyResult.data,
    }) as [bigint, bigint, number, bigint];

    if (tokensReceived === 0n) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'Buy simulation returned 0 tokens' };
    }

    // Step 2: Simulate SELL (Token -> WETH)
    const sellCallData = encodeFunctionData({
      abi: QUOTER_ABI,
      functionName: 'quoteExactInputSingle',
      args: [{
        tokenIn: tokenAddress as Address,
        tokenOut: contracts.WETH,
        amountIn: tokensReceived,
        fee,
        sqrtPriceLimitX96: 0n,
      }],
    });

    const sellResult = await httpClient.call({
      to: contracts.UNISWAP_V3_QUOTER_V2,
      data: sellCallData,
    });

    if (!sellResult.data) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'Sell simulation reverted (honeypot confirmed)' };
    }

    const [wethReturned] = decodeFunctionResult({
      abi: QUOTER_ABI,
      functionName: 'quoteExactInputSingle',
      data: sellResult.data,
    }) as [bigint, bigint, number, bigint];

    if (wethReturned === 0n) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'Sell returns 0 WETH (honeypot)' };
    }

    // Step 3: Calculate round-trip tax
    const loss = SIM_AMOUNT - wethReturned;
    const lossPct = Number(loss * 10000n / SIM_AMOUNT) / 100; // percentage with 2 decimal places

    // Estimate buy/sell tax split (approximate)
    const buyTax = 0; // Can't easily separate without mid-price
    const sellTax = lossPct; // Attribute all to total tax for now

    if (lossPct > SAFETY_PARAMS.MAX_IMPLIED_TAX_PCT) {
      return {
        check: 'honeypotSim',
        verdict: 'VETO',
        reason: `Round-trip tax ${lossPct.toFixed(1)}% exceeds max ${SAFETY_PARAMS.MAX_IMPLIED_TAX_PCT}%`,
        data: { roundTripTaxPct: lossPct, wethIn: formatEther(SIM_AMOUNT), wethOut: formatEther(wethReturned) },
      };
    }

    return {
      check: 'honeypotSim',
      verdict: 'PASS',
      reason: `Round-trip tax ${lossPct.toFixed(1)}% (within ${SAFETY_PARAMS.MAX_IMPLIED_TAX_PCT}% limit)`,
      data: { roundTripTaxPct: lossPct, tokensReceived: tokensReceived.toString(), wethReturned: formatEther(wethReturned) },
    };
  } catch (err: any) {
    log.warn({ tokenAddress, err: err.message }, 'Honeypot simulation failed');
    return {
      check: 'honeypotSim',
      verdict: 'UNKNOWN',
      reason: `Simulation error: ${err.message?.slice(0, 100)}`,
    };
  }
}

const V2_ROUTER_ABI = [
  {
    type: 'function',
    name: 'getAmountsOut',
    inputs: [
      { name: 'amountIn', type: 'uint256' },
      { name: 'path', type: 'address[]' },
    ],
    outputs: [{ name: 'amounts', type: 'uint256[]' }],
    stateMutability: 'view',
  },
] as const;

async function simulateV2Honeypot(tokenAddress: string, poolAddress: string): Promise<CheckResult> {
  try {
    // Get the quote token for this pool
    const pool = db.prepare('SELECT quote_token FROM pools WHERE address = ?').get(poolAddress) as any;
    const quoteToken = pool?.quote_token || contracts.WETH;

    // Step 1: Simulate BUY (quote -> token)
    const buyCallData = encodeFunctionData({
      abi: V2_ROUTER_ABI,
      functionName: 'getAmountsOut',
      args: [SIM_AMOUNT, [quoteToken as Address, tokenAddress as Address]],
    });

    const buyResult = await httpClient.call({
      to: contracts.UNISWAP_V3_SWAP_ROUTER, // SmartRouter handles V2 too
      data: buyCallData,
    });

    if (!buyResult.data) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'V2 buy simulation failed (no output)' };
    }

    const [buyAmounts] = decodeFunctionResult({
      abi: V2_ROUTER_ABI,
      functionName: 'getAmountsOut',
      data: buyResult.data,
    }) as unknown as [bigint[]];

    const tokensReceived = buyAmounts[buyAmounts.length - 1];
    if (!tokensReceived || tokensReceived === 0n) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'V2 buy returns 0 tokens' };
    }

    // Step 2: Simulate SELL (token -> quote)
    const sellCallData = encodeFunctionData({
      abi: V2_ROUTER_ABI,
      functionName: 'getAmountsOut',
      args: [tokensReceived, [tokenAddress as Address, quoteToken as Address]],
    });

    const sellResult = await httpClient.call({
      to: contracts.UNISWAP_V3_SWAP_ROUTER,
      data: sellCallData,
    });

    if (!sellResult.data) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'V2 sell simulation reverted (honeypot)' };
    }

    const [sellAmounts] = decodeFunctionResult({
      abi: V2_ROUTER_ABI,
      functionName: 'getAmountsOut',
      data: sellResult.data,
    }) as unknown as [bigint[]];

    const quoteReturned = sellAmounts[sellAmounts.length - 1];
    if (!quoteReturned || quoteReturned === 0n) {
      return { check: 'honeypotSim', verdict: 'VETO', reason: 'V2 sell returns 0 (honeypot)' };
    }

    // Step 3: Calculate round-trip tax
    const loss = SIM_AMOUNT - quoteReturned;
    const lossPct = Number(loss * 10000n / SIM_AMOUNT) / 100;

    if (lossPct > SAFETY_PARAMS.MAX_IMPLIED_TAX_PCT) {
      return {
        check: 'honeypotSim',
        verdict: 'VETO',
        reason: `V2 round-trip tax ${lossPct.toFixed(1)}% exceeds max ${SAFETY_PARAMS.MAX_IMPLIED_TAX_PCT}%`,
        data: { roundTripTaxPct: lossPct, amm: 'univ2' },
      };
    }

    return {
      check: 'honeypotSim',
      verdict: 'PASS',
      reason: `V2 round-trip tax ${lossPct.toFixed(1)}% (within limit)`,
      data: { roundTripTaxPct: lossPct, tokensReceived: tokensReceived.toString(), amm: 'univ2' },
    };
  } catch (err: any) {
    log.warn({ tokenAddress, err: err.message }, 'V2 honeypot simulation failed');
    return {
      check: 'honeypotSim',
      verdict: 'UNKNOWN',
      reason: `V2 simulation error: ${err.message?.slice(0, 80)}`,
      data: { amm: 'univ2' },
    };
  }
}
