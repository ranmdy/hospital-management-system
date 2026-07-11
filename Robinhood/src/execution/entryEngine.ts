import { db } from '../core/db.js';
import { bus } from '../core/eventBus.js';
import { createModuleLogger } from '../core/logger.js';
import { openPosition, getCurrentPrice } from './positionManager.js';
import { THRESHOLDS } from '../config/params.js';

const log = createModuleLogger('entryEngine');

export function startEntryEngine(): void {
  log.info('Starting entry engine');

  bus.on('token:alerted', (data) => {
    const { tokenAddress, score, safetyStatus } = data;

    if (score < THRESHOLDS.TRADE_THRESHOLD) {
      log.debug({ tokenAddress, score, threshold: THRESHOLDS.TRADE_THRESHOLD }, 'Score below trade threshold');
      return;
    }

    if (safetyStatus === 'VETO') {
      log.debug({ tokenAddress }, 'Safety VETO, skipping entry');
      return;
    }

    const price = getCurrentPrice(tokenAddress);
    if (price === null || price <= 0) {
      log.debug({ tokenAddress }, 'No price available for entry');
      return;
    }

    const position = openPosition(tokenAddress, price, 'paper');
    if (!position) return;

    db.prepare(
      `INSERT INTO events (ts, module, token_address, kind, payload) VALUES (?, ?, ?, ?, ?)`
    ).run(
      Math.floor(Date.now() / 1000), 'entryEngine', tokenAddress,
      'paper:entry',
      JSON.stringify({ price, score, safetyStatus })
    );
  });

  log.info('Entry engine active');
}
