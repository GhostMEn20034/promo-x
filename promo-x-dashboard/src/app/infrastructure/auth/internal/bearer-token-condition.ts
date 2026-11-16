import { IncludeBearerTokenCondition } from 'keycloak-angular';

/**
 * Matches the base URL itself and anything under it, but not a host that merely starts with it, such as
 * `http://localhost:8000.example.com`.
 */
export function bearerTokenCondition(baseUrl: string): IncludeBearerTokenCondition {
  const base = baseUrl.replace(/\/+$/, '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return { urlPattern: new RegExp(`^${base}(?:[/?#]|$)`, 'i') };
}
