export interface AuthConfig {
  /** Keycloak base URL, e.g. `http://localhost:8080`. */
  readonly url: string;
  readonly realm: string;
  /** A public client with the standard flow (authorization code + PKCE) enabled. */
  readonly clientId: string;
  /** Base URLs whose requests get the `Authorization: Bearer` header. No other request gets the token. */
  readonly bearerTokenUrls: readonly string[];
  /** Inactivity after which an expiring token is not refreshed and the user is logged out. 30 minutes by default. */
  readonly inactivityTimeoutMs?: number;
}
