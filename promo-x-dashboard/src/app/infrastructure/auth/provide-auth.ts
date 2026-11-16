import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import {
  AutoRefreshTokenService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  provideKeycloak,
  UserActivityService,
  withAutoRefreshToken,
} from 'keycloak-angular';
import { AuthApi } from './auth.api';
import { AuthConfig } from './auth.config';
import { bearerTokenCondition } from './internal/bearer-token-condition';
import { KeycloakAuthApi } from './internal/keycloak-auth.api';

const DEFAULT_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000;

/**
 * Sets up authentication against Keycloak. The dashboard is staff-only, so the app doesn't start until the user has
 * logged in (authorization code flow with PKCE). Pair it with `authInterceptor` in `provideHttpClient`.
 */
export function provideAuth(config: AuthConfig): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideKeycloak({
      config: { url: config.url, realm: config.realm, clientId: config.clientId },
      initOptions: {
        onLoad: 'login-required',
        pkceMethod: 'S256',
        // The session iframe needs third-party cookies; the token refresh notices an ended session instead.
        checkLoginIframe: false,
      },
      features: [
        withAutoRefreshToken({
          onInactivityTimeout: 'logout',
          sessionTimeout: config.inactivityTimeoutMs ?? DEFAULT_INACTIVITY_TIMEOUT_MS,
        }),
      ],
      providers: [AutoRefreshTokenService, UserActivityService],
    }),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: config.bearerTokenUrls.map(bearerTokenCondition),
    },
    { provide: AuthApi, useClass: KeycloakAuthApi },
  ]);
}
