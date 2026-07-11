import { createModuleLogger } from '../core/logger.js';
import { sendAlert } from '../core/telegram.js';
import { getAllPositions } from './positionManager.js';
import { startEntryEngine } from './entryEngine.js';
import { startExitEngine } from './exitEngine.js';

const log = createModuleLogger('paperTrader');

function formatPaperSummary(): string {
  const all = getAllPositions('paper');
  const closed = all.filter(p => p.status === 'closed');
  const open = all.filter(p => p.status !== 'closed');

  if (all.length === 0) return 'No paper trades yet.';

  const totalPnl = closed.reduce((sum, p) => sum + parseFloat(p.realized_pnl_eth || '0'), 0);
  const wins = closed.filter(p => parseFloat(p.realized_pnl_eth || '0') > 0).length;
  const winRate = closed.length > 0 ? ((wins / closed.length) * 100).toFixed(0) : 'N/A';

  return [
    `<b>Paper Trading Summary</b>`,
    `Open positions: ${open.length}`,
    `Closed trades: ${closed.length}`,
    `Win rate: ${winRate}%`,
    `Total PnL: ${totalPnl >= 0 ? '+' : ''}${totalPnl.toFixed(2)} VIRTUAL`,
  ].join('\n');
}

export async function startPaperTrader(): Promise<() => void> {
  log.info('Starting paper trader');

  startEntryEngine();
  const stopExitEngine = startExitEngine();

  await sendAlert(formatPaperSummary());

  log.info('Paper trader active');

  return () => {
    stopExitEngine();
    log.info('Paper trader stopped');
  };
}
