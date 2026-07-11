import { type Address } from 'viem';
import { httpClient, createWsClient } from '../core/client.js';
import { bus } from '../core/eventBus.js';
import { db, getState, setState } from '../core/db.js';
import { createModuleLogger } from '../core/logger.js';
import { contracts, ERC20_ABI, UNISWAP_V3_FACTORY_ABI, UNISWAP_V2_FACTORY_ABI } from '../config/contracts.js';

const log = createModuleLogger('newPoolListener');

const POOL_CREATED_EVENT = UNISWAP_V3_FACTORY_ABI[0]; // PoolCreated event
const PAIR_CREATED_EVENT = UNISWAP_V2_FACTORY_ABI[0]; // PairCreated event

const STATE_KEY_V3 = 'lastBlock:newPool:v3';
const STATE_KEY_V3_ALT = 'lastBlock:newPool:v3alt';
const STATE_KEY_V2 = 'lastBlock:newPool:v2';

async function fetchTokenMetadata(tokenAddress: Address) {
  try {
    const [name, symbol, decimals, totalSupply] = await Promise.all([
      httpClient.readContract({ address: tokenAddress, abi: ERC20_ABI, functionName: 'name' }),
      httpClient.readContract({ address: tokenAddress, abi: ERC20_ABI, functionName: 'symbol' }),
      httpClient.readContract({ address: tokenAddress, abi: ERC20_ABI, functionName: 'decimals' }),
      httpClient.readContract({ address: tokenAddress, abi: ERC20_ABI, functionName: 'totalSupply' }),
    ]);
    return { name, symbol, decimals, totalSupply: totalSupply.toString() };
  } catch (err) {
    log.warn({ tokenAddress, err }, 'Failed to fetch token metadata');
    return null;
  }
}

const QUOTE_TOKENS = new Set([
  contracts.WETH.toLowerCase(),
  contracts.VIRTUAL.toLowerCase(),
]);

function isQuoteToken(address: Address): boolean {
  return QUOTE_TOKENS.has(address.toLowerCase());
}

function identifyNewToken(token0: Address, token1: Address): { tokenAddress: Address; quoteToken: Address } | null {
  if (isQuoteToken(token0)) return { tokenAddress: token1, quoteToken: token0 };
  if (isQuoteToken(token1)) return { tokenAddress: token0, quoteToken: token1 };
  // Neither is WETH — skip (could be token/token pair)
  return null;
}

async function handlePoolCreated(
  poolAddress: Address,
  token0: Address,
  token1: Address,
  fee: number,
  amm: 'univ3' | 'univ2',
  blockNumber: number
) {
  const pair = identifyNewToken(token0, token1);
  if (!pair) {
    log.debug({ poolAddress, token0, token1 }, 'Skipping non-WETH pair');
    return;
  }

  const { tokenAddress, quoteToken } = pair;

  // Check if we already know this token
  const existing = db.prepare('SELECT address FROM tokens WHERE address = ?').get(tokenAddress);
  if (existing) {
    log.debug({ tokenAddress }, 'Token already tracked');
    // Still record the pool
    db.prepare(
      'INSERT OR IGNORE INTO pools (address, token_address, quote_token, amm, fee_tier, created_block) VALUES (?, ?, ?, ?, ?, ?)'
    ).run(poolAddress, tokenAddress, quoteToken, amm, fee, blockNumber);
    return;
  }

  // Fetch metadata
  const meta = await fetchTokenMetadata(tokenAddress);
  if (!meta) {
    log.warn({ tokenAddress }, 'Cannot fetch metadata, inserting with nulls');
  }

  // Get deployer from contract creation (we'll enrich later via Blockscout)
  const now = Math.floor(Date.now() / 1000);

  db.prepare(
    `INSERT OR IGNORE INTO tokens (address, name, symbol, decimals, total_supply, created_block, created_at, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, 'new')`
  ).run(
    tokenAddress,
    meta?.name ?? null,
    meta?.symbol ?? null,
    meta?.decimals ?? null,
    meta?.totalSupply ?? null,
    blockNumber,
    now
  );

  db.prepare(
    'INSERT OR IGNORE INTO pools (address, token_address, quote_token, amm, fee_tier, created_block) VALUES (?, ?, ?, ?, ?, ?)'
  ).run(poolAddress, tokenAddress, quoteToken, amm, fee, blockNumber);

  db.prepare(
    `INSERT INTO events (ts, module, token_address, kind, payload) VALUES (?, 'ingestion', ?, 'pool_created', ?)`
  ).run(now, tokenAddress, JSON.stringify({ poolAddress, amm, fee, block: blockNumber }));

  log.info({ tokenAddress, symbol: meta?.symbol, poolAddress, amm }, 'New pool detected');

  bus.emit('pool:created', {
    poolAddress,
    token0,
    token1,
    fee,
    block: blockNumber,
  });
}

async function backfillFromBlock(fromBlock: bigint, factoryAddress: Address, amm: 'univ3' | 'univ2', stateKey: string) {
  const currentBlock = await httpClient.getBlockNumber();
  if (fromBlock >= currentBlock) return;

  const chunkSize = 2000n;
  let start = fromBlock;

  while (start < currentBlock) {
    const end = start + chunkSize > currentBlock ? currentBlock : start + chunkSize;

    try {
      if (amm === 'univ3') {
        const logs = await httpClient.getLogs({
          address: factoryAddress,
          event: POOL_CREATED_EVENT,
          fromBlock: start,
          toBlock: end,
        });

        for (const l of logs) {
          await handlePoolCreated(
            l.args.pool!,
            l.args.token0!,
            l.args.token1!,
            Number(l.args.fee!),
            'univ3',
            Number(l.blockNumber)
          );
        }
      } else {
        const logs = await httpClient.getLogs({
          address: factoryAddress,
          event: PAIR_CREATED_EVENT,
          fromBlock: start,
          toBlock: end,
        });

        for (const l of logs) {
          const args = l.args as any;
          await handlePoolCreated(
            args.pair!,
            args.token0!,
            args.token1!,
            0,
            'univ2',
            Number(l.blockNumber)
          );
        }
      }
    } catch (err) {
      log.error({ start: Number(start), end: Number(end), err }, 'Backfill chunk failed');
    }

    start = end + 1n;
    setState(stateKey, start.toString());
  }

  setState(stateKey, currentBlock.toString());
  log.info({ factory: factoryAddress, amm, fromBlock: Number(fromBlock), toBlock: Number(currentBlock) }, 'Backfill complete');
}

export async function startNewPoolListener() {
  log.info('Starting new pool listener');

  const currentBlock = await httpClient.getBlockNumber();

  // Backfill any missed blocks
  const lastV3 = getState(STATE_KEY_V3);
  const lastV3Alt = getState(STATE_KEY_V3_ALT);
  const lastV2 = getState(STATE_KEY_V2);

  // Only backfill recent blocks on first run (last 1000 blocks ~ 4 minutes)
  const defaultStart = currentBlock - 1000n;

  await Promise.all([
    backfillFromBlock(
      lastV3 ? BigInt(lastV3) : defaultStart,
      contracts.UNISWAP_V3_FACTORY,
      'univ3',
      STATE_KEY_V3
    ),
    backfillFromBlock(
      lastV3Alt ? BigInt(lastV3Alt) : defaultStart,
      contracts.UNISWAP_V3_FACTORY_ALT,
      'univ3',
      STATE_KEY_V3_ALT
    ),
    backfillFromBlock(
      lastV2 ? BigInt(lastV2) : defaultStart,
      contracts.UNISWAP_V2_FACTORY,
      'univ2',
      STATE_KEY_V2
    ),
  ]);

  // Live WS subscription for V3 factory
  const wsClient = createWsClient();

  const unwatchV3 = wsClient.watchEvent({
    address: contracts.UNISWAP_V3_FACTORY,
    event: POOL_CREATED_EVENT,
    onLogs: (logs) => {
      for (const l of logs) {
        handlePoolCreated(
          l.args.pool!,
          l.args.token0!,
          l.args.token1!,
          Number(l.args.fee!),
          'univ3',
          Number(l.blockNumber)
        ).catch(err => log.error({ err }, 'V3 pool handler error'));
        setState(STATE_KEY_V3, l.blockNumber!.toString());
      }
    },
    onError: (err) => {
      log.error({ err }, 'V3 factory WS error');
    },
  });

  // Live WS subscription for V3 alt factory
  const unwatchV3Alt = wsClient.watchEvent({
    address: contracts.UNISWAP_V3_FACTORY_ALT,
    event: POOL_CREATED_EVENT,
    onLogs: (logs) => {
      for (const l of logs) {
        handlePoolCreated(
          l.args.pool!,
          l.args.token0!,
          l.args.token1!,
          Number(l.args.fee!),
          'univ3',
          Number(l.blockNumber)
        ).catch(err => log.error({ err }, 'V3 alt pool handler error'));
        setState(STATE_KEY_V3_ALT, l.blockNumber!.toString());
      }
    },
    onError: (err) => {
      log.error({ err }, 'V3 alt factory WS error');
    },
  });

  // Live WS subscription for V2 factory
  const unwatchV2 = wsClient.watchEvent({
    address: contracts.UNISWAP_V2_FACTORY,
    event: PAIR_CREATED_EVENT,
    onLogs: (logs) => {
      for (const l of logs) {
        const args = l.args as any;
        handlePoolCreated(
          args.pair!,
          args.token0!,
          args.token1!,
          0,
          'univ2',
          Number(l.blockNumber)
        ).catch(err => log.error({ err }, 'V2 pair handler error'));
        setState(STATE_KEY_V2, l.blockNumber!.toString());
      }
    },
    onError: (err) => {
      log.error({ err }, 'V2 factory WS error');
    },
  });

  // Heartbeat: check if we're still receiving blocks
  let lastSeenBlock = currentBlock;
  const heartbeat = setInterval(async () => {
    try {
      const now = await httpClient.getBlockNumber();
      if (now === lastSeenBlock) {
        log.warn({ staleBlock: Number(now) }, 'No new blocks in 60s — possible WS disconnect');
      }
      lastSeenBlock = now;
    } catch (err) {
      log.error({ err }, 'Heartbeat check failed');
    }
  }, 60_000);

  log.info('New pool listener active (V3 + V3_ALT + V2)');

  return () => {
    unwatchV3();
    unwatchV3Alt();
    unwatchV2();
    clearInterval(heartbeat);
  };
}
