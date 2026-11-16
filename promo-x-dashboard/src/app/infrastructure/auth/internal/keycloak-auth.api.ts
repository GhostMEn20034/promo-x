import { computed, DOCUMENT, inject, Injectable } from '@angular/core';
import { KEYCLOAK_EVENT_SIGNAL } from 'keycloak-angular';
import Keycloak from 'keycloak-js';
import { AuthApi } from '../auth.api';
import { Role } from '../role';
import { rolesOf, userOf } from './access-token';

@Injectable()
export class KeycloakAuthApi extends AuthApi {
  private readonly keycloak = inject(Keycloak);
  private readonly keycloakEvent = inject(KEYCLOAK_EVENT_SIGNAL);
  private readonly document = inject(DOCUMENT);

  /**
   * keycloak-js mutates its instance instead of emitting state, so the token is read again on every event it
   * reports (ready, login, refresh, logout, expiry).
   */
  private readonly token = computed(() => {
    this.keycloakEvent();
    return this.keycloak.authenticated ? (this.keycloak.tokenParsed ?? null) : null;
  });

  readonly authenticated = computed(() => this.token() !== null);

  readonly user = computed(() => {
    const token = this.token();
    return token ? userOf(token) : null;
  });

  readonly roles = computed<readonly Role[]>(() => {
    const token = this.token();
    return token ? rolesOf(token) : [];
  });

  hasAnyRole(roles: readonly Role[]): boolean {
    const granted = this.roles();
    return roles.some((role) => granted.includes(role));
  }

  login(redirectPath?: string): Promise<void> {
    const redirectUri = redirectPath
      ? new URL(redirectPath.replace(/^\//, ''), this.document.baseURI).href
      : this.document.location.href;
    return this.keycloak.login({ redirectUri });
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: this.document.baseURI });
  }
}
