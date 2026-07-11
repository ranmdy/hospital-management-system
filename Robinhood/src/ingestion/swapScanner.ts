import { type Address, parseAbiItem } from 'viem';
import { httpClient } from '../core/client.js';
import { db, getState, setState } from '../core/db.js';
import { createModuleLogger } from '../core/logger.js';

const log = createModuleLogger('swapScanner');

const V3_SWAP_EVENT = parseAbiItem(
  'event Swap(address indexed sender, address indexed recipient, int256 amount0, int256 amount1, uint160 sqrtPriceX96, uint128 liquidity, int24 tick)'
);

const V2_SWAP_EVENT = parseAbiItem(
  'event Swap(address indexed sender, uint256 amount0In, uint256 amount1In, uint256 amount0Out, uint256 amount1Out, address indexed to)'
);

const STATE_KEY = 'lastBlock:swapScanner';
const SCAN_INTERVAL_BLOCKS = 5n;

interface PoolInfo {
  address: Address;
  tokenAddress: Address;
  quoteToken: Address;
  amm: string;
}

function getActivePools(): PoolInfo[] {
  return db.prepare(
    `SELECT p.address, p.token_address as tokenAddress, p.quote_token as quoteToken, p.amm
     FROM pools p
     JOIN tokens t ON t.address = p.token_address
     WHERE t.status IN ('watching', 'alerted', 'traded')
     AND p.launched_at IS NOT NULL`
  ).all() as PoolInfo[];
}

async function scanV3Swaps(pools: PoolInfo[], fromBlock: bigint, toBlock: bigint) {
  if (pools.length === 0) return;

  const poolAddresses = pools.map(p => p.address);

  try {
    const logs = await httpClient.getLogs({
      address: poolAddresses,
      event: V3_SWAP_EVENT,
      fromBlock,
      toBlock,
    });

    const insertStmt = db.prepare(
      `INSERT INTO swaps (pool_address, block, tx_hash, wallet, is_buy, amount_weth, amount_token, price, ts)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    );

    for (const l of logs) {
      const pool = pools.find(p => p.address.toLowerCase() === l.address.toLowerCase());
      if (!pool) continue;

      const quoteIsToken0 = pool.quoteToken.toLowerCase() < pool.tokenAddress.toLowerCase();

      // In V3, negative amount means tokens leaving the pool (going to user)
      // Positive amount means tokens entering the pool (from user)
      const amount0 = l.args.amount0!;
      const amount1 = l.args.amount1!;

      let isBuy: boolean;
      let amountWeth: bigint;
      let amountToken: bigint;

      if (quoteIsToken0) {
        // Quote is token0
        // Buy = quote goes in (amount0 > 0), token goes out (amount1 < 0)
        isBuy = amount0 > 0n;
        amountWeth = amount0 > 0n ? amount0 : -amount0;
        amountToken = amount1 > 0n ? amount1 : -amount1;
      } else {
        // Quote is token1
        // Buy = quote goes in (amount1 > 0), token goes out (amount0 < 0)
        isBuy = amount1 > 0n;
        amountWeth = amount1 > 0n ? amount1 : -amount1;
        amountToken = amount0 > 0n ? amount0 : -amount0;
      }

      // Price in WETH per token
      const price = amountToken > 0n
        ? (Number(amountWeth) / Number(amountToken)).toString()
        : '0';

      const block = await httpClient.getBlock({ blockNumber: l.blockNumber! });
      const ts = Number(block.timestamp);

      insertStmt.run(
        pool.address,
        Number(l.blockNumber),
        l.transactionHash,
        l.args.recipient, // recipient is usually the buyer/seller
        isBuy ? 1 : 0,
        amountWeth.toString(),
        amountToken.toString(),
        price,
        ts
      );
    }

    if (logs.length > 0) {
      log.debug({ count: logs.length, fromBlock: Number(fromBlock), toBlock: Number(toBlock) }, 'V3 swaps recorded');
    }
  } catch (err) {
    log.error({ err, fromBlock: Number(fromBlock), toBlock: Number(toBlock) }, 'V3 swap scan error');
  }
}

async function scanV2Swaps(pools: PoolInfo[], fromBlock: bigint, toBlock: bigint) {
  if (pools.length === 0) return;

  const poolAddresses = pools.map(p => p.address);

  try {
    const logs = await httpClient.getLogs({
      address: poolAddresses,
      event: V2_SWAP_EVENT,
      fromBlock,
      toBlock,
    });

    const insertStmt = db.prepare(
      `INSERT INTO swaps (pool_address, block, tx_hash, wallet, is_buy, amount_weth, amount_token, price, ts)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
    );

    for (const l of logs) {
      const pool = pools.find(p => p.address.toLowerCase() === l.address.toLowerCase());
      if (!pool) continue;

      const quoteIsToken0 = pool.quoteToken.toLowerCase() < pool.tokenAddress.toLowerCase();

      const amount0In = l.args.amount0In!;
      const amount1In = l.args.amount1In!;
      const amount0Out = l.args.amount0Out!;
      const amount1Out = l.args.amount1Out!;

      let isBuy: boolean;
      let amountWeth: bigint;
      let amountToken: bigint;

      if (quoteIsToken0) {
        // Buy: quote in, token out
        isBuy = amount0In > 0n && amount1Out > 0n;
        amountWeth = isBuy ? amount0In : amount1In > 0n ? amount0Out : 0n;
        amountToken = isBuy ? amount1Out : amount1In;
      } else {
        // Buy: quote in (token1), token out (token0)
        isBuy = amount1In > 0n && amount0Out > 0n;
        amountWeth = isBuy ? amount1In : amount0In > 0n ? amount1Out : 0n;
        amountToken = isBuy ? amount0Out : amount0In;
      }

      if (amountWeth === 0n) continue;

      const price = amountToken > 0n
        ? (Number(amountWeth) / Number(amountToken)).toString()
        : '0';

      const block = await httpClient.getBlock({ blockNumber: l.blockNumber! });
      const ts = Number(block.timestamp);

      insertStmt.run(
        pool.address,
        Number(l.blockNumber),
        l.transactionHash,
        l.args.to, // 'to' is the swap recipient
        isBuy ? 1 : 0,
        amountWeth.toString(),
        amountToken.toString(),
        price,
        ts
      );
    }

    if (logs.length > 0) {
      log.debug({ count: logs.length, fromBlock: Number(fromBlock), toBlock: Number(toBlock) }, 'V2 swaps recorded');
    }
  } catch (err) {
    log.error({ err, fromBlock: Number(fromBlock), toBlock: Number(toBlock) }, 'V2 swap scan error');
  }
}

export async function startSwapScanner() {
  log.info('Starting swap scanner');

  const currentBlock = await httpClient.getBlockNumber();
  const lastScanned = getState(STATE_KEY);
  let fromBlock = lastScanned ? BigInt(lastScanned) : currentBlock - 10n;

  const interval = setInterval(async () => {
    try {
      const currentBlock = await httpClient.getBlockNumber();
      if (currentBlock <= fromBlock) return;

      const pools = getActivePools();
      if (pools.length === 0) {
        fromBlock = currentBlock;
        setState(STATE_KEY, currentBlock.toString());
        return;
      }

      const v3Pools = pools.filter(p => p.amm === 'univ3');
      const v2Pools = pools.filter(p => p.amm === 'univ2');

      await Promise.all([
        scanV3Swaps(v3Pools, fromBlock + 1n, currentBlock),
        scanV2Swaps(v2Pools, fromBlock + 1n, currentBlock),
      ]);

      fromBlock = currentBlock;
      setState(STATE_KEY, currentBlock.toString());
    } catch (err) {
      log.error({ err }, 'Swap scanner tick error');
    }
  }, 1500); // Scan every ~5 blocks at 250ms block time

  log.info('Swap scanner active');

  return () => {
    clearInterval(interval);
  };
}
