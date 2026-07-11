import {
  createPublicClient,
  http,
  webSocket,
  fallback,
  type PublicClient,
  type Transport,
  type Chain,
} from 'viem';
import { env } from '../config/env.js';
import { robinhoodMainnet, robinhoodTestnet } from '../config/chains.js';
import { createModuleLogger } from './logger.js';

const log = createModuleLogger('client');

const chain: Chain = env.CHAIN === 'mainnet' ? robinhoodMainnet : robinhoodTestnet;

function buildHttpTransports(): Transport[] {
  const transports: Transport[] = [];

  if (env.CHAIN === 'mainnet') {
    transports.push(
      http(`https://robinhood-mainnet.g.alchemy.com/v2/${env.ALCHEMY_API_KEY}`, {
        retryCount: 3,
        retryDelay: 1000,
      })
    );

    if (env.QUICKNODE_URL) {
      transports.push(http(env.QUICKNODE_URL, { retryCount: 2 }));
    }

    // Public RPC as final fallback
    transports.push(http('https://rpc.mainnet.chain.robinhood.com', { retryCount: 1 }));
  } else {
    transports.push(http('https://rpc.testnet.chain.robinhood.com', { retryCount: 3 }));
  }

  return transports;
}

// HTTP client for reads (with fallback)
export const httpClient: PublicClient = createPublicClient({
  chain,
  transport: fallback(buildHttpTransports()),
  batch: { multicall: { batchSize: 1024 } },
});

// WebSocket client for subscriptions (auto-reconnect handled by viem)
export function createWsClient(): PublicClient {
  const wsUrl =
    env.CHAIN === 'mainnet'
      ? `wss://robinhood-mainnet.g.alchemy.com/v2/${env.ALCHEMY_API_KEY}`
      : 'wss://rpc.testnet.chain.robinhood.com';

  const client = createPublicClient({
    chain,
    transport: webSocket(wsUrl, {
      reconnect: { attempts: 50, delay: 1000 },
      keepAlive: { interval: 30_000 },
    }),
  });

  log.info({ wsUrl: wsUrl.replace(env.ALCHEMY_API_KEY, '***') }, 'WebSocket client created');
  return client;
}

export { chain };
