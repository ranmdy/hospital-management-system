import { env } from './config/env.js';
import { runMigrations } from './core/db.js';
import { createModuleLogger } from './core/logger.js';
import { initTelegram, sendAlert } from './core/telegram.js';
import { httpClient } from './core/client.js';
import { startNewPoolListener } from './ingestion/newPoolListener.js';
import { startLiquidityListener } from './ingestion/liquidityListener.js';
import { startSwapScanner } from './ingestion/swapScanner.js';
import { startHolderScanner } from './ingestion/holderScanner.js';
import { startDexscreenerEnricher } from './ingestion/dexscreenerEnricher.js';
import { startWalletTracker } from './ingestion/walletTracker.js';
import { startAlertController } from './ingestion/alertController.js';
import { startSniperDetector } from './scoring/sniperDetector.js';
import { startPaperTrader } from './execution/paperTrader.js';

const log = createModuleLogger('main');

async function main() {
  log.info({ mode: env.MODE, chain: env.CHAIN }, 'HoodScan starting');

  // Apply database migrations
  runMigrations();

  // Initialize Telegram bot
  initTelegram();

  // Verify chain connectivity
  try {
    const blockNumber = await httpClient.getBlockNumber();
    log.info({ blockNumber: Number(blockNumber) }, 'Connected to chain');
  } catch (err) {
    log.fatal({ err }, 'Failed to connect to chain');
    process.exit(1);
  }

  // Live mode warning banner
  if (env.MODE === 'live') {
    const { privateKeyToAccount } = await import('viem/accounts');
    const account = privateKeyToAccount(env.HOT_WALLET_PRIVATE_KEY as `0x${string}`);
    log.warn('========================================');
    log.warn('    LIVE TRADING MODE ACTIVE');
    log.warn(`    Wallet: ${account.address}`);
    log.warn('========================================');
  }

  await sendAlert(
    `<b>HoodScan started</b>\nMode: ${env.MODE}\nChain: ${env.CHAIN}`
  );

  // Start scanner components (all modes need these)
  const cleanups: (() => void)[] = [];

  // Alert controller must be started before listeners (subscribes to events)
  await startAlertController();

  // Start ingestion
  cleanups.push(await startNewPoolListener());
  cleanups.push(await startLiquidityListener());
  cleanups.push(await startSwapScanner());
  cleanups.push(await startHolderScanner());
  cleanups.push(await startDexscreenerEnricher());
  cleanups.push(await startWalletTracker());

  // Start scoring modules
  startSniperDetector();

  log.info('All ingestion modules active');

  // Mode-specific startup
  if (env.MODE === 'scanner') {
    log.info('Running in scanner mode (detection + alerts only)');
  } else if (env.MODE === 'paper') {
    log.info('Running in paper trading mode');
    cleanups.push(await startPaperTrader());
  } else if (env.MODE === 'live') {
    log.info('Running in live trading mode');
    // Phase 4: live execution engine
  }

  // Global error handlers
  process.on('unhandledRejection', (reason) => {
    log.error({ reason }, 'Unhandled rejection');
    sendAlert(`<b>UNHANDLED REJECTION</b>\n<code>${String(reason)}</code>`).catch(() => {});
  });

  process.on('uncaughtException', (err) => {
    log.fatal({ err }, 'Uncaught exception');
    sendAlert(`<b>UNCAUGHT EXCEPTION</b>\n<code>${err.message}</code>`)
      .catch(() => {})
      .finally(() => process.exit(1));
  });

  // Graceful shutdown
  const shutdown = async () => {
    log.info('Shutting down...');
    for (const cleanup of cleanups) {
      try { cleanup(); } catch {}
    }
    await sendAlert('<b>HoodScan shutting down</b>');
    process.exit(0);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  log.info('HoodScan ready');
}

main().catch((err) => {
  console.error('Fatal startup error:', err);
  process.exit(1);
});
