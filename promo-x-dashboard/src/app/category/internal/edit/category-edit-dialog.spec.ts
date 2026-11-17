import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../infrastructure/http';
import { CategoryClient } from '../category.client';
import { Category } from '../category.model';
import { CategoryEditDialog } from './category-edit-dialog';

describe('CategoryEditDialog', () => {
  const category: Category = {
    id: 7,
    externalId: 'EXT-SMART',
    name: 'Smartphones',
    parent: { id: 3, externalId: 'EXT-ELEC', name: 'Electronics', parentId: null, storefrontId: 1 },
    storefrontId: 1,
  };
  const url = 'http://api.test/api/category/7';
  let fixture: ComponentFixture<CategoryEditDialog>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let saved: ReturnType<typeof vi.fn<(value: Category) => void>>;
  let closed: ReturnType<typeof vi.fn<() => void>>;

  // jsdom doesn't implement modal dialogs.
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
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        CategoryClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(CategoryEditDialog);
    fixture.componentRef.setInput('category', category);
    saved = vi.fn<(value: Category) => void>();
    closed = vi.fn<() => void>();
    fixture.componentInstance.saved.subscribe(saved);
    fixture.componentInstance.closed.subscribe(closed);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  });

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

  function parentPreview(): string {
    return element
      .querySelector('[data-testid="parent-preview"]')!
      .textContent!.replace(/\s+/g, ' ')
      .trim();
  }

  /** Opens the picker and answers its first request with these categories. */
  async function openPicker(content: Category[]): Promise<HTMLDialogElement> {
    button('Change').click();
    // Not whenStable(): the picker's request keeps the app busy until it's answered
    TestBed.tick();
    const request = http.expectOne('http://api.test/api/category/?page=1&size=10');
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    request.flush({
      content,
      number: 0,
      size: 10,
      numberOfElements: content.length,
      totalElements: content.length,
      totalPages: 1,
      first: true,
      last: true,
    });
    await fixture.whenStable();
    return element.querySelector<HTMLDialogElement>('app-category-picker-dialog dialog')!;
  }

  it('opens filled with the category and a preview of its parent', () => {
    expect(element.querySelector('dialog')!.open).toBe(true);
    expect(input('category-name').value).toBe('Smartphones');
    expect(input('category-external-id').value).toBe('EXT-SMART');
    expect(parentPreview()).toBe('Electronics ID 3 · EXT-ELEC');
  });

  it('saves the trimmed values, keeping the parent', async () => {
    await type('category-name', '  Phones  ');
    await submit();

    const request = http.expectOne(url);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Phones', externalId: 'EXT-SMART', parentId: 3 });

    const updated = { ...category, name: 'Phones' };
    request.flush(updated);
    expect(saved).toHaveBeenCalledWith(updated);
  });

  it('removes the parent, making the category top-level', async () => {
    button('Remove').click();
    await fixture.whenStable();
    expect(parentPreview()).toBe("None, it's a top-level category");
    expect(button('Choose')).toBeTruthy();

    await submit();

    expect(http.expectOne(url).request.body).toEqual({
      name: 'Smartphones',
      externalId: 'EXT-SMART',
      parentId: null,
    });
  });

  it('changes the parent to a category chosen in the picker', async () => {
    const accessories: Category = {
      id: 9,
      externalId: 'EXT-ACC',
      name: 'Accessories',
      parent: null,
      storefrontId: 1,
    };
    const picker = await openPicker([accessories]);
    expect(picker.open).toBe(true);

    button('Choose', picker).click();
    await fixture.whenStable();

    expect(element.querySelector('app-category-picker-dialog')).toBeNull();
    expect(parentPreview()).toBe('Accessories ID 9 · EXT-ACC');

    await submit();
    expect(http.expectOne(url).request.body).toEqual({
      name: 'Smartphones',
      externalId: 'EXT-SMART',
      parentId: 9,
    });
  });

  it("doesn't let the picker choose the category itself, its children or the current parent", async () => {
    const electronics: Category = {
      id: 3,
      externalId: 'EXT-ELEC',
      name: 'Electronics',
      parent: null,
      storefrontId: 1,
    };
    const smartphones = category;
    const android: Category = {
      id: 11,
      externalId: 'EXT-ANDROID',
      name: 'Android',
      parent: { id: 7, externalId: 'EXT-SMART', name: 'Smartphones', parentId: 3, storefrontId: 1 },
      storefrontId: 1,
    };
    const picker = await openPicker([electronics, smartphones, android]);

    const actions = [...picker.querySelectorAll('tbody tr')].map((row) =>
      row.lastElementChild!.textContent!.trim(),
    );
    expect(actions).toEqual(['Current', 'This category', 'Its child']);
    expect(picker.querySelector('button[aria-label^="Choose"]')).toBeNull();
  });

  it('keeps the parent when the picker is closed without choosing', async () => {
    const picker = await openPicker([]);

    picker.querySelector<HTMLButtonElement>('button[aria-label="Close"]')!.click();
    await fixture.whenStable();

    expect(element.querySelector('app-category-picker-dialog')).toBeNull();
    expect(parentPreview()).toBe('Electronics ID 3 · EXT-ELEC');
    expect(closed).not.toHaveBeenCalled();
  });

  it("doesn't send an invalid name", async () => {
    await type('category-name', 'A');
    await submit();

    http.expectNone(url);
    expect(element.textContent).toContain('Must be 2–50 characters.');
  });

  it("shows the backend's parent error under the parent", async () => {
    await submit();

    http.expectOne(url).flush(
      {
        code: 'PARENT_CATEGORY_NOT_FOUND',
        errors: [
          {
            path: 'parentId',
            code: 'PARENT_CATEGORY_NOT_FOUND',
            message: 'Parent category does not exist.',
          },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(element.querySelector('#category-parent-id-error')!.textContent).toContain(
      'Parent category does not exist.',
    );
  });

  it('warns that changing the external ID deactivates rules', async () => {
    expect(element.textContent).not.toContain('deactivates the active promotion rules');

    await type('category-external-id', 'EXT-PHONES');

    expect(element.textContent).toContain('deactivates the active promotion rules');
  });

  it("shows the backend's business errors on their fields", async () => {
    await type('category-external-id', 'EXT-TAKEN');
    await submit();

    http.expectOne(url).flush(
      {
        code: 'CATEGORY_ALREADY_EXISTS',
        errors: [
          {
            path: 'externalId',
            code: 'CATEGORY_ALREADY_EXISTS',
            message: "Category with external ID 'EXT-TAKEN' already exists for this storefront.",
          },
        ],
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(element.querySelector('#category-external-id-error')!.textContent).toContain(
      "'EXT-TAKEN' already exists",
    );
    expect(saved).not.toHaveBeenCalled();
  });

  it('says when the category no longer exists', async () => {
    await submit();

    http
      .expectOne(url)
      .flush({ code: 'CATEGORY_NOT_FOUND', errors: [] }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'This category no longer exists.',
    );
  });

  it('emits closed when canceled', () => {
    [...element.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Cancel')!
      .click();

    expect(closed).toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });
});
