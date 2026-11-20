import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { DeferBlockState, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
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
    expect(tabs).toEqual(['Overview', 'Categories', 'Products', 'Promotion rules']);
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
  const roles = signal<Role[]>([]);
  const auth = {
    roles,
    hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
  };
  let http: HttpTestingController;

  beforeEach(() => {
    roles.set([]);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://api.test' },
        { provide: AuthApi, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(StorefrontOverviewPage);
    fixture.componentRef.setInput('storefrontId', '1');
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('links to every section', async () => {
    const element = await render();
    const links = [...element.querySelectorAll('a')];

    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/categories',
      '/products',
      '/promotion-rules',
    ]);
    expect(links.filter((a) => a.textContent!.includes('Coming soon'))).toEqual([]);
  });

  it("doesn't show the promotion usage to those who may not read it", async () => {
    roles.set([Role.Employee]);
    const element = await render();

    expect(element.querySelector('app-usage-stats-panel')).toBeNull();
  });

  it('shows the promotion usage to auditors, promotion managers and admins', async () => {
    roles.set([Role.Auditor]);
    const fixture = TestBed.createComponent(StorefrontOverviewPage);
    fixture.componentRef.setInput('storefrontId', '1');
    fixture.detectChanges();
    const [panel] = await fixture.getDeferBlocks();
    await panel.render(DeferBlockState.Complete);
    TestBed.tick();
    const request = http.expectOne(
      (req) => req.url === 'http://api.test/api/promotion/usage/stats',
    );
    expect(request.request.headers.get('X-Storefront-Id')).toBe('1');
    request.flush({
      totals: {
        usageCount: 0,
        enteredPromoCodeCount: 0,
        registeredCustomerUsageCount: 0,
        anonymousCustomerUsageCount: 0,
        averageTotalDiscount: null,
        averageSubtotalDiscount: null,
        averageShippingDiscount: null,
      },
      dailyCounts: [],
    });
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('app-usage-stats-panel h2')!.textContent).toContain(
      'Promotion usage',
    );
  });
});
