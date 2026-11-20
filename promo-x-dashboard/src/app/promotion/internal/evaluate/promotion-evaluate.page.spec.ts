import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { Product } from '../../../product';
import { PromotionClient } from '../promotion.client';
import { EvaluationResult } from '../promotion.model';
import { PromotionEvaluatePage } from './promotion-evaluate.page';

describe('PromotionEvaluatePage', () => {
  const evaluateUrl = 'http://api.test/api/promotion/evaluate';
  const keyboard: Product = {
    id: 1,
    externalId: 'PROD-KB-01',
    sku: 'SKU-KB-01',
    name: 'Mechanical keyboard',
    storefrontId: 1,
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-10T21:35:46Z',
    categories: [],
  };
  const mouse: Product = {
    ...keyboard,
    id: 2,
    externalId: 'PROD-MS-01',
    sku: 'SKU-MS-01',
    name: 'Mouse',
  };
  const result: EvaluationResult = {
    sessionId: null,
    customerId: 'customer-42',
    promoCodes: ['SUMMER'],
    oldTotal: 235,
    newTotal: 195,
    totalDiscount: 40,
    itemsSubtotal: 225,
    itemsDiscount: 40,
    itemsTotal: 185,
    oldShippingCost: 10,
    shippingDiscount: 0,
    newShippingCost: 10,
    items: [
      {
        productExternalId: 'PROD-KB-01',
        productSku: 'SKU-KB-01',
        categories: ['CAT-KEYBOARDS'],
        collections: [],
        quantity: 2,
        unitPrice: 100,
        priceBeforeDiscount: 200,
        discountAmount: 40,
        priceAfterDiscount: 160,
      },
      {
        productExternalId: 'PROD-MS-01',
        productSku: 'SKU-MS-01',
        categories: [],
        collections: [],
        quantity: 1,
        unitPrice: 25,
        priceBeforeDiscount: 25,
        discountAmount: 0,
        priceAfterDiscount: 25,
      },
    ],
    matchedRules: [
      { ruleId: 1, ruleName: '20% off keyboards with SUMMER' },
      { ruleId: 2, ruleName: 'Free mouse, used up' },
    ],
    appliedRules: [{ ruleId: 1, ruleName: '20% off keyboards with SUMMER' }],
    usage: { id: '5cbc1006-9c90-4252-b39e-d9511bc88cc3', status: 'USED', expiresAt: null },
  };

  let fixture: ComponentFixture<PromotionEvaluatePage>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test/' },
        PromotionClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PromotionEvaluatePage);
    fixture.componentRef.setInput('storefrontId', '1');
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function clickButton(label: string, within: Element = element): void {
    [...within.querySelectorAll<HTMLButtonElement>('button')]
      .find((button) => button.textContent!.trim() === label)!
      .click();
    fixture.detectChanges();
  }

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function text(selector: string): string {
    return element.querySelector(selector)!.textContent!.replace(/\s+/g, ' ').trim();
  }

  async function chooseProducts(products: Product[]): Promise<void> {
    clickButton('Choose products');
    TestBed.tick();
    const page: Page<Product> = {
      content: products,
      number: 0,
      size: 10,
      numberOfElements: products.length,
      totalElements: products.length,
      totalPages: 1,
      first: true,
      last: true,
    };
    http.expectOne((req) => req.url === 'http://api.test/api/product/').flush(page);
    await fixture.whenStable();
    const dialog = element.querySelector('dialog')!;
    dialog
      .querySelectorAll<HTMLInputElement>('input[type="checkbox"]')
      .forEach((box) => box.click());
    fixture.detectChanges();
    clickButton('Done', dialog);
    await fixture.whenStable();
  }

  function evaluate(): void {
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('documents the endpoint with the API URL and this storefront', () => {
    expect(text('[data-testid="docs-endpoint"]')).toBe(
      'POST http://api.test/api/promotion/evaluate',
    );
    const curl = element.querySelector('[data-testid="docs-curl"]')!.textContent!;
    expect(curl).toContain("curl -X POST 'http://api.test/api/promotion/evaluate'");
    expect(curl).toContain("-H 'X-Storefront-Id: 1'");
    expect(curl).toContain('"productExternalId": "YOUR-PRODUCT-ID"');
    expect(curl).toContain('"reserveUsage": false');
    expect(element.textContent).toContain('integration-client');
  });

  it('says what the cart is missing instead of sending it', () => {
    evaluate();

    http.expectNone(evaluateUrl);
    expect(text('[data-testid="cart-error"]')).toBe('Add at least one product.');
  });

  it('evaluates the cart and shows the prices and the rules', async () => {
    await chooseProducts([keyboard, mouse]);
    const prices = element.querySelectorAll<HTMLInputElement>('input[aria-label^="Unit price"]');
    type(element.querySelector('input[aria-label="Quantity of Mechanical keyboard"]')!, '2');
    type(prices[0], '100');
    type(prices[1], '25');
    type(element.querySelector('#cart-shipping')!, '10');
    const code = element.querySelector<HTMLInputElement>('#cart-promo-codes')!;
    type(code, 'SUMMER');
    code.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();
    type(element.querySelector('#cart-customer')!, 'customer-42');

    // The docs follow the cart
    expect(element.querySelector('[data-testid="docs-curl"]')!.textContent).toContain(
      '"PROD-MS-01"',
    );

    evaluate();
    const request = http.expectOne(evaluateUrl);
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    expect(request.request.body).toEqual({
      customerId: 'customer-42',
      promoCodes: ['SUMMER'],
      items: [
        { productExternalId: 'PROD-KB-01', quantity: 2, unitPrice: 100 },
        { productExternalId: 'PROD-MS-01', quantity: 1, unitPrice: 25 },
      ],
      shippingCost: 10,
      reserveUsage: false,
    });
    request.flush(result);
    await fixture.whenStable();

    expect(text('[data-testid="result-total"]')).toBe('195.00 235.00');
    expect(text('[data-testid="result-saved"]')).toBe('Saves 40.00 (17%)');
    const rules = [...element.querySelectorAll('[data-testid="result-rules"] li')].map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(rules).toEqual(['Applied 20% off keyboards with SUMMER', 'Matched Free mouse, used up']);
    expect(element.querySelector('[data-testid="result-rules"] a')!.getAttribute('href')).toBe(
      '/1',
    );
    expect(text('[data-testid="result-lines"]')).toContain('Mechanical keyboard × 2');
  });

  it('says which products are no longer in the storefront', async () => {
    await chooseProducts([keyboard]);
    type(element.querySelector('input[aria-label="Unit price of Mechanical keyboard"]')!, '100');

    evaluate();
    http.expectOne(evaluateUrl).flush(
      {
        code: 'PRODUCT_EXTERNAL_IDS_NOT_FOUND',
        detail: 'Validation failed: specified product external IDs do not exist',
        errors: [
          {
            path: 'items',
            code: 'PRODUCT_EXTERNAL_IDS_NOT_FOUND',
            message: 'One or more product external IDs do not exist',
            errorContext: { missingProductExternalIds: ['PROD-KB-01'] },
          },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(text('[role="alert"]')).toContain(
      'These products are no longer in the storefront: PROD-KB-01.',
    );
  });
});
