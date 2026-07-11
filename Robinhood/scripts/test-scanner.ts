/**
 * Quick integration test: scans the last 500 blocks for new pools
 * to verify the scanner can detect and process them.
 * Run: npx tsx scripts/test-scanner.ts
 */
import { createPublicClient, http, type Address } from 'viem';
import { robinhoodMainnet } from '../src/config/chains.js';
import { contracts, UNISWAP_V3_FACTORY_ABI, UNISWAP_V2_FACTORY_ABI, ERC20_ABI } from '../src/config/contracts.js';

const client = createPublicClient({
  chain: robinhoodMainnet,
  transport: http('https://rpc.mainnet.chain.robinhood.com'),
});

async function main() {
  const currentBlock = await client.getBlockNumber();
  const fromBlock = currentBlock - 500n;

  console.log(`\nScanning blocks ${fromBlock} to ${currentBlock} for new pools...\n`);

  // Check V2 PairCreated events
  const v2Logs = await client.getLogs({
    address: contracts.UNISWAP_V2_FACTORY,
    event: UNISWAP_V2_FACTORY_ABI[0],
    fromBlock,
    toBlock: currentBlock,
  });

  console.log(`V2 PairCreated events: ${v2Logs.length}`);
  for (const l of v2Logs.slice(0, 3)) {
    const args = l.args as any;
    console.log(`  Block ${l.blockNumber}: pair=${args.pair}, token0=${args.token0}, token1=${args.token1}`);

    // Fetch token metadata for the non-WETH token
    const tokenAddr = args.token0.toLowerCase() === contracts.WETH.toLowerCase() ? args.token1 : args.token0;
    try {
      const [name, symbol] = await Promise.all([
        client.readContract({ address: tokenAddr, abi: ERC20_ABI, functionName: 'name' }),
        client.readContract({ address: tokenAddr, abi: ERC20_ABI, functionName: 'symbol' }),
      ]);
      console.log(`    Token: ${name} (${symbol})`);
    } catch {
      console.log(`    Token: <metadata fetch failed>`);
    }
  }

  // Check V3 PoolCreated events
  const v3Logs = await client.getLogs({
    address: contracts.UNISWAP_V3_FACTORY,
    event: UNISWAP_V3_FACTORY_ABI[0],
    fromBlock,
    toBlock: currentBlock,
  });

  console.log(`\nV3 PoolCreated events (factory 1): ${v3Logs.length}`);
  for (const l of v3Logs.slice(0, 3)) {
    console.log(`  Block ${l.blockNumber}: pool=${l.args.pool}, fee=${l.args.fee}`);
  }

  // Check alt V3 factory
  const v3AltLogs = await client.getLogs({
    address: contracts.UNISWAP_V3_FACTORY_ALT,
    event: UNISWAP_V3_FACTORY_ABI[0],
    fromBlock,
    toBlock: currentBlock,
  });

  console.log(`V3 PoolCreated events (factory 2): ${v3AltLogs.length}`);

  const total = v2Logs.length + v3Logs.length + v3AltLogs.length;
  console.log(`\nTotal new pools in last 500 blocks: ${total}`);
  console.log(`Average: ~${(total / 500 * 60 * 4).toFixed(1)} pools/hour (at 250ms blocks)`);
}

main().catch(console.error);
