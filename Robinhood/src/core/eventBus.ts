import { EventEmitter } from 'events';
import type { Address } from 'viem';
import { createModuleLogger } from './logger.js';

const log = createModuleLogger('eventBus');

// Typed event definitions
export interface BusEvents {
  'pool:created': {
    poolAddress: Address;
    token0: Address;
    token1: Address;
    fee: number;
    block: number;
  };
  'pool:launched': {
    poolAddress: Address;
    tokenAddress: Address;
    liquidityWeth: bigint;
    lpProvider: Address;
    block: number;
  };
  'token:scored': {
    tokenAddress: Address;
    score: number;
    breakdown: Record<string, number>;
  };
  'token:alerted': {
    tokenAddress: Address;
    score: number;
    safetyStatus: string;
  };
  'token:vetoed': {
    tokenAddress: Address;
    reason: string;
  };
  'wallet:activity': {
    wallet: Address;
    tokenAddress: Address;
    action: 'buy' | 'sell';
    amountWeth: bigint;
    block: number;
  };
  'position:opened': {
    tokenAddress: Address;
    mode: 'paper' | 'live';
    sizeEth: bigint;
  };
  'position:closed': {
    tokenAddress: Address;
    mode: 'paper' | 'live';
    pnlEth: bigint;
    reason: string;
  };
  'system:halt': {
    reason: string;
  };
}

class TypedEventBus {
  private emitter = new EventEmitter();

  constructor() {
    this.emitter.setMaxListeners(50);
  }

  emit<K extends keyof BusEvents>(event: K, data: BusEvents[K]): void {
    log.debug({ event, tokenAddress: (data as any).tokenAddress }, 'Event emitted');
    this.emitter.emit(event, data);
  }

  on<K extends keyof BusEvents>(event: K, handler: (data: BusEvents[K]) => void): void {
    this.emitter.on(event, handler);
  }

  off<K extends keyof BusEvents>(event: K, handler: (data: BusEvents[K]) => void): void {
    this.emitter.off(event, handler);
  }

  once<K extends keyof BusEvents>(event: K, handler: (data: BusEvents[K]) => void): void {
    this.emitter.once(event, handler);
  }
}

export const bus = new TypedEventBus();
