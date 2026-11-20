import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { PromotionClient } from '../promotion.client';
import { UsageStats } from '../promotion.model';
import {
  axisTicks,
  DEFAULT_PRESET_DAYS,
  labelledDays,
  localDay,
  Period,
  PERIOD_PRESETS,
  periodError,
  presetPeriod,
  toUtcRange,
} from './usage-period';

const SHORT_DATE = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  timeZone: 'UTC',
});
const LONG_DATE = new Intl.DateTimeFormat('en-US', {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  timeZone: 'UTC',
});

/** A day of the chart, ready to draw. */
interface ChartDay {
  readonly date: string;
  readonly count: number;
  readonly short: string;
  readonly long: string;
  /** The column's height, as a share of the axis. */
  readonly percent: number;
}

/**
 * A storefront's promotion usage: totals as stat tiles and the uses of each day as a column chart. Only confirmed
 * (`USED`) usages count.
 *
 * The period is chosen as days in the user's time zone, a preset or a custom range, and sent as UTC instants, so
 * the totals cover exactly those days. promo-x-core counts the days themselves in UTC, so the chart's columns are
 * UTC days.
 *
 * It provides its own `PromotionClient`, so other modules can show it outside the promotion routes.
 */
@Component({
  selector: 'app-usage-stats-panel',
  imports: [DecimalPipe],
  providers: [PromotionClient],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './usage-stats-panel.html',
  styles: `
    :host {
      display: block;
      --surface: #ffffff;
      --series-1: #2a78d6;
      --series-1-hover: #4b8fe0;
      --series-2: #eb6834;
      --grid: #eceae6;
    }
  `,
})
export class UsageStatsPanel {
  readonly storefrontId = input.required<number>();

  protected readonly presets = PERIOD_PRESETS;
  protected readonly today = localDay(new Date());
  protected readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  /** Columns are UTC days, which only line up with the user's days when they're in UTC. */
  protected readonly inUtc = new Date().getTimezoneOffset() === 0;

  protected readonly period = signal<Period>(presetPeriod(DEFAULT_PRESET_DAYS, new Date()));
  protected readonly periodProblem = computed(() => periodError(this.period()));
  /** The preset the period is, or null for a custom one. */
  protected readonly preset = computed(() => {
    const period = this.period();
    const match = PERIOD_PRESETS.find((preset) => {
      const candidate = presetPeriod(preset.days, new Date());
      return candidate.from === period.from && candidate.to === period.to;
    });
    return match?.days ?? null;
  });
  private readonly range = computed(() =>
    this.periodProblem() ? undefined : toUtcRange(this.period()),
  );
  protected readonly stats = inject(PromotionClient).usageStats(this.storefrontId, this.range);

  /** The last statistics loaded, kept on screen, dimmed, while another period loads. */
  protected readonly shown = linkedSignal<UsageStats | undefined, UsageStats | undefined>({
    source: () => (this.stats.hasValue() ? this.stats.value() : undefined),
    computation: (current, previous) => current ?? previous?.value,
  });
  /** The statistics shown aren't the period's: another one is loading, or the period isn't valid. */
  protected readonly stale = computed(
    () => this.shown() !== undefined && (this.stats.isLoading() || this.periodProblem() !== null),
  );
  protected readonly forbidden = computed(() => {
    const error = this.stats.error();
    return error instanceof HttpErrorResponse && error.status === 403;
  });

  protected readonly chart = computed(() => {
    const stats = this.shown();
    if (!stats) {
      return null;
    }
    const counts = stats.dailyCounts;
    const peakCount = Math.max(0, ...counts.map((day) => day.usageCount));
    const ticks = axisTicks(peakCount);
    const top = ticks[ticks.length - 1];
    const days: ChartDay[] = counts.map((day) => {
      const date = new Date(`${day.date}T00:00:00Z`);
      return {
        date: day.date,
        count: day.usageCount,
        short: SHORT_DATE.format(date),
        long: LONG_DATE.format(date),
        percent: (day.usageCount / top) * 100,
      };
    });
    return {
      days,
      ticks: ticks.map((tick) => ({ value: tick, percent: (tick / top) * 100 })),
      labelled: labelledDays(days.length),
      /** The first busiest day, the one value labelled on the chart. Null when there's no usage. */
      peak: peakCount > 0 ? counts.findIndex((day) => day.usageCount === peakCount) : null,
      perDay: days.length > 0 ? stats.totals.usageCount / days.length : 0,
    };
  });

  protected readonly customers = computed(() => {
    const totals = this.shown()?.totals;
    if (!totals) {
      return null;
    }
    const registered = totals.registeredCustomerUsageCount;
    const anonymous = totals.anonymousCustomerUsageCount;
    const all = registered + anonymous;
    return {
      registered,
      anonymous,
      registeredPercent: all > 0 ? (registered / all) * 100 : 0,
      anonymousPercent: all > 0 ? (anonymous / all) * 100 : 0,
    };
  });

  /** The day the tooltip is about, from the pointer or the arrow keys. */
  protected readonly active = signal<number | null>(null);
  protected readonly activeDay = computed(() => {
    const index = this.active();
    return index === null ? null : (this.chart()?.days[index] ?? null);
  });

  protected choosePreset(days: number): void {
    this.setPeriod(presetPeriod(days, new Date()));
  }

  protected setFrom(from: string): void {
    this.setPeriod({ ...this.period(), from });
  }

  protected setTo(to: string): void {
    this.setPeriod({ ...this.period(), to });
  }

  private setPeriod(period: Period): void {
    this.period.set(period);
    this.active.set(null);
  }

  /** The arrow keys move between days; Home and End go to the first and last. */
  protected onKeydown(event: KeyboardEvent): void {
    const count = this.chart()?.days.length ?? 0;
    if (count === 0) {
      return;
    }
    const current = this.active() ?? count - 1;
    const next =
      event.key === 'ArrowLeft'
        ? Math.max(0, current - 1)
        : event.key === 'ArrowRight'
          ? Math.min(count - 1, current + 1)
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? count - 1
              : null;
    if (next !== null) {
      event.preventDefault();
      this.active.set(next);
    } else if (event.key === 'Escape') {
      this.active.set(null);
    }
  }

  /** Focusing the chart shows the latest day, so the arrow keys have somewhere to start. */
  protected onFocus(): void {
    if (this.active() === null) {
      const count = this.chart()?.days.length ?? 0;
      this.active.set(count > 0 ? count - 1 : null);
    }
  }

  /** Where the tooltip sits along the chart, kept inside it near the edges. */
  protected tooltipPlacement(index: number, count: number): { left: string; transform: string } {
    const center = ((index + 0.5) / count) * 100;
    if (center < 15) {
      return { left: `${(index / count) * 100}%`, transform: 'translateX(0)' };
    }
    if (center > 85) {
      return { left: `${((index + 1) / count) * 100}%`, transform: 'translateX(-100%)' };
    }
    return { left: `${center}%`, transform: 'translateX(-50%)' };
  }
}
