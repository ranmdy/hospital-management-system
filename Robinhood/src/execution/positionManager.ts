import { type Address } from 'viem';
import { db } from '../core/db.js';
import { bus } from '../core/eventBus.js';
import { createModuleLogger } from '../core/logger.js';
import { EXECUTION_PARAMS } from '../config/params.js';

const log = createModuleLogger('positionManager');

export interface ExitEvent {
  ts: number;
  reason: string;
  price: number;
  tokensSold: number;
  virtualReturned: number;
}

export interface Position {
  id: number;
  tokenAddress: string;
  mode: 'paper' | 'live';
  entryTs: number;
  entryPrice: number;
  sizeVirtual: number;
  tokensHeld: number;
  status: 'open' | 'partial' | 'closed';
  realizedPnl: number;
  exitLog: ExitEvent[];
}

function rowToPosition(row: any): Position {
  return {
    id: row.id,
    tokenAddress: row.token_address,
    mode: row.mode,
    entryTs: row.entry_ts,
    entryPrice: parseFloat(row.entry_price),
    sizeVirtual: parseFloat(row.size_eth),
    tokensHeld: parseFloat(row.tokens_held),
    status: row.status,
    realizedPnl: parseFloat(row.realized_pnl_eth || '0'),
    exitLog: JSON.parse(row.exit_log || '[]'),
  };
}

export function openPosition(
  tokenAddress: string,
  entryPrice: number,
  mode: 'paper' | 'live' = 'paper'
): Position | null {
  const openCount = (db.prepare(
    `SELECT COUNT(*) as cnt FROM positions WHERE mode = ? AND status IN ('open', 'partial')`
  ).get(mode) as any).cnt;

  if (openCount >= EXECUTION_PARAMS.MAX_PAPER_POSITIONS) {
    log.debug({ tokenAddress, openCount }, 'Max positions reached, skipping entry');
    return null;
  }

  const existing = db.prepare(
    `SELECT id FROM positions WHERE token_address = ? AND status IN ('open', 'partial')`
  ).get(tokenAddress) as any;
  if (existing) {
    log.debug({ tokenAddress }, 'Already have open position for token');
    return null;
  }

  if (entryPrice <= 0) {
    log.warn({ tokenAddress, entryPrice }, 'Invalid entry price, skipping');
    return null;
  }

  const sizeVirtual = EXECUTION_PARAMS.PAPER_TRADE_SIZE_VIRTUAL;
  const tokensHeld = sizeVirtual / entryPrice;
  const now = Math.floor(Date.now() / 1000);

  db.prepare(
    `INSERT INTO positions (token_address, mode, entry_ts, entry_price, size_eth, tokens_held, status, realized_pnl_eth, exit_log)
     VALUES (?, ?, ?, ?, ?, ?, 'open', '0', '[]')`
  ).run(tokenAddress, mode, now, entryPrice.toString(), sizeVirtual.toString(), tokensHeld.toString());

  const id = (db.prepare('SELECT last_insert_rowid() as id').get() as any).id;

  bus.emit('position:opened', {
    tokenAddress: tokenAddress as Address,
    mode,
    sizeEth: BigInt(Math.floor(sizeVirtual * 1e18)),
  });

  log.info({ tokenAddress, entryPrice, sizeVirtual, tokensHeld }, 'Paper position opened');

  return { id, tokenAddress, mode, entryTs: now, entryPrice, sizeVirtual, tokensHeld, status: 'open', realizedPnl: 0, exitLog: [] };
}

export function closePartial(
  positionId: number,
  sellPct: number,
  currentPrice: number,
  reason: string
): void {
  const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(positionId) as any;
  if (!row || row.status === 'closed') return;

  const tokensHeld = parseFloat(row.tokens_held);
  const tokensSold = tokensHeld * (sellPct / 100);
  const virtualReturned = tokensSold * currentPrice;
  const costBasis = parseFloat(row.size_eth) * (sellPct / 100);
  const realizedPnl = parseFloat(row.realized_pnl_eth || '0') + (virtualReturned - costBasis);
  const remainingTokens = tokensHeld - tokensSold;

  const exitLog: ExitEvent[] = JSON.parse(row.exit_log || '[]');
  exitLog.push({ ts: Math.floor(Date.now() / 1000), reason, price: currentPrice, tokensSold, virtualReturned });

  const newStatus = remainingTokens < 1 ? 'closed' : 'partial';

  db.prepare(
    `UPDATE positions SET tokens_held = ?, realized_pnl_eth = ?, status = ?, exit_log = ? WHERE id = ?`
  ).run(remainingTokens.toString(), realizedPnl.toString(), newStatus, JSON.stringify(exitLog), positionId);

  log.info({ positionId, reason, sellPct, currentPrice, virtualReturned, realizedPnl }, 'Partial close');

  if (newStatus === 'closed') {
    bus.emit('position:closed', {
      tokenAddress: row.token_address as Address,
      mode: row.mode,
      pnlEth: BigInt(Math.floor(realizedPnl * 1e18)),
      reason,
    });
  }
}

export function closePosition(
  positionId: number,
  currentPrice: number,
  reason: string
): void {
  const row = db.prepare('SELECT * FROM positions WHERE id = ?').get(positionId) as any;
  if (!row || row.status === 'closed') return;

  const tokensHeld = parseFloat(row.tokens_held);
  const virtualReturned = tokensHeld * currentPrice;
  const costBasis = parseFloat(row.size_eth);
  const existingPnl = parseFloat(row.realized_pnl_eth || '0');
  const realizedPnl = existingPnl + (virtualReturned - costBasis);

  const exitLog: ExitEvent[] = JSON.parse(row.exit_log || '[]');
  exitLog.push({ ts: Math.floor(Date.now() / 1000), reason, price: currentPrice, tokensSold: tokensHeld, virtualReturned });

  db.prepare(
    `UPDATE positions SET tokens_held = '0', realized_pnl_eth = ?, status = 'closed', exit_log = ? WHERE id = ?`
  ).run(realizedPnl.toString(), JSON.stringify(exitLog), positionId);

  bus.emit('position:closed', {
    tokenAddress: row.token_address as Address,
    mode: row.mode,
    pnlEth: BigInt(Math.floor(realizedPnl * 1e18)),
    reason,
  });

  log.info({ positionId, reason, currentPrice, virtualReturned, realizedPnl }, 'Position closed');
}

export function getOpenPositions(): any[] {
  return db.prepare(
    `SELECT * FROM positions WHERE status IN ('open', 'partial') ORDER BY entry_ts ASC`
  ).all() as any[];
}

export function getAllPositions(mode = 'paper'): any[] {
  return db.prepare(
    `SELECT * FROM positions WHERE mode = ? ORDER BY entry_ts DESC`
  ).all(mode) as any[];
}

export function getCurrentPrice(tokenAddress: string): number | null {
  const row = db.prepare(
    `SELECT CAST(price AS REAL) as price FROM swaps
     WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND price != '0' AND price IS NOT NULL
     ORDER BY ts DESC LIMIT 1`
  ).get(tokenAddress) as any;
  return row?.price ?? null;
}

export function getPeakPrice(tokenAddress: string, sinceTs: number): number | null {
  const row = db.prepare(
    `SELECT MAX(CAST(price AS REAL)) as peak FROM swaps
     WHERE pool_address IN (SELECT address FROM pools WHERE token_address = ?)
     AND price != '0' AND ts >= ?`
  ).get(tokenAddress, sinceTs) as any;
  return row?.peak ?? null;
}
