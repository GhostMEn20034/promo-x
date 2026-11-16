import { DOCUMENT, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { KEYCLOAK_EVENT_SIGNAL, KeycloakEvent, KeycloakEventType } from 'keycloak-angular';
import Keycloak, { KeycloakTokenParsed } from 'keycloak-js';
import { Role } from '../role';
import { KeycloakAuthApi } from './keycloak-auth.api';

describe('KeycloakAuthApi', () => {
  const keycloak = {
    authenticated: false,
    tokenParsed: undefined as KeycloakTokenParsed | undefined,
    login: vi.fn(() => Promise.resolve()),
    logout: vi.fn(() => Promise.resolve()),
  };
  const keycloakEvent = signal<KeycloakEvent>({ type: KeycloakEventType.KeycloakAngularInit });
  let auth: KeycloakAuthApi;

  function signIn(token: KeycloakTokenParsed): void {
    keycloak.authenticated = true;
    keycloak.tokenParsed = token;
    keycloakEvent.set({ type: KeycloakEventType.AuthSuccess });
  }

  beforeEach(() => {
    keycloak.authenticated = false;
    keycloak.tokenParsed = undefined;
    keycloak.login.mockClear();
    keycloak.logout.mockClear();
    TestBed.configureTestingModule({
      providers: [
        KeycloakAuthApi,
        { provide: Keycloak, useValue: keycloak },
        { provide: KEYCLOAK_EVENT_SIGNAL, useValue: keycloakEvent },
      ],
    });
    auth = TestBed.inject(KeycloakAuthApi);
  });

  it('is anonymous before Keycloak reports a login', () => {
    expect(auth.authenticated()).toBe(false);
    expect(auth.user()).toBeNull();
    expect(auth.roles()).toEqual([]);
  });

  it('reads the user and the top-level roles claim from the access token', () => {
    signIn({
      sub: 'user-1',
      preferred_username: 'jdoe',
      name: 'John Doe',
      email: 'jdoe@example.com',
      roles: ['promotion-manager', 'offline_access', 'auditor'],
      realm_access: { roles: ['admin'] },
    });

    expect(auth.authenticated()).toBe(true);
    expect(auth.user()).toEqual({
      id: 'user-1',
      username: 'jdoe',
      name: 'John Doe',
      email: 'jdoe@example.com',
    });
    expect(auth.roles()).toEqual([Role.PromotionManager, Role.Auditor]);
    expect(auth.hasAnyRole([Role.Admin, Role.Auditor])).toBe(true);
    expect(auth.hasAnyRole([Role.Admin])).toBe(false);
  });

  it('picks up a refreshed token', () => {
    signIn({ sub: 'user-1', roles: ['employee'] });

    keycloak.tokenParsed = { sub: 'user-1', roles: ['employee', 'admin'] };
    keycloakEvent.set({ type: KeycloakEventType.AuthRefreshSuccess });

    expect(auth.roles()).toEqual([Role.Employee, Role.Admin]);
  });

  it('is anonymous again after logout', () => {
    signIn({ sub: 'user-1', roles: ['employee'] });

    keycloak.authenticated = false;
    keycloak.tokenParsed = undefined;
    keycloakEvent.set({ type: KeycloakEventType.AuthLogout });

    expect(auth.authenticated()).toBe(false);
    expect(auth.roles()).toEqual([]);
  });

  it('logs in coming back to the given path under the base href', async () => {
    await auth.login('/promotions?page=2');

    const baseUri = TestBed.inject(DOCUMENT).baseURI;
    expect(keycloak.login).toHaveBeenCalledWith({
      redirectUri: new URL('promotions?page=2', baseUri).href,
    });
  });

  it('logs out coming back to the base href', async () => {
    await auth.logout();

    expect(keycloak.logout).toHaveBeenCalledWith({
      redirectUri: TestBed.inject(DOCUMENT).baseURI,
    });
  });
});
