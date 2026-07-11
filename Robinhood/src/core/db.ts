import Database, { type Database as DatabaseType } from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { createModuleLogger } from './logger.js';

const log = createModuleLogger('db');

const DB_PATH = path.resolve('hoodscan.db');

export const db: DatabaseType = new Database(DB_PATH);

// Enable WAL mode for better concurrent read performance
db.pragma('journal_mode = WAL');
db.pragma('busy_timeout = 5000');
db.pragma('foreign_keys = ON');

export function runMigrations(): void {
  const migrationsDir = path.resolve('migrations');
  if (!fs.existsSync(migrationsDir)) {
    log.warn('No migrations directory found');
    return;
  }

  // Create migrations tracking table
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL UNIQUE,
      applied_at INTEGER NOT NULL
    )
  `);

  const applied = new Set(
    db.prepare('SELECT name FROM _migrations').all().map((r: any) => r.name)
  );

  const files = fs.readdirSync(migrationsDir)
    .filter(f => f.endsWith('.sql'))
    .sort();

  for (const file of files) {
    if (applied.has(file)) continue;

    const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf-8');
    log.info({ migration: file }, 'Applying migration');

    db.exec(sql);
    db.prepare('INSERT INTO _migrations (name, applied_at) VALUES (?, ?)').run(
      file,
      Date.now()
    );
  }

  log.info(`Migrations complete. ${files.length - applied.size} new applied.`);
}

// Helper: get/set system state
export function getState(key: string): string | undefined {
  const row = db.prepare('SELECT value FROM system_state WHERE key = ?').get(key) as
    | { value: string }
    | undefined;
  return row?.value;
}

export function setState(key: string, value: string): void {
  db.prepare(
    'INSERT OR REPLACE INTO system_state (key, value) VALUES (?, ?)'
  ).run(key, value);
}
