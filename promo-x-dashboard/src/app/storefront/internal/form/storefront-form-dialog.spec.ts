import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { API_BASE_URL } from '../../../infrastructure/http';
import { StorefrontClient } from '../storefront.client';
import { Storefront } from '../storefront.model';
import { StorefrontFormDialog } from './storefront-form-dialog';

describe('StorefrontFormDialog', () => {
  const storefront: Storefront = { id: 7, name: 'Tech Store', website: 'https://techstore.com' };
  let fixture: ComponentFixture<StorefrontFormDialog>;
  let element: HTMLElement;
  let http: HttpTestingController;
  let saved: ReturnType<typeof vi.fn<(value: Storefront) => void>>;
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
        StorefrontClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(StorefrontFormDialog);
    fixture.componentRef.setInput('storefront', storefront);
    saved = vi.fn<(value: Storefront) => void>();
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

  function type(id: string, value: string): void {
    input(id).value = value;
    input(id).dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('opens filled with the storefront', () => {
    expect(element.querySelector('dialog')!.open).toBe(true);
    expect(input('storefront-name').value).toBe('Tech Store');
    expect(input('storefront-website').value).toBe('https://techstore.com');
  });

  it('saves the trimmed values, sending a blank website as null', async () => {
    type('storefront-name', '  New Name  ');
    type('storefront-website', '   ');
    await submit();

    const request = http.expectOne('http://api.test/api/storefront/7');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'New Name', website: null });

    const updated = { id: 7, name: 'New Name', website: null };
    request.flush(updated);
    expect(saved).toHaveBeenCalledWith(updated);
  });

  it("doesn't send a blank name", async () => {
    type('storefront-name', '   ');
    await submit();

    http.expectNone('http://api.test/api/storefront/7');
    expect(element.textContent).toContain('Name is required.');
  });

  it("shows the backend's validation errors on their fields", async () => {
    await submit();

    http.expectOne('http://api.test/api/storefront/7').flush(
      {
        code: 'VALIDATION_ERROR',
        errors: [{ path: 'website', code: 'SIZE', message: 'size must be between 1 and 255' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(element.textContent).toContain('size must be between 1 and 255');
    expect(saved).not.toHaveBeenCalled();
  });

  it('says when the storefront no longer exists', async () => {
    await submit();

    http
      .expectOne('http://api.test/api/storefront/7')
      .flush(
        { code: 'STOREFRONT_NOT_FOUND', errors: [] },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'This storefront no longer exists.',
    );
  });

  it('emits closed when canceled', () => {
    [...element.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Cancel')!
      .click();

    expect(closed).toHaveBeenCalled();
    expect(saved).not.toHaveBeenCalled();
  });

  describe('without a storefront', () => {
    beforeEach(async () => {
      fixture.destroy();
      fixture = TestBed.createComponent(StorefrontFormDialog);
      fixture.componentInstance.saved.subscribe(saved);
      await fixture.whenStable();
      element = fixture.nativeElement as HTMLElement;
    });

    it('opens empty, to create one', () => {
      expect(element.querySelector('h2')!.textContent).toContain('New storefront');
      expect(input('storefront-name').value).toBe('');
      expect(element.querySelector('button[type="submit"]')!.textContent).toContain(
        'Create storefront',
      );
    });

    it('creates the storefront', async () => {
      type('storefront-name', ' Book Store ');
      type('storefront-website', 'https://books.example.com');
      await submit();

      const request = http.expectOne('http://api.test/api/storefront/');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        name: 'Book Store',
        website: 'https://books.example.com',
      });

      const created = { id: 9, name: 'Book Store', website: 'https://books.example.com' };
      request.flush(created);
      expect(saved).toHaveBeenCalledWith(created);
    });

    it('says when the user may not create storefronts', async () => {
      type('storefront-name', 'Book Store');
      await submit();

      http
        .expectOne('http://api.test/api/storefront/')
        .flush(null, { status: 403, statusText: 'Forbidden' });
      await fixture.whenStable();

      expect(element.querySelector('[role="alert"]')!.textContent).toContain(
        "You're not allowed to create storefronts.",
      );
    });
  });
});
