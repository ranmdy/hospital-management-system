import { describe, it, expect, vi } from 'vitest';
import { bus } from './eventBus.js';

describe('eventBus', () => {
  it('emits and receives events', () => {
    const handler = vi.fn();
    bus.on('pool:created', handler);

    bus.emit('pool:created', {
      poolAddress: '0xpool',
      token0: '0xtoken0',
      token1: '0xtoken1',
      fee: 500,
      block: 100,
    });

    expect(handler).toHaveBeenCalledOnce();
    expect(handler).toHaveBeenCalledWith({
      poolAddress: '0xpool',
      token0: '0xtoken0',
      token1: '0xtoken1',
      fee: 500,
      block: 100,
    });

    bus.off('pool:created', handler);
  });

  it('supports multiple handlers', () => {
    const handler1 = vi.fn();
    const handler2 = vi.fn();
    bus.on('token:vetoed', handler1);
    bus.on('token:vetoed', handler2);

    bus.emit('token:vetoed', { tokenAddress: '0xtoken', reason: 'test' });

    expect(handler1).toHaveBeenCalledOnce();
    expect(handler2).toHaveBeenCalledOnce();

    bus.off('token:vetoed', handler1);
    bus.off('token:vetoed', handler2);
  });

  it('once handler fires only once', () => {
    const handler = vi.fn();
    bus.once('system:halt', handler);

    bus.emit('system:halt', { reason: 'test1' });
    bus.emit('system:halt', { reason: 'test2' });

    expect(handler).toHaveBeenCalledOnce();
    expect(handler).toHaveBeenCalledWith({ reason: 'test1' });
  });

  it('off removes handler', () => {
    const handler = vi.fn();
    bus.on('token:alerted', handler);
    bus.off('token:alerted', handler);

    bus.emit('token:alerted', { tokenAddress: '0x1', score: 50, safetyStatus: 'PASS' });

    expect(handler).not.toHaveBeenCalled();
  });
});
