import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../../infrastructure/http';
import { ProductClient } from '../product.client';
import { ProductBulkCreatePage } from './product-bulk-create.page';

describe('ProductBulkCreatePage', () => {
  const bulkUrl = 'http://api.test/api/product/bulk-insert';
  let fixture: ComponentFixture<ProductBulkCreatePage>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        ProductClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductBulkCreatePage);
    fixture.componentRef.setInput('storefrontId', '1');
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function button(text: string): HTMLButtonElement {
    return [...element.querySelectorAll('button')].find((b) =>
      b.textContent?.trim().startsWith(text),
    )!;
  }

  async function paste(csv: string): Promise<void> {
    const textarea = element.querySelector('textarea')!;
    textarea.value = csv;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    button('Check CSV').click();
    await fixture.whenStable();
  }

  function summary(): string {
    return element
      .querySelector('[data-testid="bulk-summary"]')!
      .textContent!.replace(/\s+/g, ' ')
      .trim();
  }

  it('previews the rows and imports them in one request', async () => {
    await paste('externalId,sku,name,categoryIds\nKB-1,SKU-KB,Keyboard,2;5\nMS-1,SKU-MS,Mouse,');
    expect(summary()).toBe('2 products · ready to import');
    expect(element.querySelectorAll('tbody tr')).toHaveLength(2);

    button('Import 2 products').click();
    await fixture.whenStable();

    const request = http.expectOne(bulkUrl);
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    expect(request.request.body).toEqual({
      items: [
        { externalId: 'KB-1', sku: 'SKU-KB', name: 'Keyboard', categoryIds: [2, 5] },
        { externalId: 'MS-1', sku: 'SKU-MS', name: 'Mouse', categoryIds: [] },
      ],
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(element.querySelector('[role="status"]')!.textContent).toContain('Imported 2 products.');
    const viewProducts = element.querySelector('[role="status"] a')!;
    expect(viewProducts.textContent).toContain('View products');
    expect(viewProducts.getAttribute('href')).toBe('/');
  });

  it("doesn't import while rows have problems", async () => {
    await paste('externalId,sku,name\nKB-1,,Keyboard\nKB-1,SKU-2,Keyboard 2');

    expect(summary()).toBe('2 products · 2 with problems');
    expect(element.textContent).toContain('SKU: Required.');
    expect(element.textContent).toContain('External ID: Duplicates line 2.');
    expect(button('Import 2 products').disabled).toBe(true);
  });

  it('shows an example, and puts it in the editor checked', async () => {
    const example = element.querySelector('[data-testid="bulk-example"]')!.textContent!;
    expect(example).toContain('externalId,sku,name,categoryIds');
    expect(example).toContain('"Mouse, wireless"');

    button('Use example').click();
    await fixture.whenStable();

    expect(element.querySelector('textarea')!.value).toBe(example);
    expect(summary()).toBe('3 products · ready to import');
    const names = [...element.querySelectorAll('tbody tr')].map((row) =>
      row.querySelectorAll('td')[3].textContent!.trim(),
    );
    expect(names).toEqual(['Mechanical keyboard', 'Mouse, wireless', 'Desk pad']);
  });

  it('downloads the example as a CSV file', async () => {
    const createObjectURL = vi.fn((_: Blob) => 'blob:example');
    const revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    // jsdom has no object URLs; the originals (if any) are put back below
    const original = { create: URL.createObjectURL, revoke: URL.revokeObjectURL };
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;

    try {
      button('Download products-import-example.csv').click();

      const link = click.mock.contexts[0] as HTMLAnchorElement;
      expect(link.download).toBe('products-import-example.csv');
      expect(link.href).toBe('blob:example');
      const blob = createObjectURL.mock.calls[0][0];
      expect(blob.type).toBe('text/csv;charset=utf-8');
      expect(await blob.text()).toBe(
        element.querySelector('[data-testid="bulk-example"]')!.textContent,
      );
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:example');
    } finally {
      click.mockRestore();
      URL.createObjectURL = original.create;
      URL.revokeObjectURL = original.revoke;
    }
  });

  it('says what is wrong with the CSV itself', async () => {
    await paste('id,title\n1,Keyboard');

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'The header row is missing: externalId, sku, name.',
    );
    expect(element.querySelector('table')).toBeNull();
  });

  it("shows the backend's problems on their rows and imports nothing", async () => {
    await paste('externalId,sku,name,categoryIds\nKB-1,SKU-KB,Keyboard,2\nMS-1,SKU-MS,Mouse,99');
    button('Import 2 products').click();
    await fixture.whenStable();

    http.expectOne(bulkUrl).flush(
      {
        code: 'CATEGORY_IDS_NOT_FOUND',
        errors: [
          {
            path: 'items[1].categoryIds',
            code: 'CATEGORY_IDS_NOT_FOUND',
            message: 'One or more category IDs do not exist',
          },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')!.textContent).toContain('Nothing was imported.');
    expect(summary()).toBe('2 products · 1 with problems');
    // Only the rows with problems are shown
    const rows = element.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('Category IDs: One or more category IDs do not exist');
    expect(button('Import 2 products').disabled).toBe(true);
  });
});
