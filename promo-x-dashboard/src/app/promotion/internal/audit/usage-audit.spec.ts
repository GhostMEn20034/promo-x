import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { PromotionClient } from '../promotion.client';
import { UsageAuditItem } from '../promotion.model';
import { RuleEvaluations } from './rule-evaluations';
import { UsageAuditPage } from './usage-audit.page';

const AUDIT_URL = 'http://api.test/api/promotion/usage/audit/';

const reserved: UsageAuditItem = {
  usageId: '5cbc1006-9c90-4252-b39e-d9511bc88cc3',
  status: 'RESERVED',
  sessionId: 'cart-7f3a',
  customerId: 'customer-42',
  promoCodes: ['SUMMER', 'VIP'],
  subtotalBefore: 225,
  subtotalAfter: 185,
  shippingCostBefore: 10,
  shippingCostAfter: 0,
  totalBefore: 235,
  totalAfter: 185,
  matchedRules: [
    { ruleId: 2, ruleName: 'Free shipping' },
    { ruleId: 5, ruleName: '20% off keyboards' },
    { ruleId: 9, ruleName: 'Free mouse, used up' },
  ],
  appliedRules: [
    { ruleId: 5, ruleName: '20% off keyboards' },
    { ruleId: 2, ruleName: 'Free shipping' },
  ],
  createdAt: '2026-11-10T21:35:46Z',
};
const guest: UsageAuditItem = {
  ...reserved,
  usageId: '0d6c5b0e-6a4e-4a8b-9a39-7c2d1f0e8b11',
  status: 'USED',
  sessionId: null,
  customerId: null,
  promoCodes: [],
  totalBefore: 50,
  totalAfter: 50,
  matchedRules: [{ ruleId: 2, ruleName: 'Free shipping' }],
  appliedRules: [{ ruleId: 2, ruleName: 'Free shipping' }],
};

function pageOf(content: UsageAuditItem[], totalPages = 1): Page<UsageAuditItem> {
  return {
    content,
    number: 0,
    size: 20,
    numberOfElements: content.length,
    totalElements: totalPages * 20 > content.length ? content.length : totalPages * 20,
    totalPages,
    first: true,
    last: totalPages === 1,
  };
}

function paramsOf(request: TestRequest): Record<string, string[]> {
  const params = request.request.params;
  return Object.fromEntries(params.keys().map((key) => [key, params.getAll(key) ?? []]));
}

function clean(node: Element): string {
  return node.textContent!.replace(/\s+/g, ' ').trim();
}

describe('usage audit', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        PromotionClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('UsageAuditPage', () => {
    let fixture: ComponentFixture<UsageAuditPage>;
    let element: HTMLElement;

    async function render(
      page: Page<UsageAuditItem> | { status: number },
      pageParam?: string,
    ): Promise<TestRequest> {
      fixture = TestBed.createComponent(UsageAuditPage);
      fixture.componentRef.setInput('storefrontId', '1');
      if (pageParam) {
        fixture.componentRef.setInput('page', pageParam);
      }
      TestBed.tick();
      const request = http.expectOne((req) => req.url === AUDIT_URL);
      if ('status' in page) {
        request.flush(null, { status: page.status, statusText: 'Error' });
      } else {
        request.flush(page);
      }
      await fixture.whenStable();
      element = fixture.nativeElement as HTMLElement;
      return request;
    }

    function rows(): HTMLElement[] {
      return [...element.querySelectorAll<HTMLElement>('[data-testid="audit-row"]')];
    }

    it("asks for the storefront's evaluations without a filter", async () => {
      const request = await render(pageOf([reserved, guest]));

      expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
      expect(paramsOf(request)).toEqual({ page: ['1'], size: ['20'] });
      expect(rows()).toHaveLength(2);
    });

    it('shows each evaluation: status, customer, codes, rules in their order, and totals', async () => {
      await render(pageOf([reserved, guest]));
      const [first, second] = rows();
      const cells = (row: HTMLElement) => [...row.querySelectorAll('td')].map(clean);

      expect(cells(first).slice(1, 3)).toEqual(['Reserved', 'customer-42']);
      expect([...first.querySelectorAll('td:nth-child(4) code')].map(clean)).toEqual([
        'SUMMER',
        'VIP',
      ]);
      const rules = first.querySelector('[data-testid="audit-rules"]')!;
      const [applied, matchedOnly] = [...rules.children];
      // Applied, in the order they were applied, joined by an arrow
      expect([...applied.querySelectorAll('a')].map(clean)).toEqual([
        '20% off keyboards',
        'Free shipping',
      ]);
      expect(applied.querySelectorAll('[aria-label="then"]')).toHaveLength(1);
      // Then the rule that matched but changed nothing
      expect([...matchedOnly.querySelectorAll('a')].map(clean)).toEqual(['Free mouse, used up']);
      expect(clean(matchedOnly)).toContain('Matched only:');
      expect(second.querySelector('[data-testid="audit-rules"]')!.children).toHaveLength(1);
      // What was paid, the struck-through total before, and the discount, each named for screen readers
      expect([...first.querySelectorAll('td:nth-child(6) > span')].map(clean)).toEqual([
        '185.00',
        'Before: 235.00',
        'Discount: −50.00',
      ]);
      expect(cells(second).slice(1, 4)).toEqual(['Used', 'Guest', '—']);
      // No discount: no struck-through total
      expect(cells(second)[5]).toBe('50.00');
    });

    it('links the rules to their pages', async () => {
      await render(pageOf([reserved]));

      const links = [...element.querySelectorAll('[data-testid="audit-rules"] a')];
      expect(links.map((a) => a.getAttribute('href'))).toEqual([
        '/promotion-rules/5',
        '/promotion-rules/2',
        '/promotion-rules/9',
      ]);
    });

    it('opens a row to its breakdown', async () => {
      await render(pageOf([reserved]));
      expect(element.querySelector('[data-testid="audit-details"]')).toBeNull();

      const toggle = rows()[0].querySelector<HTMLButtonElement>('button[aria-expanded]')!;
      toggle.click();
      fixture.detectChanges();

      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      const details = element.querySelector('[data-testid="audit-details"]')!;
      const terms = [...details.querySelectorAll('dt')].map(clean);
      const values = [...details.querySelectorAll('dd')].map(clean);
      expect(terms.map((term, i) => [term, values[i]])).toEqual([
        ['Items', '225.00 → 185.00'],
        ['Shipping', '10.00 → 0.00'],
        ['Usage', reserved.usageId],
        ['Session', 'cart-7f3a'],
      ]);
    });

    it('asks for the page in the URL', async () => {
      const request = await render(pageOf([guest], 3), '2');

      expect(paramsOf(request)['page']).toEqual(['2']);
      expect(element.querySelector('app-pager nav a')).toBeTruthy();
    });

    it('says when there are no evaluations', async () => {
      await render(pageOf([]));

      expect(clean(element.querySelector('[data-testid="audit-empty"]')!)).toBe(
        'No evaluation has applied a rule yet.',
      );
    });

    it('says when the user may not see the evaluations', async () => {
      await render({ status: 403 });

      expect(element.textContent).toContain("You're not allowed to see the evaluations.");
    });
  });

  describe('RuleEvaluations', () => {
    let fixture: ComponentFixture<RuleEvaluations>;
    let element: HTMLElement;

    beforeEach(async () => {
      fixture = TestBed.createComponent(RuleEvaluations);
      fixture.componentRef.setInput('storefrontId', 1);
      fixture.componentRef.setInput('ruleId', 5);
      await fixture.whenStable();
      element = fixture.nativeElement as HTMLElement;
    });

    function click(label: string): void {
      [...element.querySelectorAll<HTMLButtonElement>('button')]
        .find((button) => clean(button) === label)!
        .click();
      fixture.detectChanges();
    }

    async function answer(page: Page<UsageAuditItem>): Promise<TestRequest> {
      TestBed.tick();
      const request = http.expectOne((req) => req.url === AUDIT_URL);
      request.flush(page);
      await fixture.whenStable();
      return request;
    }

    it("loads nothing until it's opened, then the evaluations that applied the rule", async () => {
      TestBed.tick();
      http.expectNone((req) => req.url === AUDIT_URL);
      expect(element.querySelector('app-usage-audit-list')).toBeNull();

      click('Show evaluations');
      const request = await answer(pageOf([reserved]));

      expect(paramsOf(request)).toEqual({ page: ['1'], size: ['20'], appliedRuleIds: ['5'] });
      expect(
        element.querySelector('[role="radio"][aria-checked="true"]')!.textContent!.trim(),
      ).toBe('Applied it');
      // The rule itself stands out among the others
      const highlighted = [...element.querySelectorAll('[data-testid="audit-rules"] a.ring-2')];
      expect(highlighted.map(clean)).toEqual(['20% off keyboards']);
      expect(highlighted[0].getAttribute('href')).toBe('/5');
    });

    it('switches to the evaluations that matched the rule, from the first page', async () => {
      click('Show evaluations');
      await answer(pageOf([reserved], 3));
      element.querySelector<HTMLButtonElement>('app-pager nav button:not([aria-current])')!.click();
      fixture.detectChanges();
      const second = await answer(pageOf([reserved], 3));
      expect(paramsOf(second)['page']).toEqual(['2']);

      click('Matched it');
      const matched = await answer(pageOf([]));

      expect(paramsOf(matched)).toEqual({ page: ['1'], size: ['20'], matchedRuleIds: ['5'] });
      expect(clean(element.querySelector('[data-testid="audit-empty"]')!)).toBe(
        'No evaluation has matched this rule yet.',
      );
    });

    it('closes again', async () => {
      click('Show evaluations');
      await answer(pageOf([reserved]));

      click('Hide');

      expect(element.querySelector('app-usage-audit-list')).toBeNull();
    });
  });
});
