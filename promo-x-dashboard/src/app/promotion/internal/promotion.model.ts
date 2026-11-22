/** A promotion rule as `GET /api/promotion/` lists it: without its condition tree and action. */
export interface PromotionRuleSummary {
  readonly id: number;
  readonly storefrontId: number;
  readonly name: string;
  /** Rules are evaluated from the highest priority down. */
  readonly priority: number;
  /** Once this rule changes the cart, lower-priority rules are skipped. */
  readonly isShortCircuit: boolean;
  /** Only active rules are evaluated. */
  readonly isActive: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** The operators of a leaf condition. Each condition type supports only some of them. */
export type Operator = 'EQUALS' | 'NOT_EQUALS' | 'GREATER_THAN_OR_EQUAL';

export type LogicalOperator = 'AND' | 'OR';

export type LeafConditionType =
  | 'PROMO_CODE'
  | 'CUSTOMER_AUTHENTICATION_STATE'
  | 'SUBTOTAL'
  | 'SINGLE_PRODUCT_EXTERNAL_ID_MATCH'
  | 'PRODUCT_EXTERNAL_ID_ANY_MATCH'
  | 'PRODUCT_EXTERNAL_ID_QUANTITY'
  | 'SINGLE_PRODUCT_SKU_MATCH'
  | 'PRODUCT_SKU_ANY_MATCH'
  | 'PRODUCT_SKU_QUANTITY'
  | 'SINGLE_PRODUCT_CATEGORY_MATCH'
  | 'PRODUCT_CATEGORY_ANY_MATCH'
  | 'SINGLE_PRODUCT_COLLECTION_MATCH'
  | 'PRODUCT_COLLECTION_ANY_MATCH';

export type ActionType =
  | 'PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION'
  | 'PRODUCT_PERCENTAGE_DISCOUNT_ACTION'
  | 'PRODUCT_EXTERNAL_ID_BUY_X_GET_X_FREE_ACTION'
  | 'PRODUCT_SKU_BUY_X_GET_X_FREE_ACTION'
  | 'ORDER_PERCENTAGE_DISCOUNT'
  | 'ORDER_FLAT_DISCOUNT'
  | 'FREE_SHIPPING_ACTION'
  | 'SHIPPING_PERCENTAGE_DISCOUNT';

/** Combines its children with AND (all match) or OR (any matches). */
export interface CompositeCondition {
  readonly type: 'COMPOSITE';
  readonly operator: LogicalOperator;
  readonly children: readonly ConditionNode[];
}

/** A condition on the cart or the shopper. Its other fields depend on the type, see `CONDITION_TYPES`. */
export interface LeafCondition {
  readonly type: LeafConditionType;
  readonly operator?: Operator;
  readonly [field: string]: unknown;
}

export type ConditionNode = CompositeCondition | LeafCondition;

/** What a rule does to the cart. Its other fields depend on the type, see `ACTION_TYPES`. */
export interface Action {
  readonly type: ActionType;
  readonly [field: string]: unknown;
}

/** Display metadata promo-x-core sends with a condition. */
export interface ConditionSpec {
  readonly displayName: string;
  readonly description: string;
  readonly supportedOperators: readonly Operator[];
  readonly operatorsDisplayName: Partial<Record<Operator, string>>;
}

/** Display metadata promo-x-core sends with an action. */
export interface ActionSpec {
  readonly displayName: string;
  readonly description: string;
}

/** The fields every rule response has. */
export interface PromotionRule {
  readonly id: number;
  readonly storefrontId: number;
  readonly name: string;
  readonly priority: number;
  readonly isShortCircuit: boolean;
  readonly isActive: boolean;
  /** Whether the rule may only be applied `usageLimit` times. */
  readonly hasUsageLimit: boolean;
  readonly usageLimit: number | null;
  /** Lowered shortly after each evaluation that applies the rule, so it can lag behind by a moment. */
  readonly remainingUses: number | null;
  /** The tree as it was sent, with each nested condition's `spec` added. */
  readonly conditionTree: { readonly node: ConditionNode; readonly spec: ConditionSpec };
  readonly action: { readonly action: Action; readonly spec: ActionSpec };
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** A product a rule refers to, as `GET /api/promotion/{id}` embeds it. */
export interface RuleProduct {
  readonly id: number;
  readonly externalId: string;
  readonly sku: string;
  readonly name: string;
  readonly storefrontId: number;
  readonly categoryIds: readonly number[];
}

/** `GET /api/promotion/{id}`: the rule with the products it refers to. */
export interface PromotionRuleDetails extends PromotionRule {
  readonly products: readonly RuleProduct[];
}

/** Create, update and the usage-limit change return the rule with the external IDs of its products. */
export interface SavedPromotionRule extends PromotionRule {
  readonly productExternalIds: readonly string[];
}

/** The body of `PUT /api/promotion/{id}`, which replaces every field but the usage limit. */
export interface PromotionRuleInput {
  readonly name: string;
  readonly priority: number;
  readonly isShortCircuit: boolean;
  readonly isActive: boolean;
  readonly conditionTree: ConditionNode;
  readonly action: Action;
}

/** The body of `POST /api/promotion/`. The remaining uses start at `usageLimit`. */
export interface NewPromotionRule extends PromotionRuleInput {
  readonly hasUsageLimit: boolean;
  readonly usageLimit?: number;
}

/** The body of `PATCH /api/promotion/{id}/usage-limits`. */
export interface UsageLimitsInput {
  readonly hasUsageLimit: boolean;
  readonly usageLimit: number;
  /** When false, `remainingUses` is ignored and the current count is kept. */
  readonly mutateRemainingUses: boolean;
  readonly remainingUses?: number;
}

export const RULE_NAME_MAX_LENGTH = 255;
/** Priorities and limits are Java `int`s. */
export const MAX_INT = 2_147_483_647;

/** A cart line of `POST /api/promotion/evaluate`. */
export interface EvaluationItemInput {
  readonly productExternalId: string;
  readonly quantity: number;
  /** The price of one unit, before promotions. */
  readonly unitPrice: number;
}

/** The body of `POST /api/promotion/evaluate`: the cart to price. */
export interface EvaluationRequest {
  readonly sessionId?: string;
  /** Left out for an anonymous guest. */
  readonly customerId?: string;
  readonly promoCodes: readonly string[];
  readonly items: readonly EvaluationItemInput[];
  readonly shippingCost: number;
  /** False: uses of usage-limited rules count right away, with nothing to confirm. */
  readonly reserveUsage: boolean;
}

export interface EvaluatedItem {
  readonly productExternalId: string;
  readonly productSku: string;
  readonly categories: readonly string[];
  readonly collections: readonly string[];
  readonly quantity: number;
  readonly unitPrice: number;
  readonly priceBeforeDiscount: number;
  readonly discountAmount: number;
  readonly priceAfterDiscount: number;
}

export interface RuleMention {
  readonly ruleId: number;
  readonly ruleName: string;
}

/** The usage an evaluation recorded. */
export interface EvaluationUsage {
  readonly id: string;
  readonly status: 'RESERVED' | 'USED' | 'CANCELED' | 'EXPIRED';
  readonly expiresAt: string | null;
}

/** The priced cart `POST /api/promotion/evaluate` returns. */
export interface EvaluationResult {
  readonly sessionId: string | null;
  readonly customerId: string | null;
  readonly promoCodes: readonly string[];
  readonly oldTotal: number;
  readonly newTotal: number;
  readonly totalDiscount: number;
  readonly itemsSubtotal: number;
  readonly itemsDiscount: number;
  readonly itemsTotal: number;
  readonly oldShippingCost: number;
  readonly shippingDiscount: number;
  readonly newShippingCost: number;
  readonly items: readonly EvaluatedItem[];
  /** Rules whose conditions matched, highest priority first, whether or not their action changed the cart. */
  readonly matchedRules: readonly RuleMention[];
  /** Rules whose action changed the cart, in the order they were applied. */
  readonly appliedRules: readonly RuleMention[];
  /** Null when no rule was applied. */
  readonly usage: EvaluationUsage | null;
}

/** promo-x-core takes 1 to 128 cart lines. */
export const EVALUATION_MAX_ITEMS = 128;

/** Totals of a storefront's confirmed (`USED`) usages over a period. */
export interface UsageTotals {
  readonly usageCount: number;
  /** Promo codes entered across those usages, including codes that matched no rule. */
  readonly enteredPromoCodeCount: number;
  /** Usages with a customer ID. */
  readonly registeredCustomerUsageCount: number;
  readonly anonymousCustomerUsageCount: number;
  /** Average order discount per usage. Null when there's no usage. */
  readonly averageTotalDiscount: number | null;
  readonly averageSubtotalDiscount: number | null;
  readonly averageShippingDiscount: number | null;
}

/** The usages of one UTC day. */
export interface UsageDailyCount {
  /** `YYYY-MM-DD`. */
  readonly date: string;
  readonly usageCount: number;
}

/** `GET /api/promotion/usage/stats`. */
export interface UsageStats {
  readonly totals: UsageTotals;
  /** One entry for every UTC day the period touches, oldest first, 0 for days without usage. */
  readonly dailyCounts: readonly UsageDailyCount[];
}

/** A rule an audited evaluation matched or applied, with its current name. */
export interface AuditedRule {
  readonly ruleId: number;
  readonly ruleName: string;
}

/** One evaluation that applied at least one rule, from `GET /api/promotion/usage/audit/`. */
export interface UsageAuditItem {
  /** The usage the evaluation recorded. */
  readonly usageId: string;
  /** The usage's current status. */
  readonly status: EvaluationUsage['status'];
  readonly sessionId: string | null;
  /** Null for an anonymous customer. */
  readonly customerId: string | null;
  readonly promoCodes: readonly string[];
  readonly subtotalBefore: number;
  readonly subtotalAfter: number;
  readonly shippingCostBefore: number;
  readonly shippingCostAfter: number;
  readonly totalBefore: number;
  readonly totalAfter: number;
  /** By the rules' current priority, highest first, whether or not they changed the cart. */
  readonly matchedRules: readonly AuditedRule[];
  /** In the order they were applied. */
  readonly appliedRules: readonly AuditedRule[];
  /** When the cart was evaluated. */
  readonly createdAt: string;
}

/** Which audits to read: a page, and optionally only those that matched or applied any of the rules. */
export interface UsageAuditQuery {
  /** 1-based. */
  readonly page: number;
  readonly size: number;
  readonly matchedRuleIds?: readonly number[];
  readonly appliedRuleIds?: readonly number[];
}
