import { Bot } from 'grammy';
import { env } from '../config/env.js';
import { createModuleLogger } from './logger.js';

const log = createModuleLogger('telegram');

let bot: Bot | null = null;

export function initTelegram(): Bot {
  bot = new Bot(env.TELEGRAM_BOT_TOKEN);
  log.info('Telegram bot initialized');
  return bot;
}

export async function sendAlert(message: string): Promise<void> {
  if (!bot) {
    log.warn('Telegram bot not initialized, skipping alert');
    return;
  }

  try {
    await bot.api.sendMessage(env.TELEGRAM_CHAT_ID, message, {
      parse_mode: 'HTML',
      link_preview_options: { is_disabled: true },
    });
  } catch (err) {
    log.error({ err }, 'Failed to send Telegram alert');
  }
}

export function getTelegramBot(): Bot | null {
  return bot;
}
