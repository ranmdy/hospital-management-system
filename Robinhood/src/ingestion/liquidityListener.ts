import { type Address, parseAbiItem, formatEther } from 'viem';
import { httpClient, createWsClient } from '../core/client.js';
import { bus } from '../core/eventBus.js';
import { db, setState } from '../core/db.js';
import { createModuleLogger } from '../core/logger.js';
import { contracts } from '../config/contracts.js';

const log = createModuleLogger('liquidityListener');

// V3 Mint event (liquidity added to pool)
const V3_MINT_EVENT = parseAbiItem(
  'event Mint(address sender, address indexed owner, int24 indexed tickLower, int24 indexed tickUpper, uint128 amount, uint256 amount0, uint256 amount1)'
);

// V2 Mint event (liquidity added)
const V2_MINT_EVENT = parseAbiItem(
  'event Mint(address indexed sender, uint256 amount0, uint256 amount1)'
);

// V2 Sync event to get reserves
const V2_SYNC_EVENT = parseAbiItem(
  'event Sync(uint112 reserve0, uint112 reserve1)'
);

// Track pools we're watching (pool address -> token address)
const watchedPools = new Map<string, { tokenAddress: Address; quoteToken: Address; amm: string; createdAt: number }>();

// Maximum age to watch a pool (24h)
const MAX_WATCH_DURATION_MS = 24 * 60 * 60 * 1000;

function estimateWethLiquidity(
  amount0: bigint,
  amount1: bigint,
  quoteToken: Address,
  token0IsWeth: boolean
): bigint {
  // Figure out which amount is WETH
  if (token0IsWeth) {
    return amount0;
  }
  return amount1;
}

async function handleLiquidityAdd(
  poolAddress: Address,
  txHash: string,
  blockNumber: number,
  wethAmount: bigint,
  lpProvider: Address
) {
  const poolInfo = watchedPools.get(poolAddress.toLowerCase());
  if (!poolInfo) return;

  const { tokenAddress } = poolInfo;

  // Check if this pool already launched (has initial_liquidity_weth set)
  const pool = db.prepare('SELECT launched_at FROM pools WHERE address = ?').get(poolAddress) as any;
  if (pool?.launched_at) {
    return;
  }

  // Remove from watch list immediately to prevent duplicate processing
  watchedPools.delete(poolAddress.toLowerCase());

  const now = Math.floor(Date.now() / 1000);

  // Update pool record
  db.prepare(
    `UPDATE pools SET launched_at = ?, initial_liquidity_weth = ?, lp_provider = ? WHERE address = ?`
  ).run(now, wethAmount.toString(), lpProvider, poolAddress);

  // Update token status
  db.prepare(`UPDATE tokens SET status = 'watching' WHERE address = ? AND status = 'new'`).run(tokenAddress);

  db.prepare(
    `INSERT INTO events (ts, module, token_address, kind, payload) VALUES (?, 'ingestion', ?, 'pool_launched', ?)`
  ).run(now, tokenAddress, JSON.stringify({
    poolAddress,
    liquidityWeth: wethAmount.toString(),
    liquidityEth: formatEther(wethAmount),
    lpProvider,
    block: blockNumber,
    txHash,
  }));

  log.info({
    tokenAddress,
    poolAddress,
    liquidityEth: formatEther(wethAmount),
    lpProvider,
  }, 'Pool launched — liquidity added');

  bus.emit('pool:launched', {
    poolAddress,
    tokenAddress,
    liquidityWeth: wethAmount,
    lpProvider,
    block: blockNumber,
  });
}

function addPoolToWatch(poolAddress: Address, tokenAddress: Address, quoteToken: Address, amm: string) {
  watchedPools.set(poolAddress.toLowerCase(), {
    tokenAddress,
    quoteToken,
    amm,
    createdAt: Date.now(),
  });
  log.debug({ poolAddress, tokenAddress }, 'Watching pool for liquidity');
}

export async function startLiquidityListener() {
  log.info('Starting liquidity listener');

  // Listen for new pools from the event bus
  const quoteTokens = new Set([contracts.WETH.toLowerCase(), contracts.VIRTUAL.toLowerCase()]);

  bus.on('pool:created', (data) => {
    // Determine which token is the new one (quote token is WETH or VIRTUAL)
    const token0Lower = data.token0.toLowerCase();
    const token1Lower = data.token1.toLowerCase();

    let tokenAddress: Address;
    let quoteToken: Address;
    if (quoteTokens.has(token0Lower)) {
      tokenAddress = data.token1;
      quoteToken = data.token0;
    } else if (quoteTokens.has(token1Lower)) {
      tokenAddress = data.token0;
      quoteToken = data.token1;
    } else {
      return; // Neither is a known quote token
    }

    addPoolToWatch(data.poolAddress, tokenAddress, quoteToken, data.fee > 0 ? 'univ3' : 'univ2');
  });

  // Also load existing unwatched pools from DB
  const unwatchedPools = db.prepare(
    `SELECT p.address, p.token_address, p.quote_token, p.amm
     FROM pools p
     WHERE p.launched_at IS NULL
     AND p.created_block > (SELECT COALESCE(MAX(created_block), 0) FROM pools) - 50000`
  ).all() as any[];

  for (const p of unwatchedPools) {
    addPoolToWatch(p.address, p.token_address, p.quote_token, p.amm);
  }

  log.info({ count: watchedPools.size }, 'Loaded existing unwatched pools');

  // Periodic scan for liquidity events on watched pools
  const scanInterval = setInterval(async () => {
    if (watchedPools.size === 0) return;

    // Remove pools older than 24h
    const now = Date.now();
    for (const [addr, info] of watchedPools) {
      if (now - info.createdAt > MAX_WATCH_DURATION_MS) {
        watchedPools.delete(addr);
        log.debug({ poolAddress: addr }, 'Stopped watching pool (24h timeout)');
      }
    }

    // Get pools grouped by type for efficient querying
    const v3Pools: Address[] = [];
    const v2Pools: Address[] = [];

    for (const [addr, info] of watchedPools) {
      if (info.amm === 'univ3') v3Pools.push(addr as Address);
      else v2Pools.push(addr as Address);
    }

    const currentBlock = await httpClient.getBlockNumber();
    const fromBlock = currentBlock - 50n; // Last ~12 seconds

    // Check V3 pools for Mint events
    if (v3Pools.length > 0) {
      try {
        const logs = await httpClient.getLogs({
          address: v3Pools,
          event: V3_MINT_EVENT,
          fromBlock,
          toBlock: currentBlock,
        });

        for (const l of logs) {
          const poolAddr = l.address as Address;
          const poolInfo = watchedPools.get(poolAddr.toLowerCase());
          if (!poolInfo) continue;

          // In V3, token0 < token1 by address. Figure out which side is the quote token.
          const quoteIsToken0 = poolInfo.quoteToken.toLowerCase() < poolInfo.tokenAddress.toLowerCase();
          const quoteAmount = quoteIsToken0 ? l.args.amount0! : l.args.amount1!;

          if (quoteAmount > 0n) {
            // Get tx to find the LP provider
            const tx = await httpClient.getTransaction({ hash: l.transactionHash! });
            await handleLiquidityAdd(poolAddr, l.transactionHash!, Number(l.blockNumber), quoteAmount, tx.from);
          }
        }
      } catch (err) {
        log.error({ err }, 'V3 liquidity scan error');
      }
    }

    // Check V2 pools for Mint events
    if (v2Pools.length > 0) {
      try {
        const logs = await httpClient.getLogs({
          address: v2Pools,
          event: V2_SYNC_EVENT,
          fromBlock,
          toBlock: currentBlock,
        });

        for (const l of logs) {
          const poolAddr = l.address as Address;
          const poolInfo = watchedPools.get(poolAddr.toLowerCase());
          if (!poolInfo) continue;

          const quoteIsToken0 = poolInfo.quoteToken.toLowerCase() < poolInfo.tokenAddress.toLowerCase();
          const quoteReserve = quoteIsToken0 ? l.args.reserve0! : l.args.reserve1!;

          if (quoteReserve > 0n) {
            const tx = await httpClient.getTransaction({ hash: l.transactionHash! });
            await handleLiquidityAdd(poolAddr, l.transactionHash!, Number(l.blockNumber), BigInt(quoteReserve), tx.from);
          }
        }
      } catch (err) {
        log.error({ err }, 'V2 liquidity scan error');
      }
    }
  }, 3000); // Every 3 seconds

  log.info('Liquidity listener active');

  return () => {
    clearInterval(scanInterval);
    watchedPools.clear();
  };
}
