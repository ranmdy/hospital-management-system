import { config } from 'dotenv';
import { z } from 'zod';

config();

const envSchema = z.object({
  MODE: z.enum(['scanner', 'paper', 'live']).default('scanner'),
  CHAIN: z.enum(['mainnet', 'testnet']).default('mainnet'),
  ALCHEMY_API_KEY: z.string().min(1, 'ALCHEMY_API_KEY is required'),
  QUICKNODE_URL: z.string().optional(),
  TELEGRAM_BOT_TOKEN: z.string().min(1, 'TELEGRAM_BOT_TOKEN is required'),
  TELEGRAM_CHAT_ID: z.string().min(1, 'TELEGRAM_CHAT_ID is required'),
  PAPER_BANKROLL_ETH: z.coerce.number().positive().default(1.0),
  POSITION_PCT: z.coerce.number().positive().default(2),
  MAX_POSITION_ETH: z.coerce.number().positive().default(0.05),
  MAX_POSITIONS: z.coerce.number().int().positive().default(4),
  MAX_DAILY_ENTRIES: z.coerce.number().int().positive().default(6),
  SLIPPAGE_BPS: z.coerce.number().int().positive().default(300),
  PANIC_SLIPPAGE: z.coerce.number().int().positive().default(2500),
  MAX_DRAWDOWN_PCT: z.coerce.number().positive().default(20),
  ALERT_THRESHOLD: z.coerce.number().default(40),
  TRADE_THRESHOLD: z.coerce.number().default(60),
  MIN_INITIAL_LIQUIDITY_WETH: z.coerce.number().positive().default(2),
  HOT_WALLET_PRIVATE_KEY: z.string().optional(),
  LIVE_TRADING_ACK: z.string().optional(),
});

const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  console.error('Invalid environment configuration:');
  for (const issue of parsed.error.issues) {
    console.error(`  ${issue.path.join('.')}: ${issue.message}`);
  }
  process.exit(1);
}

export const env = parsed.data;

// Live mode double-gate
if (env.MODE === 'live') {
  if (!env.HOT_WALLET_PRIVATE_KEY) {
    console.error('MODE=live requires HOT_WALLET_PRIVATE_KEY');
    process.exit(1);
  }
  if (env.LIVE_TRADING_ACK !== 'I_UNDERSTAND_THE_RISKS') {
    console.error('MODE=live requires LIVE_TRADING_ACK=I_UNDERSTAND_THE_RISKS');
    process.exit(1);
  }
}
