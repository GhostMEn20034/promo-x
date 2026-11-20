/**
 * The period the usage statistics cover, as days in the user's time zone, its translation to the UTC instants
 * promo-x-core takes, and the chart's axis.
 */

export const PERIOD_PRESETS = [
  { days: 7, label: 'Last 7 days' },
  { days: 30, label: 'Last 30 days' },
  { days: 90, label: 'Last 90 days' },
] as const;

export const DEFAULT_PRESET_DAYS = 30;

/** A custom period can't be longer than a year, so the chart's columns stay readable. */
export const MAX_PERIOD_DAYS = 366;

/** Days in the user's time zone, `YYYY-MM-DD`, both included. */
export interface Period {
  readonly from: string;
  readonly to: string;
}

const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

/** The local day of an instant, `YYYY-MM-DD`. */
export function localDay(instant: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${instant.getFullYear()}-${pad(instant.getMonth() + 1)}-${pad(instant.getDate())}`;
}

/** The local midnight starting the day, or null when the text isn't a real day. */
function startOf(day: string, plusDays = 0): Date | null {
  const match = DAY.exec(day);
  if (!match) {
    return null;
  }
  const [year, month, date] = [Number(match[1]), Number(match[2]) - 1, Number(match[3])];
  const start = new Date(year, month, date);
  // Rejects days such as 2026-02-30, which Date would roll over
  if (start.getFullYear() !== year || start.getMonth() !== month || start.getDate() !== date) {
    return null;
  }
  return new Date(year, month, date + plusDays);
}

/** The last `days` local days, today included. */
export function presetPeriod(days: number, now: Date): Period {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const first = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1));
  return { from: localDay(first), to: localDay(today) };
}

/** What's wrong with the period, or null. */
export function periodError(period: Period): string | null {
  const from = startOf(period.from);
  const to = startOf(period.to);
  if (!from || !to) {
    return 'Choose both days.';
  }
  if (from > to) {
    return '"From" is after "To".';
  }
  return dayCount(period) > MAX_PERIOD_DAYS ? `At most ${MAX_PERIOD_DAYS} days.` : null;
}

/** How many local days the period has. */
export function dayCount(period: Period): number {
  const from = startOf(period.from)!;
  const to = startOf(period.to)!;
  // Rounded: a day across a daylight saving change lasts 23 or 25 hours
  return Math.round((to.getTime() - from.getTime()) / (24 * 60 * 60 * 1000)) + 1;
}

/**
 * The UTC instants promo-x-core takes for a valid period: from the local midnight starting the first day to the one
 * ending the last day, which it excludes.
 */
export function toUtcRange(period: Period): { readonly from: string; readonly to: string } {
  return {
    from: startOf(period.from)!.toISOString(),
    to: startOf(period.to, 1)!.toISOString(),
  };
}

/**
 * Round ticks from 0 to at least `max`, about `count` of them: steps of 1, 2 or 5 times a power of ten. A period
 * without usage still gets an axis, 0 to 1.
 */
export function axisTicks(max: number, count = 4): readonly number[] {
  const top = Math.max(max, 1);
  const rough = top / count;
  const power = 10 ** Math.floor(Math.log10(rough));
  const step =
    [1, 2, 5, 10].map((m) => m * power).find((candidate) => candidate >= rough) ?? 10 * power;
  const wholeStep = Math.max(1, Math.round(step));
  const ticks: number[] = [];
  for (let tick = 0; tick < top + wholeStep; tick += wholeStep) {
    ticks.push(tick);
    if (tick >= top) {
      break;
    }
  }
  return ticks;
}

/** The indexes of the days labelled on the axis: the first, the last, and evenly spaced ones between. */
export function labelledDays(count: number, labels = 5): ReadonlySet<number> {
  if (count <= labels) {
    return new Set(Array.from({ length: count }, (_, i) => i));
  }
  const step = (count - 1) / (labels - 1);
  return new Set(Array.from({ length: labels }, (_, i) => Math.round(i * step)));
}
