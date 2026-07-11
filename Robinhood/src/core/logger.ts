import pino from 'pino';
import { env } from '../config/env.js';
import path from 'path';
import fs from 'fs';

const logsDir = path.resolve('logs');
if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

export const logger = pino({
  level: 'info',
  transport: {
    targets: [
      {
        target: 'pino/file',
        options: { destination: 1 }, // stdout
        level: 'info',
      },
      {
        target: 'pino/file',
        options: { destination: path.join(logsDir, 'hoodscan.log') },
        level: 'debug',
      },
    ],
  },
  base: { mode: env.MODE, chain: env.CHAIN },
});

export function createModuleLogger(module: string) {
  return logger.child({ module });
}
