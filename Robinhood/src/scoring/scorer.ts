import { SCORING_WEIGHTS } from '../config/params.js';

export interface TokenFeatures {
  smartMoneyConfluence: boolean;
  holderGrowthAccel: boolean;
  buyPressure: boolean;
  sniperFlushComplete: boolean;
  metaMatch: boolean;
  socialPresence: boolean;
  dexscreenerBoost: boolean;
  lpLocked: boolean;
  freshDeployer: boolean;
  sniperDominated: boolean;
  stealthNoSocials: boolean;
}

export interface ScoreBreakdown {
  total: number;
  signals: Record<string, number>;
}

export function score(features: TokenFeatures): ScoreBreakdown {
  const signals: Record<string, number> = {};
  let total = 0;

  for (const [key, weight] of Object.entries(SCORING_WEIGHTS)) {
    const active = features[key as keyof TokenFeatures];
    const value = active ? weight : 0;
    signals[key] = value;
    total += value;
  }

  return { total, signals };
}
