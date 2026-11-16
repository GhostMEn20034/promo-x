import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthApi } from './auth.api';
import { Role } from './role';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  const authenticated = signal(false);
  const granted = signal<Role[]>([]);
  const auth = {
    authenticated,
    roles: granted,
    hasAnyRole: (roles: readonly Role[]) => roles.some((role) => granted().includes(role)),
    login: vi.fn(() => Promise.resolve()),
  };

  function run(...roles: Role[]) {
    const state = { url: '/promotions' } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() =>
      roleGuard(...roles)({} as ActivatedRouteSnapshot, state),
    ) as Promise<boolean | UrlTree>;
  }

  beforeEach(() => {
    authenticated.set(false);
    granted.set([]);
    auth.login.mockClear();
    TestBed.configureTestingModule({ providers: [{ provide: AuthApi, useValue: auth }] });
  });

  it('sends an anonymous user to log in, coming back to the requested URL', async () => {
    expect(await run(Role.PromotionManager)).toBe(false);
    expect(auth.login).toHaveBeenCalledWith('/promotions');
  });

  it('lets a user with one of the roles through', async () => {
    authenticated.set(true);
    granted.set([Role.Auditor]);

    expect(await run(Role.PromotionManager, Role.Auditor)).toBe(true);
  });

  it('sends a user without the roles to the forbidden page', async () => {
    authenticated.set(true);
    granted.set([Role.Employee]);

    const result = await run(Role.PromotionManager);

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/forbidden');
  });
});
