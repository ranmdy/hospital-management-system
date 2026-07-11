import { db } from '../core/db.js';

export function cleanDb() {
  db.pragma('foreign_keys = OFF');
  db.exec('DELETE FROM events');
  db.exec('DELETE FROM score_snapshots');
  db.exec('DELETE FROM safety_reports');
  db.exec('DELETE FROM holder_snapshots');
  db.exec('DELETE FROM swaps');
  db.exec('DELETE FROM positions');
  db.exec('DELETE FROM pools');
  db.exec('DELETE FROM tokens');
  db.exec('DELETE FROM wallets');
  db.exec('DELETE FROM system_state');
  db.pragma('foreign_keys = ON');
}
