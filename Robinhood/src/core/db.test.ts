import { describe, it, expect, beforeEach } from 'vitest';
import { db, getState, setState } from './db.js';

beforeEach(() => {
  db.exec('DELETE FROM system_state');
});

describe('db helpers', () => {
  it('setState and getState round-trip', () => {
    setState('test_key', 'test_value');
    expect(getState('test_key')).toBe('test_value');
  });

  it('getState returns undefined for missing key', () => {
    expect(getState('nonexistent')).toBeUndefined();
  });

  it('setState overwrites existing values', () => {
    setState('counter', '1');
    setState('counter', '2');
    expect(getState('counter')).toBe('2');
  });

  it('database tables exist after migration', () => {
    const tables = db.prepare(
      "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
    ).all() as { name: string }[];

    const tableNames = tables.map(t => t.name);
    expect(tableNames).toContain('tokens');
    expect(tableNames).toContain('pools');
    expect(tableNames).toContain('swaps');
    expect(tableNames).toContain('wallets');
    expect(tableNames).toContain('safety_reports');
    expect(tableNames).toContain('positions');
    expect(tableNames).toContain('events');
    expect(tableNames).toContain('system_state');
  });

  it('indexes exist', () => {
    const indexes = db.prepare(
      "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'"
    ).all() as { name: string }[];

    const names = indexes.map(i => i.name);
    expect(names).toContain('idx_swaps_pool_block');
    expect(names).toContain('idx_swaps_wallet');
    expect(names).toContain('idx_holder_snapshots_token_ts');
    expect(names).toContain('idx_events_token_ts');
    expect(names).toContain('idx_score_snapshots_token_ts');
  });
});
