import { formatEther, type Address } from 'viem';
import { bus } from '../core/eventBus.js';
import { db } from '../core/db.js';
import { createModuleLogger } from '../core/logger.js';
import { sendAlert } from '../core/telegram.js';
import { runSafetyPipeline } from '../safety/pipeline.js';
import { score } from '../scoring/scorer.js';
import { collectFeatures } from '../scoring/featureCollector.js';
import { env } from '../config/env.js';

const log = createModuleLogger('alertController');

function formatSafetyEmoji(verdict: string): string {
  switch (verdict) {
    case 'PASS': return '[OK]';
    case 'VETO': return '[X]';
    case 'UNKNOWN': return '[?]';
    default: return '[-]';
  }
}

function buildAlertMessage(
  tokenAddress: string,
  symbol: string | null,
  name: string | null,
  liquidityEth: string,
  safetyOverall: string,
  safetyChecks: any[],
  scoreTotal: number,
  scoreBreakdown: Record<string, number>,
  poolAddress: string,
  amm: string
): string {
  const header = safetyOverall === 'VETO'
    ? '<b>[VETOED]</b>'
    : scoreTotal >= env.TRADE_THRESHOLD
      ? '<b>[TRADE SIGNAL]</b>'
      : scoreTotal >= env.ALERT_THRESHOLD
        ? '<b>[ALERT]</b>'
        : '<b>[NEW LAUNCH]</b>';

  const lines = [
    `${header} ${symbol || '???'} (${name || 'Unknown'})`,
    ``,
    `<b>Token:</b> <code>${tokenAddress}</code>`,
    `<b>Pool:</b> <code>${poolAddress}</code> (${amm})`,
    `<b>Liquidity:</b> ${liquidityEth} ETH`,
    ``,
    `<b>Safety: ${safetyOverall}</b>`,
    ...safetyChecks.map(c => `  ${formatSafetyEmoji(c.verdict)} ${c.check}: ${c.reason}`),
    ``,
    `<b>Score: ${scoreTotal}</b>`,
    ...Object.entries(scoreBreakdown)
      .filter(([_, v]) => v !== 0)
      .map(([k, v]) => `  ${v > 0 ? '+' : ''}${v} ${k}`),
  ];

  // Add explorer link
  lines.push(`\n<a href="https://robinhoodchain.blockscout.com/token/${tokenAddress}">Blockscout</a>`);

  return lines.join('\n');
}

export async function startAlertController() {
  log.info('Starting alert controller');

  bus.on('pool:launched', async (data) => {
    const { tokenAddress, poolAddress, liquidityWeth, block } = data;

    try {
      // Run safety pipeline
      const safetyReport = await runSafetyPipeline(tokenAddress);

      // Collect features and score
      const features = collectFeatures(tokenAddress);
      const scoreResult = score(features);

      // Store score snapshot
      const now = Math.floor(Date.now() / 1000);
      db.prepare(
        `INSERT INTO score_snapshots (token_address, ts, total, breakdown) VALUES (?, ?, ?, ?)`
      ).run(tokenAddress, now, scoreResult.total, JSON.stringify(scoreResult.signals));

      // Get token info for alert
      const token = db.prepare('SELECT name, symbol FROM tokens WHERE address = ?').get(tokenAddress) as any;
      const pool = db.prepare('SELECT amm FROM pools WHERE address = ?').get(poolAddress) as any;

      // Emit scored event
      bus.emit('token:scored', {
        tokenAddress,
        score: scoreResult.total,
        breakdown: scoreResult.signals,
      });

      // Update token status based on score
      if (safetyReport.overall !== 'VETO' && scoreResult.total >= env.ALERT_THRESHOLD) {
        db.prepare(`UPDATE tokens SET status = 'alerted' WHERE address = ? AND status = 'watching'`)
          .run(tokenAddress);
        bus.emit('token:alerted', {
          tokenAddress,
          score: scoreResult.total,
          safetyStatus: safetyReport.overall,
        });
      }

      // Send Telegram alert
      const message = buildAlertMessage(
        tokenAddress,
        token?.symbol,
        token?.name,
        formatEther(liquidityWeth),
        safetyReport.overall,
        safetyReport.checks,
        scoreResult.total,
        scoreResult.signals,
        poolAddress,
        pool?.amm || 'unknown'
      );

      await sendAlert(message);

      log.info({
        tokenAddress,
        symbol: token?.symbol,
        safety: safetyReport.overall,
        score: scoreResult.total,
      }, 'Alert sent');
    } catch (err) {
      log.error({ tokenAddress, err }, 'Alert controller error');
    }
  });

  log.info('Alert controller active');
}
