import { createPublicClient, http, getAddress, type Address } from 'viem';
import { robinhoodMainnet, robinhoodTestnet } from '../src/config/chains.js';
import { contracts, UNISWAP_V3_FACTORY_ABI, UNISWAP_V2_FACTORY_ABI } from '../src/config/contracts.js';
import { config } from 'dotenv';

config();

const CHAIN = (process.env.CHAIN || 'mainnet') as 'mainnet' | 'testnet';
const chain = CHAIN === 'mainnet' ? robinhoodMainnet : robinhoodTestnet;

const alchemyKey = process.env.ALCHEMY_API_KEY;
const hasAlchemy = alchemyKey && alchemyKey !== 'test_placeholder';

const rpcUrl = CHAIN === 'mainnet'
  ? hasAlchemy
    ? `https://robinhood-mainnet.g.alchemy.com/v2/${alchemyKey}`
    : 'https://rpc.mainnet.chain.robinhood.com'
  : 'https://rpc.testnet.chain.robinhood.com';

const client = createPublicClient({
  chain,
  transport: http(rpcUrl),
});

// Known Uniswap v3 canonical deployment addresses to check
const CANDIDATE_V3_FACTORIES: Address[] = [
  '0x1F98431c8aD98523631AE4a59f267346ea31F984', // Uniswap v3 canonical
  '0x33128a8fC17869897dcE68Ed026d694621f6FDfD', // Base deployment
  '0x0227628f3F023bb0B980b67D528571c95c6DaC1c', // Arbitrum One
];

const CANDIDATE_V2_FACTORIES: Address[] = [
  '0x5C69bEe701ef814a2B6a3EDD4B1652CB9cc5aA6f', // Uniswap v2 canonical
  '0xf1D7CC64Fb4452F05c498126312eBE29f30Fbcf9', // some Arbitrum v2 forks
];

async function checkBytecode(address: Address, label: string): Promise<boolean> {
  try {
    const code = await client.getCode({ address });
    const exists = !!code && code !== '0x';
    console.log(`  ${label} @ ${address}: ${exists ? 'EXISTS' : 'NO CODE'}`);
    return exists;
  } catch (err) {
    console.log(`  ${label} @ ${address}: ERROR - ${(err as Error).message}`);
    return false;
  }
}

async function checkV3Factory(address: Address): Promise<boolean> {
  try {
    const tickSpacing = await client.readContract({
      address,
      abi: UNISWAP_V3_FACTORY_ABI,
      functionName: 'feeAmountTickSpacing',
      args: [3000],
    });
    console.log(`  V3 Factory @ ${address}: VERIFIED (3000 fee => tick spacing ${tickSpacing})`);
    return true;
  } catch {
    return false;
  }
}

async function checkV2Factory(address: Address): Promise<boolean> {
  try {
    const pairsLength = await client.readContract({
      address,
      abi: UNISWAP_V2_FACTORY_ABI,
      functionName: 'allPairsLength',
    });
    console.log(`  V2 Factory @ ${address}: VERIFIED (${pairsLength} pairs)`);
    return true;
  } catch {
    return false;
  }
}

async function main() {
  console.log(`\n=== HoodScan Contract Discovery ===`);
  console.log(`Chain: ${CHAIN} (ID: ${chain.id})`);
  console.log(`RPC: ${rpcUrl.replace(process.env.ALCHEMY_API_KEY || '', '***')}\n`);

  // 1. Verify core contracts
  console.log('--- Core Contracts ---');
  await checkBytecode(contracts.WETH, 'WETH');
  await checkBytecode(contracts.MULTICALL, 'Multicall');
  await checkBytecode(contracts.PERMIT2, 'Permit2');

  // 2. Discover Uniswap v3 Factory
  console.log('\n--- Uniswap V3 Factory Discovery ---');
  let v3Found = false;
  for (const candidate of CANDIDATE_V3_FACTORIES) {
    const hasCode = await checkBytecode(candidate, 'V3 Factory candidate');
    if (hasCode) {
      const verified = await checkV3Factory(candidate);
      if (verified) {
        v3Found = true;
        console.log(`  => USE THIS: ${candidate}`);
        break;
      }
    }
  }
  if (!v3Found) {
    console.log('  WARNING: No Uniswap V3 Factory found among known addresses.');
    console.log('  Check Blockscout verified contracts or Uniswap deployment registry.');
  }

  // 3. Discover Uniswap v2 Factory
  console.log('\n--- Uniswap V2 Factory Discovery ---');
  let v2Found = false;
  for (const candidate of CANDIDATE_V2_FACTORIES) {
    const hasCode = await checkBytecode(candidate, 'V2 Factory candidate');
    if (hasCode) {
      const verified = await checkV2Factory(candidate);
      if (verified) {
        v2Found = true;
        console.log(`  => USE THIS: ${candidate}`);
        break;
      }
    }
  }
  if (!v2Found) {
    console.log('  WARNING: No Uniswap V2 Factory found among known addresses.');
    console.log('  May need to check Dexscreener pair pages for router addresses.');
  }

  // 4. Test chain connectivity
  console.log('\n--- Chain Connectivity ---');
  try {
    const blockNumber = await client.getBlockNumber();
    console.log(`  Current block: ${blockNumber}`);

    const block = await client.getBlock({ blockNumber });
    console.log(`  Block timestamp: ${new Date(Number(block.timestamp) * 1000).toISOString()}`);
    console.log(`  Block txs: ${block.transactions.length}`);
  } catch (err) {
    console.error(`  ERROR: ${(err as Error).message}`);
  }

  console.log('\n=== Discovery Complete ===\n');
  console.log('Next steps:');
  console.log('1. Update src/config/contracts.ts with discovered addresses');
  console.log('2. If V3/V2 factories not found, check:');
  console.log('   - https://robinhoodchain.blockscout.com/verified-contracts');
  console.log('   - Uniswap Labs deployment addresses for chain 4663');
  console.log('   - Dexscreener pair pages for router contract addresses');
}

main().catch(console.error);
