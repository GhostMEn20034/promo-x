import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../infrastructure/http';
import { UsageStats } from '../promotion.model';
import { setTimeZone } from './time-zone.testing';
import { UsageStatsPanel } from './usage-stats-panel';

describe('UsageStatsPanel', () => {
  const statsUrl = 'http://api.test/api/promotion/usage/stats';
  const stats: UsageStats = {
    totals: {
      usageCount: 12,
      enteredPromoCodeCount: 9,
      registeredCustomerUsageCount: 3,
      anonymousCustomerUsageCount: 9,
      averageTotalDiscount: 10,
      averageSubtotalDiscount: 8.3333,
      averageShippingDiscount: 1.6667,
    },
    dailyCounts: [
      { date: '2026-11-18', usageCount: 2 },
      { date: '2026-11-19', usageCount: 0 },
      { date: '2026-11-20', usageCount: 10 },
    ],
  };

  // UTC+2 now, UTC+3 before 2026-10-25
  let restoreTimeZone: () => void;
  beforeEach(() => (restoreTimeZone = setTimeZone('Europe/Kyiv')));
  afterEach(() => restoreTimeZone());

  let fixture: ComponentFixture<UsageStatsPanel>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-20T12:00:00Z'));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  /** Renders the panel and answers its request; returns the request's query parameters. */
  async function render(response: UsageStats = stats): Promise<Record<string, string | null>> {
    fixture = TestBed.createComponent(UsageStatsPanel);
    fixture.componentRef.setInput('storefrontId', 1);
    TestBed.tick();
    const request = http.expectOne((req) => req.url === statsUrl);
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    const params = Object.fromEntries(
      request.request.params.keys().map((key) => [key, request.request.params.get(key)]),
    );
    request.flush(response);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
    return params;
  }

  function text(selector: string): string {
    return element.querySelector(selector)!.textContent!.replace(/\s+/g, ' ').trim();
  }

  /** The tooltip's lines: the value, then the day. */
  function tooltip(): string[] {
    return [...element.querySelectorAll('[data-testid="usage-tooltip"] p')].map((line) =>
      line.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('asks for the last 30 local days by default, as UTC instants', async () => {
    expect(await render()).toEqual({
      from: '2026-10-21T21:00:00.000Z',
      to: '2026-11-20T22:00:00.000Z',
    });
    expect(element.querySelector<HTMLInputElement>('#usage-from')!.value).toBe('2026-10-22');
    expect(element.querySelector<HTMLInputElement>('#usage-to')!.value).toBe('2026-11-20');
    // ICU may name it Europe/Kiev
    expect(text('[data-testid="usage-time-zone"]')).toBe(
      `Days in your time zone, ${Intl.DateTimeFormat().resolvedOptions().timeZone}`,
    );
    expect(text('[data-testid="usage-utc-note"]')).toContain('promo-x-core counts days in UTC');
  });

  it('asks for the days chosen in the date fields', async () => {
    await render();

    const from = element.querySelector<HTMLInputElement>('#usage-from')!;
    from.value = '2026-11-01';
    from.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    TestBed.tick();
    const request = http.expectOne((req) => req.url === statsUrl);
    expect(request.request.params.get('from')).toBe('2026-10-31T22:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-11-20T22:00:00.000Z');
    request.flush(stats);
    await fixture.whenStable();

    // A custom period isn't a preset
    expect(element.querySelector('[role="radio"][aria-checked="true"]')).toBeNull();
  });

  it("doesn't ask for a backwards period, and says why", async () => {
    await render();

    const to = element.querySelector<HTMLInputElement>('#usage-to')!;
    to.value = '2026-10-01';
    to.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    TestBed.tick();

    http.expectNone((req) => req.url === statsUrl);
    expect(text('[role="alert"]')).toBe('"From" is after "To".');
    expect(element.querySelector('[data-testid="usage-stats"]')!.classList).toContain('opacity-50');
  });

  it('shows the totals as tiles', async () => {
    await render();

    expect(text('[data-testid="usage-count"]')).toBe('12');
    expect(text('[data-testid="usage-average"]')).toBe('10.00');
    expect(text('[data-testid="usage-customers"]')).toBe('25% signed in');
    expect(text('[data-testid="usage-codes"]')).toBe('9');
    expect(element.textContent).toContain('Items 8.33 · Shipping 1.67, per use');
  });

  it('draws a column per day, labelling only the busiest one', async () => {
    await render();

    const days = [...element.querySelectorAll<HTMLElement>('[data-testid="usage-day"]')];
    expect(days).toHaveLength(3);
    // A day without uses draws no column
    expect(days.map((day) => day.querySelectorAll('span').length > 0)).toEqual([true, false, true]);
    expect(days[2].textContent!.trim()).toBe('10');
    expect(days[0].textContent!.trim()).toBe('');
    expect(element.textContent).toContain('Busiest: Fri, Nov 20, 2026, 10 uses');
    expect(
      [...element.querySelectorAll('[data-testid="usage-table"] tbody tr')].map((row) =>
        row.textContent!.replace(/\s+/g, ' ').trim(),
      ),
    ).toEqual(['Wed, Nov 18, 2026 2', 'Thu, Nov 19, 2026 0', 'Fri, Nov 20, 2026 10']);
  });

  it('shows a day on hover and with the arrow keys', async () => {
    await render();
    const days = element.querySelectorAll<HTMLElement>('[data-testid="usage-day"]');

    days[0].dispatchEvent(new Event('pointerenter'));
    fixture.detectChanges();
    expect(tooltip()).toEqual(['2 uses', 'Wed, Nov 18, 2026']);

    const chart = element.querySelector<HTMLElement>('[data-testid="usage-chart"]')!;
    chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(tooltip()).toEqual(['0 uses', 'Thu, Nov 19, 2026']);

    chart.dispatchEvent(new Event('pointerleave'));
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="usage-tooltip"]')).toBeNull();
  });

  it('asks for another period when one is chosen', async () => {
    await render();

    [...element.querySelectorAll<HTMLButtonElement>('[role="radio"]')]
      .find((button) => button.textContent!.trim() === 'Last 7 days')!
      .click();
    fixture.detectChanges();
    TestBed.tick();
    const request = http.expectOne((req) => req.url === statsUrl);
    expect(request.request.params.get('from')).toBe('2026-11-13T22:00:00.000Z');
    request.flush(stats);
    await fixture.whenStable();
    expect(text('[role="radio"][aria-checked="true"]')).toBe('Last 7 days');
    expect(element.querySelector<HTMLInputElement>('#usage-from')!.value).toBe('2026-11-14');
  });

  it('says when there are no uses', async () => {
    await render({
      totals: {
        usageCount: 0,
        enteredPromoCodeCount: 0,
        registeredCustomerUsageCount: 0,
        anonymousCustomerUsageCount: 0,
        averageTotalDiscount: null,
        averageSubtotalDiscount: null,
        averageShippingDiscount: null,
      },
      dailyCounts: [{ date: '2026-11-20', usageCount: 0 }],
    });

    expect(text('[data-testid="usage-average"]')).toBe('—');
    expect(element.textContent).toContain('No uses in this period');
  });

  it('says when the user may not see the statistics', async () => {
    fixture = TestBed.createComponent(UsageStatsPanel);
    fixture.componentRef.setInput('storefrontId', 1);
    TestBed.tick();
    http
      .expectOne((req) => req.url === statsUrl)
      .flush(null, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain("You're not allowed to see the usage statistics.");
  });
});
