import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { PromotionClient } from '../promotion.client';
import { PromotionRuleSummary } from '../promotion.model';
import { PromotionRuleListPage } from './promotion-rule-list.page';

describe('PromotionRuleListPage', () => {
  const summer: PromotionRuleSummary = {
    id: 1,
    storefrontId: 1,
    name: '20% off keyboards with SUMMER',
    priority: 100,
    isShortCircuit: true,
    isActive: true,
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-12T09:00:00Z',
  };
  const freeShipping: PromotionRuleSummary = {
    ...summer,
    id: 2,
    name: 'Free shipping over 50',
    priority: 10,
    isShortCircuit: false,
    isActive: false,
  };
  const listUrl = 'http://api.test/api/promotion/';
  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };

  let fixture: ComponentFixture<PromotionRuleListPage>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function pageOf(content: PromotionRuleSummary[], totalPages = 1): Page<PromotionRuleSummary> {
    return {
      content,
      number: 0,
      size: 20,
      numberOfElements: content.length,
      totalElements: content.length,
      totalPages,
      first: true,
      last: totalPages === 1,
    };
  }

  /** Renders the page with these query parameters and answers its request; returns the request's params. */
  async function render(
    queryParams: Record<string, string>,
    page: Page<PromotionRuleSummary>,
  ): Promise<Record<string, string | null>> {
    fixture = TestBed.createComponent(PromotionRuleListPage);
    fixture.componentRef.setInput('storefrontId', '1');
    for (const [name, value] of Object.entries(queryParams)) {
      fixture.componentRef.setInput(name, value);
    }
    TestBed.tick();
    const request = http.expectOne((req) => req.url === listUrl);
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    const params = Object.fromEntries(
      request.request.params.keys().map((key) => [key, request.request.params.get(key)]),
    );
    request.flush(page);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
    return params;
  }

  function rows(): string[][] {
    return [...element.querySelectorAll('tbody tr')].map((row) =>
      [...row.querySelectorAll('td')].map((cell) => cell.textContent!.replace(/\s+/g, ' ').trim()),
    );
  }

  function change(id: string, value: string): void {
    const field = element.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    field.value = value;
    field.dispatchEvent(new Event('change'));
  }

  beforeEach(() => {
    roles.set([]);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        { provide: AuthApi, useValue: auth },
        PromotionClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  it('requests the first page newest first and renders a row per rule', async () => {
    const params = await render({}, pageOf([summer, freeShipping]));

    expect(params).toEqual({ page: '1', size: '20', sort: 'createdAt,desc' });
    expect(rows()).toEqual([
      [
        '1',
        '20% off keyboards with SUMMER',
        '100',
        'Active',
        'Stops lower rules',
        'Nov 10, 2026',
        'Nov 12, 2026',
      ],
      ['2', 'Free shipping over 50', '10', 'Inactive', '—', 'Nov 10, 2026', 'Nov 12, 2026'],
    ]);
    expect(element.querySelector('[data-testid="rules-count"]')!.textContent!.trim()).toBe(
      '2 rules',
    );
  });

  it('sends the filters and sort from the URL and shows them in the controls', async () => {
    const params = await render(
      {
        page: '2',
        sort: 'priority,desc',
        active: 'true',
        shortCircuit: 'false',
        createdFrom: '2026-11-01',
        createdTo: '2026-11-30',
      },
      pageOf([summer], 2),
    );

    expect(params).toEqual({
      page: '2',
      size: '20',
      sort: 'priority,desc',
      isActive: 'true',
      isShortCircuit: 'false',
      createdFrom: '2026-11-01T00:00:00Z',
      createdTo: '2026-11-30T23:59:59.999999Z',
    });
    const value = (id: string) =>
      element.querySelector<HTMLSelectElement | HTMLInputElement>(`#${id}`)!.value;
    expect(value('rules-status')).toBe('true');
    expect(value('rules-short-circuit')).toBe('false');
    expect(value('rules-created-from')).toBe('2026-11-01');
    expect(value('rules-created-to')).toBe('2026-11-30');
    expect(value('rules-sort')).toBe('priority,desc');
    expect(element.querySelector('[data-testid="rules-count"]')!.textContent).toContain(
      'match the filters',
    );
  });

  it('puts a changed filter in the URL and goes back to the first page', async () => {
    await render({ page: '3', sort: 'priority,desc' }, pageOf([summer], 3));

    change('rules-status', 'false');

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: {
          page: null,
          sort: 'priority,desc',
          active: 'false',
          shortCircuit: null,
          createdFrom: null,
          createdTo: null,
        },
        queryParamsHandling: 'merge',
      }),
    );
  });

  it('puts a changed sort and day in the URL', async () => {
    await render({}, pageOf([summer]));

    change('rules-sort', 'updatedAt,desc');
    change('rules-created-from', '2026-11-05');

    expect(
      navigate.mock.calls.map(
        (call: unknown[]) => (call[1] as { queryParams: unknown }).queryParams,
      ),
    ).toEqual([
      expect.objectContaining({ sort: 'updatedAt,desc', createdFrom: null }),
      expect.objectContaining({ sort: null, createdFrom: '2026-11-05' }),
    ]);
  });

  it('says when no rules match the filters, and clears them', async () => {
    await render({ active: 'false', createdFrom: '2026-11-01' }, pageOf([]));

    expect(element.textContent).toContain('No rules match these filters.');
    const clear = [...element.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === 'Clear filters',
    )!;
    clear.click();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: expect.objectContaining({ active: null, createdFrom: null, createdTo: null }),
      }),
    );
  });

  it('says when the storefront has no rules at all', async () => {
    await render({}, pageOf([]));

    expect(element.textContent).toContain('This storefront has no promotion rules yet.');
    expect(element.textContent).not.toContain('Clear filters');
    expect(element.textContent).not.toContain('Create the first one');
  });

  it('links each rule to its page', async () => {
    await render({}, pageOf([summer, freeShipping]));

    const links = [...element.querySelectorAll<HTMLAnchorElement>('tbody a')];
    expect(links.map((a) => [a.textContent!.trim(), a.getAttribute('href')])).toEqual([
      ['20% off keyboards with SUMMER', '/1'],
      ['Free shipping over 50', '/2'],
    ]);
  });

  it('offers New rule to promotion managers only', async () => {
    await render({}, pageOf([summer]));
    expect(element.querySelector('a[href="/new"]')).toBeNull();

    roles.set([Role.PromotionManager]);
    fixture.detectChanges();
    expect(element.querySelector('a[href="/new"]')!.textContent!.trim()).toBe('New rule');
  });

  it('warns when the created range is backwards', async () => {
    await render({ createdFrom: '2026-11-30', createdTo: '2026-11-01' }, pageOf([]));

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      '"Created from" is after "Created to"',
    );
  });

  it('shows a retry when the list fails to load', async () => {
    fixture = TestBed.createComponent(PromotionRuleListPage);
    fixture.componentRef.setInput('storefrontId', '1');
    TestBed.tick();
    http
      .expectOne((req) => req.url === listUrl)
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      "Couldn't load the promotion rules.",
    );
  });
});
