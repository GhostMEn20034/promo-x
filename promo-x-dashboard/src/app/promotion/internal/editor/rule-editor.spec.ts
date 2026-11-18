import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { Category } from '../../../category';
import { Product } from '../../../product';
import { PromotionClient } from '../promotion.client';
import { PromotionRuleDetails, SavedPromotionRule } from '../promotion.model';
import { RuleEditor } from './rule-editor';

describe('RuleEditor', () => {
  const createUrl = 'http://api.test/api/promotion/';
  const existing: PromotionRuleDetails = {
    id: 5,
    storefrontId: 1,
    name: 'Free shipping for members',
    priority: 10,
    isShortCircuit: false,
    isActive: true,
    hasUsageLimit: false,
    usageLimit: 1,
    remainingUses: 1,
    conditionTree: {
      node: {
        type: 'COMPOSITE',
        operator: 'OR',
        children: [
          {
            type: 'CUSTOMER_AUTHENTICATION_STATE',
            operator: 'EQUALS',
            spec: { displayName: 'Customer Authentication Status' },
          },
          {
            type: 'PRODUCT_CATEGORY_ANY_MATCH',
            categories: ['CAT-ELEC'],
            operator: 'EQUALS',
            spec: { displayName: 'Product Category List Match' },
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
      action: { type: 'FREE_SHIPPING_ACTION' },
      spec: { displayName: 'Free Shipping', description: '' },
    },
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-10T21:35:46Z',
    products: [],
  };

  let fixture: ComponentFixture<RuleEditor>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let saved: SavedPromotionRule[];

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

  async function render(rule: PromotionRuleDetails | null = null): Promise<void> {
    fixture = TestBed.createComponent(RuleEditor);
    fixture.componentRef.setInput('storefrontId', 1);
    fixture.componentRef.setInput('rule', rule);
    saved = [];
    fixture.componentInstance.saved.subscribe((rule) => saved.push(rule));
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function addCondition(type: string, group = 0): Promise<HTMLElement> {
    element.querySelectorAll<HTMLButtonElement>('[data-testid="add-condition"]')[group].click();
    fixture.detectChanges();
    await fixture.whenStable();
    element.querySelector<HTMLButtonElement>(`dialog button[data-type="${type}"]`)!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const editors = element.querySelectorAll<HTMLElement>(
      `[data-testid="condition-editor"][data-type="${type}"]`,
    );
    return editors[editors.length - 1];
  }

  function productNamed(id: number, externalId: string, name: string): Product {
    return {
      id,
      externalId,
      sku: `SKU-${id}`,
      name,
      storefrontId: 1,
      createdAt: '2026-11-10T21:35:46Z',
      updatedAt: '2026-11-10T21:35:46Z',
      categories: [],
    };
  }

  /** Answers the product picker's request with these products. */
  async function listProducts(products: Product[]): Promise<void> {
    const request = http.expectOne((req) => req.url === 'http://api.test/api/product/');
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
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
    request.flush(page);
    await fixture.whenStable();
  }

  function categoryNamed(id: number, externalId: string | null, name: string): Category {
    return { id, externalId, name, parent: null, storefrontId: 1 };
  }

  /** Answers the category picker's request with these categories. */
  async function listCategories(categories: Category[]): Promise<void> {
    const request = http.expectOne((req) => req.url === 'http://api.test/api/category/');
    const page: Page<Category> = {
      content: categories,
      number: 0,
      size: 10,
      numberOfElements: categories.length,
      totalElements: categories.length,
      totalPages: 1,
      first: true,
      last: true,
    };
    request.flush(page);
    await fixture.whenStable();
  }

  function clickButton(within: Element, label: string): void {
    [...within.querySelectorAll<HTMLButtonElement>('button')]
      .find((button) => button.textContent!.trim() === label)!
      .click();
    fixture.detectChanges();
  }

  function chooseAction(type: string): void {
    element
      .querySelector<HTMLInputElement>(`input[name="rule-action-type"][value="${type}"]`)!
      .click();
    fixture.detectChanges();
  }

  function submit(): void {
    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    fixture.detectChanges();
  }

  function previewText(): string[] {
    return [...element.querySelectorAll('aside [data-testid="condition-text"]')].map((node) =>
      node.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('builds a rule and creates it', async () => {
    await render();
    type(element.querySelector('#rule-name')!, '  15% off with SUMMER ');
    type(element.querySelector('#rule-priority')!, '100');

    const code = await addCondition('PROMO_CODE');
    type(code.querySelector('input')!, 'SUMMER');
    const subtotal = await addCondition('SUBTOTAL');
    type(subtotal.querySelector('input')!, '50');
    element.querySelector<HTMLButtonElement>('[data-testid="match-any"]')!.click();
    chooseAction('ORDER_PERCENTAGE_DISCOUNT');
    type(element.querySelector('#rule-action-discountPercentage')!, '15');
    element.querySelector<HTMLInputElement>('#rule-has-usage-limit')!.click();
    fixture.detectChanges();
    type(element.querySelector('#rule-usage-limit')!, '500');

    expect(previewText()).toEqual([
      'Promo code is SUMMER',
      'Cart subtotal (before discounts) is at least 50',
    ]);
    expect(element.querySelector('aside [data-testid="action-headline"]')!.textContent).toContain(
      '15% off',
    );

    submit();
    const request = http.expectOne(createUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    expect(request.request.body).toEqual({
      name: '15% off with SUMMER',
      priority: 100,
      isShortCircuit: false,
      isActive: true,
      hasUsageLimit: true,
      usageLimit: 500,
      conditionTree: {
        type: 'COMPOSITE',
        operator: 'OR',
        children: [
          { type: 'PROMO_CODE', operator: 'EQUALS', code: 'SUMMER' },
          {
            type: 'SUBTOTAL',
            operator: 'GREATER_THAN_OR_EQUAL',
            threshold: 50,
            subtotalType: 'GROSS',
          },
        ],
      },
      action: { type: 'ORDER_PERCENTAGE_DISCOUNT', discountPercentage: 0.15 },
    });
    const response = {
      ...existing,
      id: 9,
      productExternalIds: [],
    } as unknown as SavedPromotionRule;
    request.flush(response);
    await fixture.whenStable();

    expect(saved).toEqual([response]);
  });

  it('sends a single condition on its own, and no usage limit when there is none', async () => {
    await render();
    type(element.querySelector('#rule-name')!, 'Guests');
    type(element.querySelector('#rule-priority')!, '1');
    await addCondition('CUSTOMER_AUTHENTICATION_STATE');
    chooseAction('FREE_SHIPPING_ACTION');

    submit();
    const body = http.expectOne(createUrl).request.body;
    expect(body.conditionTree).toEqual({
      type: 'CUSTOMER_AUTHENTICATION_STATE',
      operator: 'EQUALS',
    });
    expect(body.action).toEqual({ type: 'FREE_SHIPPING_ACTION' });
    expect(body.hasUsageLimit).toBe(false);
    expect('usageLimit' in body).toBe(false);
  });

  it('shows what is missing instead of sending', async () => {
    await render();
    submit();

    http.expectNone(createUrl);
    expect(element.textContent).toContain('Name is required.');
    expect(element.querySelector('[data-testid="group-error"]')!.textContent).toContain(
      'Add at least one condition.',
    );
    expect(element.querySelector('[data-testid="action-error"]')!.textContent).toContain(
      'Choose what the rule does.',
    );
    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'Some fields need attention.',
    );

    const quantity = await addCondition('PRODUCT_SKU_QUANTITY');
    const [sku, amount] = quantity.querySelectorAll('input');
    type(sku, 'SKU-1');
    type(amount, '0');
    expect(quantity.textContent).toContain('A whole number, 1 or more.');
  });

  it('nests a group that combines the other way', async () => {
    await render();
    element.querySelector<HTMLButtonElement>('[data-testid="add-group"]')!.click();
    fixture.detectChanges();
    // The nested group's button comes first, before the top group's own
    await addCondition('SINGLE_PRODUCT_SKU_MATCH', 0);

    const groups = element.querySelectorAll('aside [data-testid="condition-group"]');
    // The top group has a single child, the nested group, which is drawn on its own
    expect([...groups].map((group) => group.getAttribute('aria-label'))).toEqual([
      'Any of these conditions',
    ]);
    expect(previewText()).toEqual(['Cart contains SKU SKU']);
  });

  it('marks the condition holding a product external ID that does not exist', async () => {
    await render();
    type(element.querySelector('#rule-name')!, 'Keyboard');
    type(element.querySelector('#rule-priority')!, '5');
    const product = await addCondition('SINGLE_PRODUCT_EXTERNAL_ID_MATCH');
    clickButton(product, 'Choose');
    TestBed.tick();
    await listProducts([productNamed(4, 'P-404', 'Old keyboard')]);
    clickButton(element.querySelector('dialog')!, 'Choose');
    chooseAction('FREE_SHIPPING_ACTION');

    submit();
    http.expectOne(createUrl).flush(
      {
        type: 'https://api.example.com/problems/business-rule-violation',
        title: 'Business rule violation',
        status: 409,
        detail: 'Validation failed: specified product external IDs do not exist',
        instance: '/api/promotion/',
        code: 'PRODUCT_EXTERNAL_IDS_NOT_FOUND',
        timestamp: '2026-11-10T21:35:47Z',
        errors: [
          {
            category: 'BUSINESS',
            source: 'DOMAIN',
            path: null,
            code: 'PRODUCT_EXTERNAL_IDS_NOT_FOUND',
            message: 'One or more product external IDs do not exist',
            errorContext: { missingProductExternalIds: ['P-404'], existingProductExternalIds: [] },
          },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    const editor = element.querySelector('[data-testid="condition-editor"]')!;
    expect(editor.textContent).toContain('Not in this storefront: P-404.');
    expect(saved).toEqual([]);

    // Changing the value clears it
    clickButton(editor, 'Remove');
    expect(editor.textContent).not.toContain('Not in this storefront');
  });

  it('chooses products from the list instead of typing their external IDs', async () => {
    await render();
    type(element.querySelector('#rule-name')!, 'Bundle');
    type(element.querySelector('#rule-priority')!, '5');
    const condition = await addCondition('PRODUCT_EXTERNAL_ID_ANY_MATCH');
    expect(condition.querySelector('input')).toBeNull();
    expect(condition.querySelector('[data-testid="product-field"]')!.textContent).toContain(
      'No products chosen',
    );

    clickButton(condition, 'Choose products');
    TestBed.tick();
    await listProducts([
      productNamed(1, 'PROD-KB-01', 'Mechanical keyboard'),
      productNamed(2, 'PROD-MS-01', 'Mouse'),
    ]);
    const dialog = element.querySelector('dialog')!;
    dialog
      .querySelectorAll<HTMLInputElement>('input[type="checkbox"]')
      .forEach((box) => box.click());
    fixture.detectChanges();
    clickButton(dialog, 'Done');

    const chips = () =>
      [...condition.querySelectorAll('[data-testid="product-chip"]')].map((chip) =>
        chip.textContent!.replace(/\s+/g, ' ').trim(),
      );
    expect(chips()).toEqual(['Mechanical keyboard PROD-KB-01', 'Mouse PROD-MS-01']);
    expect(previewText()).toEqual([
      'Cart contains any of the products Mechanical keyboard PROD-KB-01 Mouse PROD-MS-01',
    ]);

    clickButton(condition.querySelector('[data-testid="product-chip"]')!, '');
    expect(chips()).toEqual(['Mouse PROD-MS-01']);

    // The action's product is chosen the same way
    chooseAction('PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION');
    const actionField = element.querySelectorAll('[data-testid="product-field"]')[1];
    clickButton(actionField, 'Choose');
    TestBed.tick();
    await listProducts([productNamed(2, 'PROD-MS-01', 'Mouse')]);
    clickButton(element.querySelector('dialog')!, 'Choose');
    expect(actionField.textContent).toContain('Mouse');
    type(element.querySelector('#rule-action-discountPercentage')!, '10');

    submit();
    const body = http.expectOne(createUrl).request.body;
    expect(body.conditionTree).toEqual({
      type: 'PRODUCT_EXTERNAL_ID_ANY_MATCH',
      operator: 'EQUALS',
      productExternalIds: ['PROD-MS-01'],
    });
    expect(body.action).toEqual({
      type: 'PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION',
      targetProductExternalId: 'PROD-MS-01',
      discountPercentage: 0.1,
    });
  });

  it('chooses a category and sends its external ID', async () => {
    await render();
    type(element.querySelector('#rule-name')!, 'Electronics');
    type(element.querySelector('#rule-priority')!, '5');
    const condition = await addCondition('SINGLE_PRODUCT_CATEGORY_MATCH');
    expect(condition.querySelector('input')).toBeNull();

    clickButton(condition, 'Choose');
    TestBed.tick();
    await listCategories([
      categoryNamed(1, 'CAT-ELEC', 'Electronics'),
      categoryNamed(2, null, 'Drafts'),
    ]);
    const dialog = element.querySelector('dialog')!;
    expect(dialog.textContent).toContain('No external ID');
    clickButton(dialog, 'Choose');

    const field = condition.querySelector('[data-testid="category-field"]')!;
    expect(field.textContent!.replace(/\s+/g, ' ')).toContain('Electronics ID 1 · CAT-ELEC');
    expect(previewText()).toEqual(['Cart contains a product from category Electronics CAT-ELEC']);

    chooseAction('FREE_SHIPPING_ACTION');
    submit();
    expect(http.expectOne(createUrl).request.body.conditionTree).toEqual({
      type: 'SINGLE_PRODUCT_CATEGORY_MATCH',
      operator: 'EQUALS',
      category: 'CAT-ELEC',
    });
  });

  it("adds picked categories to a rule's, keeping the ones it already had", async () => {
    await render(existing);
    const condition = element.querySelector('[data-type="PRODUCT_CATEGORY_ANY_MATCH"]')!;
    const chips = () =>
      [...condition.querySelectorAll('[data-testid="category-chip"]')].map((chip) =>
        chip.textContent!.replace(/\s+/g, ' ').trim(),
      );
    // The rule only has the external ID, so there's no name to show
    expect(chips()).toEqual(['CAT-ELEC']);

    clickButton(condition, 'Choose categories');
    TestBed.tick();
    await listCategories([categoryNamed(3, 'CAT-AUDIO', 'Audio')]);
    const dialog = element.querySelector('dialog')!;
    dialog.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    fixture.detectChanges();
    clickButton(dialog, 'Done');

    expect(chips()).toEqual(['Audio CAT-AUDIO', 'CAT-ELEC']);

    clickButton(condition.querySelectorAll('[data-testid="category-chip"]')[1], '');
    submit();
    expect(http.expectOne('http://api.test/api/promotion/5').request.body.conditionTree).toEqual({
      type: 'COMPOSITE',
      operator: 'OR',
      children: [
        { type: 'CUSTOMER_AUTHENTICATION_STATE', operator: 'EQUALS' },
        { type: 'PRODUCT_CATEGORY_ANY_MATCH', operator: 'EQUALS', categories: ['CAT-AUDIO'] },
      ],
    });
  });

  it('says a product must be chosen', async () => {
    await render();
    const condition = await addCondition('SINGLE_PRODUCT_EXTERNAL_ID_MATCH');
    submit();

    expect(condition.textContent).toContain('Choose a product.');
  });

  it('loads a rule and saves it back as it was', async () => {
    await render(existing);

    expect(element.querySelector<HTMLInputElement>('#rule-name')!.value).toBe(
      'Free shipping for members',
    );
    expect(element.querySelector('#rule-usage-limit')).toBeNull();
    expect(previewText()).toEqual([
      'Customer is signed in',
      'Cart contains a product from any of the categories CAT-ELEC',
    ]);

    submit();
    const request = http.expectOne('http://api.test/api/promotion/5');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      name: 'Free shipping for members',
      priority: 10,
      isShortCircuit: false,
      isActive: true,
      conditionTree: {
        type: 'COMPOSITE',
        operator: 'OR',
        children: [
          { type: 'CUSTOMER_AUTHENTICATION_STATE', operator: 'EQUALS' },
          { type: 'PRODUCT_CATEGORY_ANY_MATCH', operator: 'EQUALS', categories: ['CAT-ELEC'] },
        ],
      },
      action: { type: 'FREE_SHIPPING_ACTION' },
    });
  });
});
