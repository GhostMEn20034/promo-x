import { Signal } from '@angular/core';
import { Role } from './role';

export interface AuthUser {
  /** The token's `sub`. */
  readonly id: string;
  readonly username: string;
  readonly name?: string;
  readonly email?: string;
}

/**
 * The auth module's public API. The rest of the app reads the signed-in user and their roles through it and
 * never talks to the identity provider directly.
 */
export abstract class AuthApi {
  abstract readonly authenticated: Signal<boolean>;
  abstract readonly user: Signal<AuthUser | null>;
  abstract readonly roles: Signal<readonly Role[]>;

  /** True when the user has at least one of the given roles. */
  abstract hasAnyRole(roles: readonly Role[]): boolean;

  /** Sends the user to the identity provider's login page, coming back to `redirectPath` (the current URL by default). */
  abstract login(redirectPath?: string): Promise<void>;

  /** Ends the identity provider session and comes back to the app's root. */
  abstract logout(): Promise<void>;
}
