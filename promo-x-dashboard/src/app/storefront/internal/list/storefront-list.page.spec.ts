import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { API_BASE_URL, Page } from '../../../infrastructure/http';
import { StorefrontClient } from '../storefront.client';
import { Storefront } from '../storefront.model';
import { StorefrontListPage } from './storefront-list.page';

describe('StorefrontListPage', () => {
  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };
  let fixture: ComponentFixture<StorefrontListPage>;
  let element: HTMLElement;
  let http: HttpTestingController;

  function pageOf(content: Storefront[], totalPages = 1): Page<Storefront> {
    return {
      content,
      number: 0,
      size: 12,
      numberOfElements: content.length,
      totalElements: content.length,
      totalPages,
      first: true,
      last: totalPages === 1,
    };
  }

  async function render(page: Page<Storefront>): Promise<void> {
    fixture = TestBed.createComponent(StorefrontListPage);
    TestBed.tick();
    http.expectOne('http://api.test/api/storefront/?page=1&size=12').flush(page);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function cardNames(): string[] {
    return [...element.querySelectorAll('app-storefront-card h2')].map((h) =>
      h.textContent!.trim(),
    );
  }

  // jsdom doesn't implement modal dialogs.
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
        StorefrontClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders a card per storefront of the first page', async () => {
    await render(
      pageOf([
        { id: 1, name: 'Tech Store', website: 'https://techstore.com' },
        { id: 2, name: 'Book Store', website: null },
      ]),
    );

    expect(cardNames()).toEqual(['Tech Store', 'Book Store']);
    expect(element.querySelector('nav')).toBeNull();
  });

  it('says when there are no storefronts', async () => {
    await render(pageOf([]));

    expect(element.textContent).toContain('There are no storefronts yet.');
  });

  it('shows the edit buttons only to admins', async () => {
    await render(pageOf([{ id: 1, name: 'Tech Store', website: null }]));
    expect(element.querySelector('button[aria-label="Edit Tech Store"]')).toBeNull();

    roles.set([Role.Admin]);
    await fixture.whenStable();
    expect(element.querySelector('button[aria-label="Edit Tech Store"]')).toBeTruthy();
  });

  it('shows the paging controls when there are more pages', async () => {
    await render(pageOf([{ id: 1, name: 'Tech Store', website: null }], 3));

    expect(element.querySelector('nav')!.textContent).toContain('Page 1 of 3');
  });

  it('shows a retry when the list fails to load', async () => {
    fixture = TestBed.createComponent(StorefrontListPage);
    TestBed.tick();
    http
      .expectOne('http://api.test/api/storefront/?page=1&size=12')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      "Couldn't load the storefronts.",
    );
  });

  it('offers a new storefront to admins: in the header, and as a card after the last one', async () => {
    await render(pageOf([{ id: 1, name: 'Tech Store', website: null }]));
    expect(element.querySelector('[data-testid="new-storefront-card"]')).toBeNull();
    expect(element.textContent).not.toContain('New storefront');

    roles.set([Role.Admin]);
    await fixture.whenStable();
    const cards = [...element.querySelectorAll('ul > li')];
    expect(cards.at(-1)!.querySelector('[data-testid="new-storefront-card"]')).toBeTruthy();
    expect(
      [...element.querySelectorAll('button')].filter(
        (button) => button.textContent!.trim() === 'New storefront',
      ),
    ).toHaveLength(2);
  });

  it('shows the new storefront card on the last page only', async () => {
    roles.set([Role.Admin]);
    await render(pageOf([{ id: 1, name: 'Tech Store', website: null }], 3));

    expect(element.querySelector('[data-testid="new-storefront-card"]')).toBeNull();
  });

  it('creates a storefront, then confirms it and reloads the list', async () => {
    roles.set([Role.Admin]);
    await render(pageOf([{ id: 1, name: 'Tech Store', website: null }]));

    element.querySelector<HTMLButtonElement>('[data-testid="new-storefront-card"]')!.click();
    await fixture.whenStable();
    const name = element.querySelector<HTMLInputElement>('#storefront-name')!;
    name.value = 'Book Store';
    name.dispatchEvent(new Event('input'));
    element.querySelector('form')!.dispatchEvent(new Event('submit'));

    const created = { id: 2, name: 'Book Store', website: null };
    http.expectOne('http://api.test/api/storefront/').flush(created);
    TestBed.tick();
    http
      .expectOne('http://api.test/api/storefront/?page=1&size=12')
      .flush(pageOf([{ id: 1, name: 'Tech Store', website: null }, created]));
    await fixture.whenStable();

    expect(element.querySelector('dialog')).toBeNull();
    expect(element.querySelector('[role="status"]')!.textContent).toContain('Created Book Store.');
    expect(element.querySelector('[role="status"] a')!.getAttribute('href')).toBe('/storefronts/2');
    expect(cardNames()).toEqual(['Tech Store', 'Book Store']);
  });
});
