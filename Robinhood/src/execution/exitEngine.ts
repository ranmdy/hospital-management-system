import { createModuleLogger } from '../core/logger.js';
import { EXECUTION_PARAMS } from '../config/params.js';
import {
  getOpenPositions,
  getCurrentPrice,
  getPeakPrice,
  closePartial,
  closePosition,
} from './positionManager.js';

const log = createModuleLogger('exitEngine');

const POLL_INTERVAL_MS = 30_000;

function checkExits(position: any): void {
  const tokenAddress: string = position.token_address;
  const currentPrice = getCurrentPrice(tokenAddress);

  if (currentPrice === null) {
    log.debug({ tokenAddress }, 'No current price for exit check');
    return;
  }

  const entryPrice = parseFloat(position.entry_price);
  const entryTs: number = position.entry_ts;
  const now = Math.floor(Date.now() / 1000);
  const exitLog = JSON.parse(position.exit_log || '[]') as { reason: string }[];

  // Track which ladder rungs already triggered
  const laddersTriggered = new Set<number>(
    exitLog
      .filter(e => e.reason.startsWith('ladder:'))
      .map(e => parseInt(e.reason.replace('ladder:', ''), 10))
  );

  const profitPct = ((currentPrice - entryPrice) / entryPrice) * 100;

  // Exit ladder (highest rung first to avoid double-triggering)
  for (const rung of [...EXECUTION_PARAMS.EXIT_LADDER].reverse()) {
    if (profitPct >= rung.profitPct && !laddersTriggered.has(rung.profitPct)) {
      log.info({ tokenAddress, profitPct: profitPct.toFixed(1), rung }, 'Exit ladder triggered');
      closePartial(position.id, rung.sellPct, currentPrice, `ladder:${rung.profitPct}`);
      return;
    }
  }

  // Hard stop loss
  if (profitPct <= -EXECUTION_PARAMS.HARD_STOP_LOSS_PCT) {
    log.info({ tokenAddress, profitPct: profitPct.toFixed(1) }, 'Hard stop loss triggered');
    closePosition(position.id, currentPrice, 'hard_stop');
    return;
  }

  // Trailing stop from peak price
  const peakPrice = getPeakPrice(tokenAddress, entryTs);
  if (peakPrice && peakPrice > entryPrice) {
    const drawdownFromPeak = ((peakPrice - currentPrice) / peakPrice) * 100;
    if (drawdownFromPeak >= EXECUTION_PARAMS.TRAILING_STOP_DRAWDOWN_PCT) {
      log.info({ tokenAddress, drawdownFromPeak: drawdownFromPeak.toFixed(1), peakPrice, currentPrice }, 'Trailing stop triggered');
      closePosition(position.id, currentPrice, 'trailing_stop');
      return;
    }
  }

  // Time stop: 24h
  const hoursHeld = (now - entryTs) / 3600;
  if (hoursHeld >= EXECUTION_PARAMS.TIME_STOP_HOURS) {
    log.info({ tokenAddress, hoursHeld: hoursHeld.toFixed(1) }, 'Time stop triggered');
    closePosition(position.id, currentPrice, 'time_stop');
    return;
  }

  log.debug({
    tokenAddress,
    profitPct: profitPct.toFixed(1),
    hoursHeld: hoursHeld.toFixed(1),
  }, 'Position holding');
}

export function startExitEngine(): () => void {
  log.info('Starting exit engine');

  const poll = () => {
    const positions = getOpenPositions();
    if (positions.length === 0) return;

    log.debug({ count: positions.length }, 'Checking exits for open positions');
    for (const pos of positions) {
      try {
        checkExits(pos);
      } catch (err) {
        log.error({ positionId: pos.id, err }, 'Exit check error');
      }
    }
  };

  const interval = setInterval(poll, POLL_INTERVAL_MS);
  // Run immediately
  poll();

  log.info('Exit engine active');
  return () => clearInterval(interval);
}
