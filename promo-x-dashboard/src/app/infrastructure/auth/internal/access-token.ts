import { KeycloakTokenParsed } from 'keycloak-js';
import { AuthUser } from '../auth.api';
import { isRole, Role } from '../role';

/**
 * Reads the roles the same way promo-x-core does: from the access token's top-level `roles` claim.
 * Keycloak's `realm_access` and `resource_access` are ignored, so the UI never shows what the backend would refuse.
 */
export function rolesOf(token: KeycloakTokenParsed): Role[] {
  const claim: unknown = token['roles'];
  return Array.isArray(claim) ? claim.filter(isRole) : [];
}

export function userOf(token: KeycloakTokenParsed): AuthUser {
  const id = token.sub ?? '';
  return {
    id,
    username: stringClaim(token, 'preferred_username') ?? id,
    name: stringClaim(token, 'name'),
    email: stringClaim(token, 'email'),
  };
}

function stringClaim(token: KeycloakTokenParsed, name: string): string | undefined {
  const value: unknown = token[name];
  return typeof value === 'string' && value !== '' ? value : undefined;
}
