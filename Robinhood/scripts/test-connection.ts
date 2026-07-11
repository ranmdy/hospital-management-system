import { env } from '../src/config/env.js';
import { httpClient } from '../src/core/client.js';
import { sendAlert, initTelegram } from '../src/core/telegram.js';

async function main() {
  // Test chain connection
  const block = await httpClient.getBlockNumber();
  console.log(`Chain connected! Block: ${block}`);
  console.log(`Mode: ${env.MODE}, Chain: ${env.CHAIN}`);

  // Test Telegram
  initTelegram();
  await sendAlert('<b>HoodScan test</b>\nConnection verified.');
  console.log('Telegram alert sent!');
}

main().catch(err => {
  console.error('Connection test failed:', err.message);
  process.exit(1);
});
