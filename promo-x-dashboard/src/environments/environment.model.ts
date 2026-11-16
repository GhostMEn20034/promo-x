/**
 * The shape both environment files follow. It lives in its own file because `environment.ts` is swapped for
 * `environment.development.ts` in development builds, so neither can import the other.
 */
export interface Environment {
  /** promo-x-core's base URL. */
  readonly apiUrl: string;
  readonly auth: {
    /** Keycloak's base URL. */
    readonly url: string;
    readonly realm: string;
    readonly clientId: string;
  };
}
