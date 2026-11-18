import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { API_BASE_URL } from '../../../infrastructure/http';
import { PromotionClient } from '../promotion.client';
import { PromotionRuleDetails, SavedPromotionRule } from '../promotion.model';
import { PromotionRuleDetailsPage } from './promotion-rule-details.page';

describe('PromotionRuleDetailsPage', () => {
  const rule: PromotionRuleDetails = {
    id: 5,
    storefrontId: 1,
    name: '20% off keyboards with SUMMER',
    priority: 100,
    isShortCircuit: true,
    isActive: true,
    hasUsageLimit: true,
    usageLimit: 500,
    remainingUses: 312,
    conditionTree: {
      node: {
        type: 'COMPOSITE',
        operator: 'AND',
        children: [
          { type: 'PROMO_CODE', code: 'SUMMER', operator: 'EQUALS' },
          {
            type: 'COMPOSITE',
            operator: 'OR',
            children: [
              { type: 'CUSTOMER_AUTHENTICATION_STATE', operator: 'EQUALS' },
              {
                type: 'SUBTOTAL',
                threshold: 100,
                operator: 'GREATER_THAN_OR_EQUAL',
                subtotalType: 'GROSS',
              },
            ],
          },
        ],
      },
      spec: {
        displayName: 'Condition Group',
        description: '',
        supportedOperators: [],
        operatorsDisplayName: {},
      },
    },
    action: {
      action: {
        type: 'PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION',
        targetProductExternalId: 'PROD-KB-01',
        discountPercentage: 0.2,
      },
      spec: { displayName: 'Discount on specific Product', description: '' },
    },
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-12T09:00:00Z',
    products: [
      {
        id: 7,
        externalId: 'PROD-KB-01',
        sku: 'SKU-KB-01',
        name: 'Mechanical keyboard',
        storefrontId: 1,
        categoryIds: [2],
      },
    ],
  };
  const ruleUrl = 'http://api.test/api/promotion/5';
  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };

  let fixture: ComponentFixture<PromotionRuleDetailsPage>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

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

  async function render(details: PromotionRuleDetails = rule): Promise<void> {
    fixture = TestBed.createComponent(PromotionRuleDetailsPage);
    fixture.componentRef.setInput('storefrontId', '1');
    fixture.componentRef.setInput('ruleId', '5');
    TestBed.tick();
    http.expectOne(ruleUrl).flush(details);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function text(selector: string): string[] {
    return [...element.querySelectorAll(selector)].map((node) =>
      node.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  function click(label: string): void {
    const button = [...element.querySelectorAll('button')].find(
      (candidate) => candidate.textContent!.trim() === label,
    );
    button!.click();
  }

  it('draws the rule as IF → THEN, with its products linked', async () => {
    await render();

    expect(element.querySelector('h2')!.textContent).toContain('20% off keyboards with SUMMER');
    expect(text('[data-testid="condition-text"]')).toEqual([
      'Promo code is SUMMER',
      'Customer is signed in',
      'Cart subtotal (before discounts) is at least 100',
    ]);
    const groups = [...element.querySelectorAll('[data-testid="condition-group"]')];
    expect(groups.map((group) => group.getAttribute('aria-label'))).toEqual([
      'All of these conditions',
      'Any of these conditions',
    ]);
    expect(text('[data-testid="action-headline"]')).toEqual(['20% off']);
    expect(text('[data-testid="action-target"]')).toEqual([
      'every line of product Mechanical keyboard PROD-KB-01',
    ]);
    expect(element.querySelector('[data-testid="action-target"] a')!.getAttribute('href')).toBe(
      '/products/7',
    );
    expect(text('[data-testid="rule-products"] li')).toEqual([
      'Mechanical keyboard PROD-KB-01 · SKU SKU-KB-01',
    ]);
  });

  it('shows the uses left against the limit, or that there is none', async () => {
    await render();
    expect(text('[data-testid="usage-remaining"]')).toEqual(['312 of 500 uses left']);
    expect(element.querySelector('[role="meter"]')!.getAttribute('aria-valuenow')).toBe('312');

    fixture.destroy();
    await render({ ...rule, hasUsageLimit: false });
    expect(text('[data-testid="usage-remaining"]')).toEqual(['Unlimited']);
  });

  it("treats another storefront's rule as missing", async () => {
    await render({ ...rule, storefrontId: 2 });

    expect(element.textContent).toContain("This rule doesn't exist in this storefront.");
  });

  it('offers changes to promotion managers only', async () => {
    roles.set([Role.Auditor]);
    await render();
    expect(element.querySelector('a[href="/edit"]')).toBeNull();
    expect(text('button')).not.toContain('Delete');
    expect(text('button')).not.toContain('Change');

    roles.set([Role.PromotionManager]);
    fixture.detectChanges();
    expect(element.querySelector('a[href="/edit"]')!.textContent).toContain('Edit rule');
    expect(text('button')).toEqual(expect.arrayContaining(['Delete', 'Change']));
  });

  it('deletes the rule once confirmed, then goes back to the list', async () => {
    roles.set([Role.PromotionManager]);
    await render();

    click('Delete');
    fixture.detectChanges();
    expect(element.querySelector('dialog')!.textContent).toContain('Delete this rule?');
    click('Delete rule');
    const request = http.expectOne(ruleUrl);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(
      ['..'],
      expect.objectContaining({ relativeTo: expect.anything() }),
    );
  });

  it('sets the uses left and shows the new count', async () => {
    roles.set([Role.PromotionManager]);
    await render();

    click('Change');
    fixture.detectChanges();
    await fixture.whenStable();
    const dialog = element.querySelector('dialog')!;
    dialog.querySelector<HTMLInputElement>('input[value="set"]')!.click();
    fixture.detectChanges();
    const remaining = dialog.querySelector<HTMLInputElement>('#usage-remaining')!;
    remaining.value = '1000';
    remaining.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    click('Save');
    fixture.detectChanges();

    // Above the total of 500, so it isn't sent
    http.expectNone(`${ruleUrl}/usage-limits`);
    expect(dialog.textContent).toContain('At most the total uses (500).');

    remaining.value = '450';
    remaining.dispatchEvent(new Event('input'));
    click('Save');
    const request = http.expectOne(`${ruleUrl}/usage-limits`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      hasUsageLimit: true,
      usageLimit: 500,
      mutateRemainingUses: true,
      remainingUses: 450,
    });
    const saved: SavedPromotionRule = {
      ...rule,
      remainingUses: 450,
      productExternalIds: ['PROD-KB-01'],
    };
    request.flush(saved);
    await fixture.whenStable();

    expect(element.querySelector('dialog')).toBeNull();
    expect(text('[data-testid="usage-remaining"]')).toEqual(['450 of 500 uses left']);
    // The products are kept from the details
    expect(text('[data-testid="rule-products"] li')).toHaveLength(1);
  });

  it('turns the limit off, keeping the numbers', async () => {
    roles.set([Role.PromotionManager]);
    await render();

    click('Change');
    fixture.detectChanges();
    await fixture.whenStable();
    element.querySelector<HTMLInputElement>('#usage-has-limit')!.click();
    fixture.detectChanges();
    click('Save');

    expect(http.expectOne(`${ruleUrl}/usage-limits`).request.body).toEqual({
      hasUsageLimit: false,
      usageLimit: 500,
      mutateRemainingUses: false,
    });
  });
});
