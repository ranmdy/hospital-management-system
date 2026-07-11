import type { Address } from 'viem';

// Verified on-chain addresses for Robinhood Chain (mainnet, chain ID 4663)
// Each address must be verified via Blockscout or official deployment registries.
// Run `npm run discover` to validate all addresses.

export const contracts = {
  // Core chain contracts (from official docs)
  WETH: '0x0Bd7D308f8E1639FAb988df18A8011f41EAcAD73' as Address,
  MULTICALL: '0x2cAC2D899eCC914d704FeaAE33ac1bF36277DaD1' as Address,
  PERMIT2: '0x000000000022D473030F116dDEE9F6B43aC78BA3' as Address,
  // Virtuals Protocol token — primary quote token for memecoins on this chain
  VIRTUAL: '0xc6911796042b15d7Fa4F6CDe69e245DdCd3d9c31' as Address,

  // Uniswap V3 (PancakeSwap-style deployment, verified via on-chain calls)
  // SmartRouter.factory() and QuoterV2.factory() both return this address
  // feeAmountTickSpacing: 100->1, 500->10, 2500->50, 10000->200 (3000 not enabled)
  UNISWAP_V3_FACTORY: '0x0eC554F0bFf0bE6C99d1E95C8015Bb0950f6a2C7' as Address,
  // SmartRouter: verified via WETH9() returning correct WETH address
  UNISWAP_V3_SWAP_ROUTER: '0x532161bD7b0Cfa3a72D39602C0ea84f449d749b8' as Address,
  // Multiple UniversalRouter deployments exist; using most recent verified
  UNISWAP_V3_UNIVERSAL_ROUTER: '0x06AfBA43Fd06227fA663b0DAecF536f6EaA6bf99' as Address,
  // Verified: factory() returns 0xee29b23... (alternate V3 factory — monitor both)
  UNISWAP_V3_POSITION_MANAGER: '0xB1F6F1cD4060cC592F21C3F35006AdD27d7aa3e5' as Address,
  // Verified: factory() returns main V3 factory
  UNISWAP_V3_QUOTER_V2: '0x4CF566F002B575e730E9cFc0182f76De6CE3cdD0' as Address,
  // Alternate V3 factory (used by NonfungiblePositionManager) — also monitor for PoolCreated
  UNISWAP_V3_FACTORY_ALT: '0xeE29b23120358C1c48C360439f0b872628aE9Dd2' as Address,

  // Uniswap V2 — verified via allPairsLength() = 4821 pairs
  // Found by calling factory() on a known UniswapV2Pair (0x60E50467...)
  UNISWAP_V2_FACTORY: '0x8bceAA40B9AcDFAedf85AdF4fF01f5AD6517937f' as Address,
  UNISWAP_V2_ROUTER: '0x532161bD7b0Cfa3a72D39602C0ea84f449d749b8' as Address, // SmartRouter handles both
} as const;

// ABIs for event detection — minimal, only what we need
export const UNISWAP_V3_FACTORY_ABI = [
  {
    type: 'event',
    name: 'PoolCreated',
    inputs: [
      { name: 'token0', type: 'address', indexed: true },
      { name: 'token1', type: 'address', indexed: true },
      { name: 'fee', type: 'uint24', indexed: true },
      { name: 'tickSpacing', type: 'int24', indexed: false },
      { name: 'pool', type: 'address', indexed: false },
    ],
  },
  {
    type: 'function',
    name: 'feeAmountTickSpacing',
    inputs: [{ name: 'fee', type: 'uint24' }],
    outputs: [{ name: '', type: 'int24' }],
    stateMutability: 'view',
  },
] as const;

export const UNISWAP_V2_FACTORY_ABI = [
  {
    type: 'event',
    name: 'PairCreated',
    inputs: [
      { name: 'token0', type: 'address', indexed: true },
      { name: 'token1', type: 'address', indexed: true },
      { name: 'pair', type: 'address', indexed: false },
      { name: 'allPairsLength', type: 'uint256', indexed: false },
    ],
  },
  {
    type: 'function',
    name: 'allPairsLength',
    inputs: [],
    outputs: [{ name: '', type: 'uint256' }],
    stateMutability: 'view',
  },
] as const;

export const ERC20_ABI = [
  {
    type: 'function',
    name: 'name',
    inputs: [],
    outputs: [{ name: '', type: 'string' }],
    stateMutability: 'view',
  },
  {
    type: 'function',
    name: 'symbol',
    inputs: [],
    outputs: [{ name: '', type: 'string' }],
    stateMutability: 'view',
  },
  {
    type: 'function',
    name: 'decimals',
    inputs: [],
    outputs: [{ name: '', type: 'uint8' }],
    stateMutability: 'view',
  },
  {
    type: 'function',
    name: 'totalSupply',
    inputs: [],
    outputs: [{ name: '', type: 'uint256' }],
    stateMutability: 'view',
  },
  {
    type: 'function',
    name: 'balanceOf',
    inputs: [{ name: 'account', type: 'address' }],
    outputs: [{ name: '', type: 'uint256' }],
    stateMutability: 'view',
  },
] as const;

export const POOL_SWAP_ABI = [
  {
    type: 'event',
    name: 'Swap',
    inputs: [
      { name: 'sender', type: 'address', indexed: true },
      { name: 'recipient', type: 'address', indexed: true },
      { name: 'amount0', type: 'int256', indexed: false },
      { name: 'amount1', type: 'int256', indexed: false },
      { name: 'sqrtPriceX96', type: 'uint160', indexed: false },
      { name: 'liquidity', type: 'uint128', indexed: false },
      { name: 'tick', type: 'int24', indexed: false },
    ],
  },
] as const;

export const POOL_MINT_ABI = [
  {
    type: 'event',
    name: 'Mint',
    inputs: [
      { name: 'sender', type: 'address', indexed: false },
      { name: 'owner', type: 'address', indexed: true },
      { name: 'tickLower', type: 'int24', indexed: true },
      { name: 'tickUpper', type: 'int24', indexed: true },
      { name: 'amount', type: 'uint128', indexed: false },
      { name: 'amount0', type: 'uint256', indexed: false },
      { name: 'amount1', type: 'uint256', indexed: false },
    ],
  },
] as const;
