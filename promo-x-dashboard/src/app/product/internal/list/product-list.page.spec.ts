import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { ProductClient } from '../product.client';
import { Product } from '../product.model';
import { ProductListPage } from './product-list.page';

describe('ProductListPage', () => {
  const keyboard: Product = {
    id: 7,
    externalId: 'PROD-KB',
    sku: 'SKU-KB',
    name: 'Keyboard',
    storefrontId: 1,
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-10T21:35:46Z',
    categories: [
      { id: 2, externalId: 'CAT-ELEC', name: 'Electronics' },
      { id: 5, externalId: 'CAT-KB', name: 'Keyboards' },
      { id: 8, externalId: 'CAT-GAMING', name: 'Gaming' },
    ],
  };
  const listUrl = 'http://api.test/api/product/?page=1&size=20';

  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };
  let fixture: ComponentFixture<ProductListPage>;
  let element: HTMLElement;
  let http: HttpTestingController;

  function pageOf(content: Product[], totalPages = 1): Page<Product> {
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

  async function render(page: Page<Product>): Promise<void> {
    fixture = TestBed.createComponent(ProductListPage);
    fixture.componentRef.setInput('storefrontId', '1');
    TestBed.tick();
    const request = http.expectOne(listUrl);
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    request.flush(page);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function rows(): string[][] {
    return [...element.querySelectorAll('tbody tr')].map((row) =>
      [...row.querySelectorAll('td')].map((cell) => cell.textContent!.replace(/\s+/g, ' ').trim()),
    );
  }

  function button(text: string): HTMLButtonElement | undefined {
    return [...element.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
  }

  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
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
        ProductClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders a row per product, with up to two categories', async () => {
    await render(pageOf([keyboard]));

    expect(rows()).toEqual([
      ['7', 'Keyboard', 'SKU-KB', 'PROD-KB', 'ElectronicsKeyboards+1', 'Nov 10, 2026'],
    ]);
    expect(element.querySelector('tbody a')!.getAttribute('href')).toBe('/7');
    expect(element.textContent).toContain('1 product');
  });

  it('says when the storefront has no products', async () => {
    await render(pageOf([]));

    expect(element.querySelector('table')).toBeNull();
    expect(element.textContent).toContain('This storefront has no products yet.');
  });

  it('shows numbered pages when there is more than one', async () => {
    await render(pageOf([keyboard], 3));

    expect(element.querySelector('app-pager a[aria-label="Page 3"]')).toBeTruthy();
  });

  it('offers creating and bulk import only to employees', async () => {
    roles.set([Role.Auditor]);
    await render(pageOf([keyboard]));
    expect(button('New product')).toBeUndefined();
    expect(element.querySelector('a[href="/bulk"]')).toBeNull();

    roles.set([Role.Employee]);
    await fixture.whenStable();
    expect(button('New product')).toBeTruthy();
    expect(element.querySelector('a[href="/bulk"]')!.textContent).toContain('Bulk import');
  });

  it('shows the edit buttons only to admins', async () => {
    roles.set([Role.Employee]);
    await render(pageOf([keyboard]));
    expect(element.querySelector('button[aria-label="Edit Keyboard"]')).toBeNull();

    roles.set([Role.Admin]);
    await fixture.whenStable();
    expect(element.querySelector('button[aria-label="Edit Keyboard"]')).toBeTruthy();
  });

  it('updates the edited row in place', async () => {
    roles.set([Role.Admin]);
    await render(pageOf([keyboard]));

    element.querySelector<HTMLButtonElement>('button[aria-label="Edit Keyboard"]')!.click();
    await fixture.whenStable();
    const name = element.querySelector<HTMLInputElement>('#product-name')!;
    name.value = 'Mechanical keyboard';
    name.dispatchEvent(new Event('input'));
    element.querySelector('dialog form')!.dispatchEvent(new Event('submit'));

    http.expectOne('http://api.test/api/product/7').flush({
      ...keyboard,
      categories: undefined,
      name: 'Mechanical keyboard',
      categoryIds: [2, 5, 8],
    });
    await fixture.whenStable();

    expect(element.querySelector('dialog')).toBeNull();
    expect(rows()[0][1]).toBe('Mechanical keyboard');
    expect(rows()[0][4]).toBe('ElectronicsKeyboards+1');
  });

  it('reloads the first page after creating a product and announces it', async () => {
    roles.set([Role.Employee]);
    await render(pageOf([keyboard]));

    button('New product')!.click();
    await fixture.whenStable();
    for (const [id, value] of [
      ['product-name', 'Mouse'],
      ['product-sku', 'SKU-MS'],
      ['product-externalId', 'PROD-MS'],
    ]) {
      const field = element.querySelector<HTMLInputElement>(`#${id}`)!;
      field.value = value;
      field.dispatchEvent(new Event('input'));
    }
    element.querySelector('dialog form')!.dispatchEvent(new Event('submit'));

    const mouse = {
      ...keyboard,
      id: 9,
      name: 'Mouse',
      sku: 'SKU-MS',
      externalId: 'PROD-MS',
      categories: [],
    };
    http.expectOne('http://api.test/api/product/').flush({ ...mouse, categoryIds: [] });
    TestBed.tick();
    http.expectOne(listUrl).flush(pageOf([mouse, keyboard]));
    await fixture.whenStable();

    expect(element.querySelector('[role="status"]')!.textContent).toContain('Created Mouse.');
    expect(rows().map((row) => row[1])).toEqual(['Mouse', 'Keyboard']);
  });
});
