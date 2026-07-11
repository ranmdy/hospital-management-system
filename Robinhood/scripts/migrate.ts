import { runMigrations } from '../src/core/db.js';

runMigrations();
console.log('Migrations applied successfully.');
process.exit(0);
