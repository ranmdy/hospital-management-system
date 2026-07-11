/**
 * Paper trading performance report.
 * Run: npx tsx scripts/paper-report.ts
 */
import { db } from '../src/core/db.js';

interface PositionRow {
  id: number;
  token_address: string;
  entry_ts: number;
  entry_price: string;
  size_eth: string;
  tokens_held: string;
  status: string;
  realized_pnl_eth: string;
  exit_log: string;
}

function fmt(n: number, decimals = 2): string {
  return (n >= 0 ? '+' : '') + n.toFixed(decimals);
}

function formatDuration(seconds: number): string {
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  return `${(seconds / 3600).toFixed(1)}h`;
}

async function main() {
  const all = db.prepare(
    `SELECT p.*, t.symbol FROM positions p
     LEFT JOIN tokens t ON t.address = p.token_address
     WHERE p.mode = 'paper'
     ORDER BY p.entry_ts DESC`
  ).all() as any[];

  if (all.length === 0) {
    console.log('No paper trades recorded yet.');
    return;
  }

  const open = all.filter(p => p.status !== 'closed');
  const closed = all.filter(p => p.status === 'closed');

  const totalPnl = closed.reduce((s, p) => s + parseFloat(p.realized_pnl_eth || '0'), 0);
  const wins = closed.filter(p => parseFloat(p.realized_pnl_eth || '0') > 0);
  const losses = closed.filter(p => parseFloat(p.realized_pnl_eth || '0') <= 0);
  const winRate = closed.length > 0 ? (wins.length / closed.length) * 100 : 0;
  const avgWin = wins.length > 0 ? wins.reduce((s, p) => s + parseFloat(p.realized_pnl_eth), 0) / wins.length : 0;
  const avgLoss = losses.length > 0 ? losses.reduce((s, p) => s + parseFloat(p.realized_pnl_eth), 0) / losses.length : 0;

  console.log('\n========================================');
  console.log('       HOODSCAN PAPER TRADING REPORT    ');
  console.log('========================================\n');

  console.log(`Total trades:   ${all.length} (${open.length} open, ${closed.length} closed)`);
  console.log(`Win rate:       ${winRate.toFixed(0)}% (${wins.length}W / ${losses.length}L)`);
  console.log(`Total PnL:      ${fmt(totalPnl)} VIRTUAL`);
  console.log(`Avg win:        ${fmt(avgWin)} VIRTUAL`);
  console.log(`Avg loss:       ${fmt(avgLoss)} VIRTUAL`);
  if (avgLoss !== 0) {
    console.log(`Profit factor:  ${(Math.abs(avgWin * wins.length) / Math.abs(avgLoss * losses.length)).toFixed(2)}`);
  }

  if (open.length > 0) {
    console.log('\n--- OPEN POSITIONS ---');
    for (const p of open) {
      const entryPrice = parseFloat(p.entry_price);
      const sizeVirtual = parseFloat(p.size_eth);
      const symbol = p.symbol || p.token_address.slice(0, 10);
      const age = formatDuration(Math.floor(Date.now() / 1000) - p.entry_ts);
      console.log(`  ${symbol.padEnd(12)} entry=${entryPrice.toExponential(3)} size=${sizeVirtual}V age=${age} status=${p.status}`);
    }
  }

  if (closed.length > 0) {
    console.log('\n--- CLOSED TRADES ---');
    for (const p of closed) {
      const pnl = parseFloat(p.realized_pnl_eth || '0');
      const pct = ((pnl / parseFloat(p.size_eth)) * 100);
      const exitLog = JSON.parse(p.exit_log || '[]');
      const exitReason = exitLog[exitLog.length - 1]?.reason || '?';
      const symbol = p.symbol || p.token_address.slice(0, 10);
      const duration = formatDuration(
        (exitLog[exitLog.length - 1]?.ts || p.entry_ts) - p.entry_ts
      );
      const pnlStr = fmt(pnl) + ' VIRTUAL';
      const marker = pnl > 0 ? '[W]' : '[L]';
      console.log(`  ${marker} ${symbol.padEnd(12)} ${pnlStr.padEnd(20)} ${fmt(pct, 0)}%  (${duration}, exit: ${exitReason})`);
    }
  }

  console.log('\n========================================\n');
}

main().catch(err => {
  console.error('Report error:', err);
  process.exit(1);
});
