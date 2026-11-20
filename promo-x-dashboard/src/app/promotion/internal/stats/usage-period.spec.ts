import {
  axisTicks,
  dayCount,
  labelledDays,
  periodError,
  presetPeriod,
  toUtcRange,
} from './usage-period';
import { setTimeZone } from './time-zone.testing';

describe('usage period', () => {
  // UTC+2 in winter, UTC+3 in summer
  let restoreTimeZone: () => void;
  beforeEach(() => (restoreTimeZone = setTimeZone('Europe/Kyiv')));
  afterEach(() => restoreTimeZone());

  it('picks the last days in the local time zone, today included', () => {
    // Still the 19th in UTC, already the 20th in Kyiv
    const now = new Date('2026-11-19T22:30:00Z');

    expect(presetPeriod(7, now)).toEqual({ from: '2026-11-14', to: '2026-11-20' });
    expect(presetPeriod(1, now)).toEqual({ from: '2026-11-20', to: '2026-11-20' });
  });

  it('sends local days as UTC instants, from the first midnight to the one ending the last day', () => {
    expect(toUtcRange({ from: '2026-11-14', to: '2026-11-20' })).toEqual({
      from: '2026-11-13T22:00:00.000Z',
      to: '2026-11-20T22:00:00.000Z',
    });
  });

  it('follows daylight saving time', () => {
    // Kyiv goes from UTC+3 to UTC+2 on 2026-10-25
    const period = { from: '2026-10-24', to: '2026-10-26' };

    expect(toUtcRange(period)).toEqual({
      from: '2026-10-23T21:00:00.000Z',
      to: '2026-10-26T22:00:00.000Z',
    });
    expect(dayCount(period)).toBe(3);
  });

  it('rejects a period that is backwards, unreal or too long', () => {
    expect(periodError({ from: '2026-11-01', to: '2026-11-30' })).toBeNull();
    expect(periodError({ from: '2026-11-30', to: '2026-11-01' })).toBe('"From" is after "To".');
    expect(periodError({ from: '2026-02-30', to: '2026-03-01' })).toBe('Choose both days.');
    expect(periodError({ from: '', to: '2026-03-01' })).toBe('Choose both days.');
    expect(periodError({ from: '2025-01-01', to: '2026-11-30' })).toBe('At most 366 days.');
  });

  it('rounds the axis up to clean ticks', () => {
    expect(axisTicks(0)).toEqual([0, 1]);
    expect(axisTicks(3)).toEqual([0, 1, 2, 3]);
    expect(axisTicks(7)).toEqual([0, 2, 4, 6, 8]);
    expect(axisTicks(45)).toEqual([0, 20, 40, 60]);
    expect(axisTicks(1200)).toEqual([0, 500, 1000, 1500]);
  });

  it('labels the first and last days and a few evenly between', () => {
    expect([...labelledDays(3)]).toEqual([0, 1, 2]);
    expect([...labelledDays(30)]).toEqual([0, 7, 15, 22, 29]);
  });
});
