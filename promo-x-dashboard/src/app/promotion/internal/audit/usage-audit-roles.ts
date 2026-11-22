import { Role } from '../../../infrastructure/auth';

/** The roles promo-x-core lets read the usage audit. */
export const USAGE_AUDIT_ROLES: readonly Role[] = [Role.PromotionManager, Role.Auditor, Role.Admin];
