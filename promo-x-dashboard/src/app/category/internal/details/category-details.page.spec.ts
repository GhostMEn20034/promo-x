import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../../infrastructure/http';
import { CategoryClient } from '../category.client';
import { Category } from '../category.model';
import { CategoryDetailsPage } from './category-details.page';

describe('CategoryDetailsPage', () => {
  const smartphones: Category = {
    id: 7,
    externalId: 'EXT-SMART',
    name: 'Smartphones',
    parent: { id: 3, externalId: 'EXT-ELEC', name: 'Electronics', parentId: null, storefrontId: 1 },
    storefrontId: 1,
  };
  let fixture: ComponentFixture<CategoryDetailsPage>;
  let http: HttpTestingController;

  async function render(categoryId: string, respond: (url: string) => void): Promise<HTMLElement> {
    fixture = TestBed.createComponent(CategoryDetailsPage);
    fixture.componentRef.setInput('storefrontId', '1');
    fixture.componentRef.setInput('categoryId', categoryId);
    TestBed.tick();
    respond(`http://api.test/api/category/${categoryId}`);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        CategoryClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the category by its ID and shows it', async () => {
    const element = await render('7', (url) => http.expectOne(url).flush(smartphones));

    expect(element.querySelector('h2')!.textContent).toContain('Smartphones');
    expect(element.textContent).toContain('EXT-SMART');
    expect(element.textContent).toContain('Electronics');
  });

  it("says the category doesn't exist on a 404", async () => {
    const element = await render('99', (url) =>
      http
        .expectOne(url)
        .flush(
          { code: 'CATEGORY_NOT_FOUND', errors: [] },
          { status: 404, statusText: 'Not Found' },
        ),
    );

    expect(element.textContent).toContain("This category doesn't exist in this storefront.");
  });

  it("doesn't show a category of another storefront", async () => {
    const element = await render('7', (url) =>
      http.expectOne(url).flush({ ...smartphones, storefrontId: 2 }),
    );

    expect(element.querySelector('h2')).toBeNull();
    expect(element.textContent).toContain("This category doesn't exist in this storefront.");
  });

  it("doesn't request an ID that isn't a number", async () => {
    const element = await render('abc', () => http.expectNone(() => true));

    expect(element.textContent).toContain("This category doesn't exist in this storefront.");
  });
});
