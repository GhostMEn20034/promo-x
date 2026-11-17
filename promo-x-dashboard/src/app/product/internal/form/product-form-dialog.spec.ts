import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Category } from '../../../category';
import { API_BASE_URL } from '../../../infrastructure/http';
import { ProductClient } from '../product.client';
import { Product, SavedProduct } from '../product.model';
import { ProductFormDialog } from './product-form-dialog';

describe('ProductFormDialog', () => {
  const electronics: Category = {
    id: 2,
    externalId: 'CAT-ELEC',
    name: 'Electronics',
    parent: null,
    storefrontId: 1,
  };
  const keyboards: Category = {
    id: 5,
    externalId: 'CAT-KB',
    name: 'Keyboards',
    parent: null,
    storefrontId: 1,
  };
  const product: Product = {
    id: 7,
    externalId: 'PROD-KB',
    sku: 'SKU-KB',
    name: 'Keyboard',
    storefrontId: 1,
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-10T21:35:46Z',
    categories: [electronics],
  };
  let fixture: ComponentFixture<ProductFormDialog>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let saved: ReturnType<typeof vi.fn<(value: Product) => void>>;

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  async function render(editing: Product | null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        ProductClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductFormDialog);
    fixture.componentRef.setInput('storefrontId', 1);
    fixture.componentRef.setInput('product', editing);
    saved = vi.fn<(value: Product) => void>();
    fixture.componentInstance.saved.subscribe(saved);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  function input(id: string): HTMLInputElement {
    return element.querySelector<HTMLInputElement>(`#${id}`)!;
  }

  async function type(id: string, value: string): Promise<void> {
    input(id).value = value;
    input(id).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function button(text: string, root: ParentNode = element): HTMLButtonElement {
    return [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === text)!;
  }

  function chips(): string[] {
    return [...element.querySelectorAll('[data-testid="category-chip"]')].map((chip) =>
      chip.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  function savedFrom(body: { categoryIds: number[] } & Partial<SavedProduct>): SavedProduct {
    return {
      id: 7,
      externalId: 'PROD-KB',
      sku: 'SKU-KB',
      name: 'Keyboard',
      storefrontId: 1,
      createdAt: '2026-11-10T21:35:46Z',
      updatedAt: '2026-11-11T09:00:00Z',
      ...body,
    };
  }

  describe('creating', () => {
    beforeEach(() => render(null));

    it('posts the trimmed product to the storefront and emits it', async () => {
      expect(element.querySelector('h2')!.textContent).toContain('New product');
      await type('product-name', '  Mouse  ');
      await type('product-sku', 'SKU-MS');
      await type('product-externalId', 'PROD-MS');
      await submit();

      const request = http.expectOne('http://api.test/api/product/');
      expect(request.request.method).toBe('POST');
      expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
      expect(request.request.body).toEqual({
        externalId: 'PROD-MS',
        sku: 'SKU-MS',
        name: 'Mouse',
        categoryIds: [],
      });

      request.flush(
        savedFrom({ id: 9, externalId: 'PROD-MS', sku: 'SKU-MS', name: 'Mouse', categoryIds: [] }),
      );
      expect(saved).toHaveBeenCalledWith(
        expect.objectContaining({ id: 9, name: 'Mouse', categories: [] }),
      );
    });

    it("doesn't send blank fields", async () => {
      await submit();

      http.expectNone('http://api.test/api/product/');
      expect(element.textContent).toContain('Name is required.');
      expect(element.textContent).toContain('SKU is required.');
      expect(element.textContent).toContain('External ID is required.');
    });

    it("shows the backend's errors under the external ID and the categories", async () => {
      await type('product-name', 'Mouse');
      await type('product-sku', 'SKU-MS');
      await type('product-externalId', 'PROD-TAKEN');
      await submit();

      http.expectOne('http://api.test/api/product/').flush(
        {
          code: 'EXTERNAL_ID_ALREADY_EXISTS',
          errors: [
            { path: 'externalId', code: 'EXTERNAL_ID_ALREADY_EXISTS', message: 'Already exists.' },
            { path: 'categoryIds', code: 'CATEGORY_IDS_NOT_FOUND', message: 'Unknown category.' },
          ],
        },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();

      expect(element.querySelector('#product-externalId-error')!.textContent).toContain(
        'Already exists.',
      );
      expect(element.querySelector('#product-categories-error')!.textContent).toContain(
        'Unknown category.',
      );
      expect(saved).not.toHaveBeenCalled();
    });
  });

  describe('editing', () => {
    beforeEach(() => render(product));

    it('opens filled with the product and its categories', () => {
      expect(element.querySelector('h2')!.textContent).toContain('Edit product');
      expect(input('product-name').value).toBe('Keyboard');
      expect(input('product-sku').value).toBe('SKU-KB');
      expect(input('product-externalId').value).toBe('PROD-KB');
      expect(chips()).toEqual(['Electronics #2']);
    });

    it('puts every field and emits the product with the chosen categories', async () => {
      await type('product-name', 'Mechanical keyboard');
      await submit();

      const request = http.expectOne('http://api.test/api/product/7');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({
        externalId: 'PROD-KB',
        sku: 'SKU-KB',
        name: 'Mechanical keyboard',
        categoryIds: [2],
      });

      request.flush(savedFrom({ name: 'Mechanical keyboard', categoryIds: [2] }));
      expect(saved).toHaveBeenCalledWith({
        ...product,
        name: 'Mechanical keyboard',
        updatedAt: '2026-11-11T09:00:00Z',
        categories: [electronics],
      });
    });

    it('removes a category with its chip', async () => {
      element.querySelector<HTMLButtonElement>('button[aria-label="Remove Electronics"]')!.click();
      await fixture.whenStable();
      expect(chips()).toEqual([]);

      await submit();
      expect(http.expectOne('http://api.test/api/product/7').request.body.categoryIds).toEqual([]);
    });

    it('chooses categories in the picker, keeping the ones already chosen', async () => {
      button('Choose categories').click();
      // Not whenStable(): the picker's request keeps the app busy until it's answered
      TestBed.tick();
      const request = http.expectOne('http://api.test/api/category/?page=1&size=10');
      expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
      request.flush({
        content: [electronics, keyboards],
        number: 0,
        size: 10,
        numberOfElements: 2,
        totalElements: 2,
        totalPages: 1,
        first: true,
        last: true,
      });
      await fixture.whenStable();
      const picker = element.querySelector<HTMLDialogElement>('app-category-picker-dialog dialog')!;

      const checkboxes = picker.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
      expect([...checkboxes].map((box) => box.checked)).toEqual([true, false]);
      checkboxes[1].click();
      await fixture.whenStable();
      expect(picker.textContent).toContain('2 chosen');

      button('Done', picker).click();
      await fixture.whenStable();

      expect(element.querySelector('app-category-picker-dialog')).toBeNull();
      expect(chips()).toEqual(['Electronics #2', 'Keyboards #5']);
    });

    it('says when only admins may edit', async () => {
      await submit();

      http.expectOne('http://api.test/api/product/7').flush(null, {
        status: 403,
        statusText: 'Forbidden',
      });
      await fixture.whenStable();

      expect(element.querySelector('[role="alert"]')!.textContent).toContain(
        "You're not allowed to edit products.",
      );
    });
  });
});
