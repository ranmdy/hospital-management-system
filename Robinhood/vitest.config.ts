import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@config': path.resolve(__dirname, 'src/config'),
      '@core': path.resolve(__dirname, 'src/core'),
      '@ingestion': path.resolve(__dirname, 'src/ingestion'),
      '@safety': path.resolve(__dirname, 'src/safety'),
      '@scoring': path.resolve(__dirname, 'src/scoring'),
      '@execution': path.resolve(__dirname, 'src/execution'),
      '@evaluation': path.resolve(__dirname, 'src/evaluation'),
    },
  },
  test: {
    globals: true,
    fileParallelism: false,
    env: {
      MODE: 'scanner',
      CHAIN: 'testnet',
      ALCHEMY_API_KEY: 'test_key',
      TELEGRAM_BOT_TOKEN: 'test_token',
      TELEGRAM_CHAT_ID: 'test_chat',
    },
  },
});
