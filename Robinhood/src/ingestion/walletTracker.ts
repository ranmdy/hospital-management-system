import { type Address } from 'viem';
import { db } from '../core/db.js';
import { bus } from '../core/eventBus.js';
import { createModuleLogger } from '../core/logger.js';

const log = createModuleLogger('walletTracker');

const BLOCKSCOUT_API = 'https://robinhoodchain.blockscout.com/api/v2';
const POLL_INTERVAL_MS = 60_000; // Check every 60s
const RATE_LIMIT_DELAY_MS = 2000;

interface TrackedWallet {
  address: string;
  tags: string[];
}

function getTrackedWallets(): TrackedWallet[] {
  const rows = db.prepare(
    `SELECT address, tags FROM wallets
     WHERE tags LIKE '%smart%' OR tags LIKE '%sniper%'
     ORDER BY first_seen DESC
     LIMIT 100`
  ).all() as any[];

  return rows.map(r => ({
    address: r.address,
    tags: JSON.parse(r.tags || '[]'),
  }));
}

async function pollWalletActivity(wallet: TrackedWallet): Promise<void> {
  try {
    // Get recent transactions from Blockscout
    const lastCheckedKey = `wallet_last_checked:${wallet.address}`;
    const lastCheckedRow = db.prepare('SELECT value FROM system_state WHERE key = ?').get(lastCheckedKey) as any;
    const lastChecked = lastCheckedRow?.value || '0';

    const res = await fetch(
      `${BLOCKSCOUT_API}/addresses/${wallet.address}/transactions?limit=10`
    );

    if (!res.ok) {
      log.debug({ wallet: wallet.address, status: res.status }, 'Blockscout wallet tx fetch failed');
      return;
    }

    const data = await res.json() as any;
    const txs = data.items || [];

    for (const tx of txs) {
      const txTs = Math.floor(new Date(tx.timestamp).getTime() / 1000);
      if (txTs <= Number(lastChecked)) continue;

      // Check if this tx interacts with any of our tracked pools
      const toAddress = (tx.to?.hash || '').toLowerCase();
      const pool = db.prepare(
        `SELECT p.address, p.token_address FROM pools p
         WHERE LOWER(p.address) = ?`
      ).get(toAddress) as any;

      if (pool) {
        // This wallet interacted with a tracked pool — check if it's a swap
        const swap = db.prepare(
          `SELECT is_buy, amount_weth FROM swaps
           WHERE tx_hash = ? AND wallet = ?`
        ).get(tx.hash, wallet.address) as any;

        if (swap) {
          const action = swap.is_buy ? 'buy' : 'sell';
          log.info({
            wallet: wallet.address,
            tags: wallet.tags,
            action,
            tokenAddress: pool.token_address,
            amountWeth: swap.amount_weth,
          }, 'Tracked wallet activity detected');

          bus.emit('wallet:activity', {
            wallet: wallet.address as Address,
            tokenAddress: pool.token_address as Address,
            action,
            amountWeth: BigInt(swap.amount_weth),
            block: tx.block_number,
          });
        }
      }
    }

    // Update last checked timestamp
    const now = Math.floor(Date.now() / 1000);
    db.prepare('INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)')
      .run(lastCheckedKey, now.toString());
  } catch (err) {
    log.error({ wallet: wallet.address, err }, 'Wallet activity poll error');
  }
}

export async function startWalletTracker() {
  log.info('Starting wallet tracker');

  const poll = async () => {
    const wallets = getTrackedWallets();
    if (wallets.length === 0) return;

    log.debug({ walletCount: wallets.length }, 'Polling tracked wallets');

    for (const wallet of wallets) {
      await pollWalletActivity(wallet);
      await new Promise(r => setTimeout(r, RATE_LIMIT_DELAY_MS));
    }
  };

  // Run immediately then on interval
  await poll();
  const interval = setInterval(poll, POLL_INTERVAL_MS);

  log.info('Wallet tracker active');
  return () => clearInterval(interval);
}
