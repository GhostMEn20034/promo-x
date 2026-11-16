import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthApi } from './auth.api';
import { FORBIDDEN_PATH } from './auth.routes';
import { Role } from './role';

/**
 * Lets the route through when the user has at least one of `roles`. An anonymous user is sent to log in and an
 * authenticated user without the roles to the forbidden page.
 *
 * The UI check only hides what the user can't use; promo-x-core enforces the roles itself.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return async (_route, state) => {
    const auth = inject(AuthApi);

    if (!auth.authenticated()) {
      await auth.login(state.url);
      return false;
    }

    return auth.hasAnyRole(roles) || inject(Router).createUrlTree([FORBIDDEN_PATH]);
  };
}
