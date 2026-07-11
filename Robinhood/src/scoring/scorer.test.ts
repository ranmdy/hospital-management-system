import { describe, it, expect } from 'vitest';
import { score, type TokenFeatures } from './scorer.js';

const emptyFeatures: TokenFeatures = {
  smartMoneyConfluence: false,
  holderGrowthAccel: false,
  buyPressure: false,
  sniperFlushComplete: false,
  metaMatch: false,
  socialPresence: false,
  dexscreenerBoost: false,
  lpLocked: false,
  freshDeployer: false,
  sniperDominated: false,
  stealthNoSocials: false,
};

describe('scorer', () => {
  it('returns 0 for no active signals', () => {
    const result = score(emptyFeatures);
    expect(result.total).toBe(0);
  });

  it('adds positive weight for smartMoneyConfluence', () => {
    const result = score({ ...emptyFeatures, smartMoneyConfluence: true });
    expect(result.total).toBe(30);
    expect(result.signals.smartMoneyConfluence).toBe(30);
  });

  it('subtracts weight for negative signals', () => {
    const result = score({ ...emptyFeatures, sniperDominated: true });
    expect(result.total).toBe(-25);
  });

  it('correctly sums multiple signals', () => {
    const result = score({
      ...emptyFeatures,
      smartMoneyConfluence: true,
      holderGrowthAccel: true,
      buyPressure: true,
      lpLocked: true,
      freshDeployer: true, // -10
    });
    // 30 + 20 + 15 + 10 - 10 = 65
    expect(result.total).toBe(65);
  });

  it('max possible score', () => {
    const result = score({
      smartMoneyConfluence: true,
      holderGrowthAccel: true,
      buyPressure: true,
      sniperFlushComplete: true,
      metaMatch: true,
      socialPresence: true,
      dexscreenerBoost: true,
      lpLocked: true,
      freshDeployer: false,
      sniperDominated: false,
      stealthNoSocials: false,
    });
    // 30+20+15+15+10+5+5+10 = 110
    expect(result.total).toBe(110);
  });

  it('min possible score (all negatives, no positives)', () => {
    const result = score({
      ...emptyFeatures,
      freshDeployer: true,
      sniperDominated: true,
      stealthNoSocials: true,
    });
    // -10 + -25 + -10 = -45
    expect(result.total).toBe(-45);
  });
});
