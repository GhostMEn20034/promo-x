import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../../infrastructure/http';
import { StorefrontOverviewPage } from '../overview/storefront-overview.page';
import { StorefrontClient } from '../storefront.client';
import { StorefrontDetailsPage } from './storefront-details.page';

describe('StorefrontDetailsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        StorefrontClient,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the storefront with a tab per section', async () => {
    const fixture = TestBed.createComponent(StorefrontDetailsPage);
    fixture.componentRef.setInput('storefrontId', '1');
    TestBed.tick();
    http
      .expectOne('http://api.test/api/storefront/1')
      .flush({ id: 1, name: 'Tech Store', website: 'techstore.com' });
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')!.textContent).toContain('Tech Store');
    const tabs = [...element.querySelectorAll('nav a')].map((a) => a.textContent!.trim());
    expect(tabs).toEqual(['Overview', 'Categories', 'Products', 'Promotion rules Soon']);
    expect(element.querySelector('router-outlet')).toBeTruthy();
  });

  it("says the storefront doesn't exist on a 404, without tabs", async () => {
    const fixture = TestBed.createComponent(StorefrontDetailsPage);
    fixture.componentRef.setInput('storefrontId', '99');
    TestBed.tick();
    http
      .expectOne('http://api.test/api/storefront/99')
      .flush(
        { code: 'STOREFRONT_NOT_FOUND', errors: [] },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain("This storefront doesn't exist.");
    expect(element.querySelector('nav')).toBeNull();
  });
});

describe('StorefrontOverviewPage', () => {
  it('links to every section, marking the ones that are coming soon', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(StorefrontOverviewPage);
    await fixture.whenStable();
    const links = [...(fixture.nativeElement as HTMLElement).querySelectorAll('a')];

    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/categories',
      '/products',
      '/promotion-rules',
    ]);
    expect(links[0].textContent).not.toContain('Coming soon');
    expect(links[1].textContent).not.toContain('Coming soon');
    expect(links[2].textContent).toContain('Coming soon');
  });
});
