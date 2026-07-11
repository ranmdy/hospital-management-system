// Tunable parameters — v1 weights are placeholders, re-fit from Layer 5 weekly reports

export const SCORING_WEIGHTS = {
  smartMoneyConfluence: 30,
  holderGrowthAccel: 20,
  buyPressure: 15,
  sniperFlushComplete: 15,
  metaMatch: 10,
  socialPresence: 5,
  dexscreenerBoost: 5,
  lpLocked: 10,
  freshDeployer: -10,
  sniperDominated: -25,
  stealthNoSocials: -10,
} as const;

export const THRESHOLDS = {
  ALERT_THRESHOLD: 40,
  TRADE_THRESHOLD: 60,
} as const;

export const SAFETY_PARAMS = {
  MAX_IMPLIED_TAX_PCT: 15,
  MAX_TOP10_HOLDER_PCT: 40,
  MAX_DEPLOYER_HOLD_PCT: 20,
  SERIAL_RUG_MIN_COUNT: 2,
  MIN_INITIAL_LIQUIDITY_WETH: 2n * 10n ** 18n, // 2 WETH in wei
} as const;

export const EXECUTION_PARAMS = {
  EXIT_LADDER: [
    { profitPct: 100, sellPct: 50 },  // at +100% sell half
    { profitPct: 200, sellPct: 25 },  // at +200% sell quarter
  ],
  TRAILING_STOP_DRAWDOWN_PCT: 40,
  HARD_STOP_LOSS_PCT: 50,
  PAPER_TRADE_SIZE_VIRTUAL: 100,      // VIRTUAL per paper trade
  MAX_PAPER_POSITIONS: 5,             // max concurrent paper positions
  TIME_STOP_HOURS: 24,                // close after 24h if no exit triggered
} as const;

// Symbols that indicate impersonation scams
export const BLOCKLISTED_SYMBOLS = [
  'WETH', 'ETH', 'USDT', 'USDC', 'USDG', 'DAI', 'WBTC', 'BTC',
  'NVDA', 'AAPL', 'TSLA', 'AMZN', 'GOOG', 'MSFT', 'META',
  'SPY', 'QQQ', 'HOOD',
] as const;
