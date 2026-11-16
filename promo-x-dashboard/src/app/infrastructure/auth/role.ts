/**
 * The roles promo-x-core checks, as they appear in the access token's top-level `roles` claim.
 * Mirrors the backend's `Roles`.
 */
export const Role = {
  IntegrationClient: 'integration-client',
  PromotionManager: 'promotion-manager',
  Employee: 'employee',
  Auditor: 'auditor',
  Admin: 'admin',
} as const;

export type Role = (typeof Role)[keyof typeof Role];

const ROLES: ReadonlySet<string> = new Set(Object.values(Role));

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && ROLES.has(value);
}
