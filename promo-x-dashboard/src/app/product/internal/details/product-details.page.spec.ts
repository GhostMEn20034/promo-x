import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { API_BASE_URL } from '../../../infrastructure/http';
import { ProductClient } from '../product.client';
import { Product } from '../product.model';
import { ProductDetailsPage } from './product-details.page';

describe('ProductDetailsPage', () => {
  const keyboard: Product = {
    id: 7,
    externalId: 'PROD-KB',
    sku: 'SKU-KB',
    name: 'Keyboard',
    storefrontId: 1,
    createdAt: '2026-11-10T21:35:46Z',
    updatedAt: '2026-11-10T21:35:46Z',
    categories: [{ id: 2, externalId: 'CAT-ELEC', name: 'Electronics' }],
  };
  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };
  let fixture: ComponentFixture<ProductDetailsPage>;
  let http: HttpTestingController;

  async function render(productId: string, respond: (url: string) => void): Promise<HTMLElement> {
    fixture = TestBed.createComponent(ProductDetailsPage);
    fixture.componentRef.setInput('storefrontId', '1');
    fixture.componentRef.setInput('productId', productId);
    TestBed.tick();
    respond(`http://api.test/api/product/${productId}`);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
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

  it('loads the product by its ID and shows it, linking its categories', async () => {
    const element = await render('7', (url) => http.expectOne(url).flush(keyboard));

    expect(element.querySelector('h2')!.textContent).toContain('Keyboard');
    expect(element.textContent).toContain('SKU-KB');
    expect(element.textContent).toContain('PROD-KB');
    const category = element.querySelector<HTMLAnchorElement>('app-category-chips a')!;
    expect(category.textContent).toBe('Electronics');
    expect(category.getAttribute('href')).toBe('/categories/2');
  });

  it('lets admins edit the product and shows the saved values', async () => {
    roles.set([Role.Employee]);
    const element = await render('7', (url) => http.expectOne(url).flush(keyboard));
    const editButton = () =>
      [...element.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Edit');
    expect(editButton()).toBeUndefined();

    roles.set([Role.Admin]);
    await fixture.whenStable();
    editButton()!.click();
    await fixture.whenStable();
    const name = element.querySelector<HTMLInputElement>('#product-name')!;
    name.value = 'Mechanical keyboard';
    name.dispatchEvent(new Event('input'));
    element.querySelector('dialog form')!.dispatchEvent(new Event('submit'));

    http.expectOne('http://api.test/api/product/7').flush({
      ...keyboard,
      categories: undefined,
      name: 'Mechanical keyboard',
      categoryIds: [2],
    });
    await fixture.whenStable();

    expect(element.querySelector('dialog')).toBeNull();
    expect(element.querySelector('h2')!.textContent).toContain('Mechanical keyboard');
    expect(element.querySelector('app-category-chips')!.textContent).toContain('Electronics');
  });

  it("says the product doesn't exist on a 404", async () => {
    const element = await render('99', (url) =>
      http
        .expectOne(url)
        .flush({ code: 'PRODUCT_NOT_FOUND', errors: [] }, { status: 404, statusText: 'Not Found' }),
    );

    expect(element.textContent).toContain("This product doesn't exist in this storefront.");
  });

  it("doesn't show a product of another storefront", async () => {
    const element = await render('7', (url) =>
      http.expectOne(url).flush({ ...keyboard, storefrontId: 2 }),
    );

    expect(element.querySelector('h2')).toBeNull();
    expect(element.textContent).toContain("This product doesn't exist in this storefront.");
  });
});
