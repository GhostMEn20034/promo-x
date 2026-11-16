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
    http.expectOne('http://api.test/storefront/?page=1&size=12').flush(page);
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;
  }

  function cardNames(): string[] {
    return [...element.querySelectorAll('app-storefront-card h2')].map((h) =>
      h.textContent!.trim(),
    );
  }

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
      .expectOne('http://api.test/storefront/?page=1&size=12')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      "Couldn't load the storefronts.",
    );
  });
});
