import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { CategoryClient } from '../category.client';
import { Category } from '../category.model';
import { CategoryListPage } from './category-list.page';

describe('CategoryListPage', () => {
  const electronics: Category = {
    id: 3,
    externalId: 'EXT-ELEC',
    name: 'Electronics',
    parent: null,
    storefrontId: 1,
  };
  const smartphones: Category = {
    id: 7,
    externalId: 'EXT-SMART',
    name: 'Smartphones',
    parent: { id: 3, externalId: 'EXT-ELEC', name: 'Electronics', parentId: null, storefrontId: 1 },
    storefrontId: 1,
  };
  const listUrl = 'http://api.test/api/category/?page=1&size=20';

  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };
  let fixture: ComponentFixture<CategoryListPage>;
  let element: HTMLElement;
  let http: HttpTestingController;

  function pageOf(content: Category[], totalPages = 1): Page<Category> {
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

  async function render(page: Page<Category>): Promise<void> {
    fixture = TestBed.createComponent(CategoryListPage);
    fixture.componentRef.setInput('storefrontId', '1');
    TestBed.tick();
    http.expectOne(listUrl).flush(page);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function rows(): string[][] {
    return [...element.querySelectorAll('tbody tr')].map((row) =>
      [...row.querySelectorAll('td')].map((cell) => cell.textContent!.replace(/\s+/g, ' ').trim()),
    );
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
        CategoryClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("requests the storefront's categories with the X-Storefront-Id header", () => {
    fixture = TestBed.createComponent(CategoryListPage);
    fixture.componentRef.setInput('storefrontId', '1');
    TestBed.tick();

    const request = http.expectOne(listUrl);
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    request.flush(pageOf([]));
  });

  it('renders a row per category, with its parent', async () => {
    await render(pageOf([electronics, smartphones]));

    expect(rows()).toEqual([
      ['3', 'Electronics', 'EXT-ELEC', '—'],
      ['7', 'Smartphones', 'EXT-SMART', 'Electronics#3'],
    ]);
    expect(element.textContent).toContain('2 categories');
  });

  it('links the ID to the category', async () => {
    await render(pageOf([electronics]));

    expect(element.querySelector('tbody a')!.getAttribute('href')).toBe('/3');
  });

  it('says when the storefront has no categories', async () => {
    await render(pageOf([]));

    expect(element.querySelector('table')).toBeNull();
    expect(element.textContent).toContain('This storefront has no categories yet.');
  });

  it('shows numbered pages when there is more than one', async () => {
    await render(pageOf([electronics], 2));

    const pager = element.querySelector('app-pager nav')!;
    expect(pager.querySelector('[aria-current="page"]')!.textContent!.trim()).toBe('1');
    expect(pager.querySelector('a[aria-label="Page 2"]')).toBeTruthy();
  });

  it('shows the edit buttons only to employees', async () => {
    roles.set([Role.Auditor]);
    await render(pageOf([electronics]));
    expect(element.querySelector('button[aria-label="Edit Electronics"]')).toBeNull();

    roles.set([Role.Employee]);
    await fixture.whenStable();
    expect(element.querySelector('button[aria-label="Edit Electronics"]')).toBeTruthy();
  });

  it('updates the edited row and the parent shown on its children', async () => {
    roles.set([Role.Employee]);
    await render(pageOf([electronics, smartphones]));

    element.querySelector<HTMLButtonElement>('button[aria-label="Edit Electronics"]')!.click();
    await fixture.whenStable();
    const name = element.querySelector<HTMLInputElement>('#category-name')!;
    name.value = 'Gadgets';
    name.dispatchEvent(new Event('input'));
    element.querySelector('dialog form')!.dispatchEvent(new Event('submit'));

    http.expectOne('http://api.test/api/category/3').flush({ ...electronics, name: 'Gadgets' });
    await fixture.whenStable();

    expect(element.querySelector('dialog')).toBeNull();
    expect(rows()).toEqual([
      ['3', 'Gadgets', 'EXT-ELEC', '—', ''],
      ['7', 'Smartphones', 'EXT-SMART', 'Gadgets#3', ''],
    ]);
  });

  it('shows a retry when the list fails to load', async () => {
    fixture = TestBed.createComponent(CategoryListPage);
    fixture.componentRef.setInput('storefrontId', '1');
    TestBed.tick();
    http.expectOne(listUrl).flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      "Couldn't load the categories.",
    );
  });
});
