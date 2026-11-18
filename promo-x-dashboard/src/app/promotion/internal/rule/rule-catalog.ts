import { ActionType, LeafConditionType, Operator } from '../promotion.model';

/**
 * The condition and action types promo-x-core knows (its `ConditionNodeRegistry` and `ActionStrategyRegistry`),
 * with what the dashboard needs to edit them and to read them out as sentences.
 */

/** How a field is edited, checked, sent and shown. */
export type FieldKind =
  // One text value
  | 'code'
  | 'product'
  | 'sku'
  | 'category'
  | 'collection'
  // A non-empty list of text values
  | 'products'
  | 'skus'
  | 'categories'
  | 'collections'
  /** An integer, at least 1. */
  | 'quantity'
  /** A decimal, at least 0. */
  | 'threshold'
  /** A decimal, more than 0. */
  | 'amount'
  /** More than 0 and at most 100 in the form; promo-x-core takes the fraction, more than 0 and at most 1. */
  | 'percent'
  | 'subtotalType';

export interface FieldSpec {
  /** The field's name in promo-x-core's JSON. */
  readonly key: string;
  readonly label: string;
  readonly kind: FieldKind;
  readonly placeholder?: string;
}

/** A sentence is read out from text, the operator's verb and field values, in order. */
export type SentencePart = string | { readonly verb: true } | { readonly field: string };

const VERB = { verb: true } as const;

export interface OperatorOption {
  readonly value: Operator;
  /** The verb in the sentence, e.g. "is at least". */
  readonly label: string;
  /** Shown in a warning colour, so an exclusion stands out. */
  readonly negative?: boolean;
}

export type ConditionFamily = 'checkout' | 'cart' | 'product' | 'sku' | 'category' | 'collection';

export interface ConditionTypeSpec {
  readonly type: LeafConditionType;
  readonly family: ConditionFamily;
  readonly label: string;
  readonly description: string;
  /** Empty when the type takes no operator. The first is the default. */
  readonly operators: readonly OperatorOption[];
  readonly fields: readonly FieldSpec[];
  readonly sentence: readonly SentencePart[];
}

export interface FamilySpec {
  readonly label: string;
  readonly icon: IconName;
  /** Tailwind classes of the family's icon tile. */
  readonly tile: string;
  readonly note?: string;
}

export type IconName =
  | 'ticket'
  | 'user'
  | 'receipt'
  | 'box'
  | 'barcode'
  | 'folder'
  | 'layers'
  | 'percent'
  | 'gift'
  | 'cart'
  | 'truck';

export const CONDITION_FAMILIES: Readonly<Record<ConditionFamily, FamilySpec>> = {
  checkout: { label: 'Checkout', icon: 'ticket', tile: 'bg-violet-100 text-violet-700' },
  cart: { label: 'Cart value', icon: 'receipt', tile: 'bg-emerald-100 text-emerald-700' },
  product: { label: 'Products', icon: 'box', tile: 'bg-blue-100 text-blue-700' },
  sku: { label: 'SKUs', icon: 'barcode', tile: 'bg-cyan-100 text-cyan-700' },
  category: { label: 'Categories', icon: 'folder', tile: 'bg-amber-100 text-amber-700' },
  collection: {
    label: 'Collections',
    icon: 'layers',
    tile: 'bg-slate-200 text-slate-600',
    note: "Products have no collections yet, so these conditions don't match anything for now.",
  },
};

const CONTAINS: readonly OperatorOption[] = [
  { value: 'EQUALS', label: 'contains' },
  { value: 'NOT_EQUALS', label: 'does not contain', negative: true },
];
const QUANTITY: readonly OperatorOption[] = [
  { value: 'GREATER_THAN_OR_EQUAL', label: 'is at least' },
  { value: 'EQUALS', label: 'is exactly' },
];

const conditionTypes: readonly ConditionTypeSpec[] = [
  {
    type: 'PROMO_CODE',
    family: 'checkout',
    label: 'Promo code',
    description: 'The shopper entered a promo code, or didn’t.',
    operators: [
      { value: 'EQUALS', label: 'is' },
      { value: 'NOT_EQUALS', label: 'is not', negative: true },
    ],
    fields: [{ key: 'code', label: 'Code', kind: 'code', placeholder: 'SUMMER' }],
    sentence: ['Promo code', VERB, { field: 'code' }],
  },
  {
    type: 'CUSTOMER_AUTHENTICATION_STATE',
    family: 'checkout',
    label: 'Customer sign-in',
    description: 'The shopper is a registered customer, or an anonymous guest.',
    operators: [
      { value: 'EQUALS', label: 'is signed in' },
      { value: 'NOT_EQUALS', label: 'is a guest' },
    ],
    fields: [],
    sentence: ['Customer', VERB],
  },
  {
    type: 'SUBTOTAL',
    family: 'cart',
    label: 'Cart subtotal',
    description:
      'The items subtotal compared to an amount, before or after earlier rules’ discounts.',
    operators: [
      { value: 'GREATER_THAN_OR_EQUAL', label: 'is at least' },
      { value: 'EQUALS', label: 'is exactly' },
      { value: 'NOT_EQUALS', label: 'is not', negative: true },
    ],
    fields: [
      { key: 'threshold', label: 'Amount', kind: 'threshold', placeholder: '100.00' },
      { key: 'subtotalType', label: 'Measured', kind: 'subtotalType' },
    ],
    sentence: ['Cart subtotal', { field: 'subtotalType' }, VERB, { field: 'threshold' }],
  },
  {
    type: 'SINGLE_PRODUCT_EXTERNAL_ID_MATCH',
    family: 'product',
    label: 'Product in cart',
    description: 'A specific product is in the cart, or isn’t.',
    operators: CONTAINS,
    fields: [{ key: 'productExternalId', label: 'Product', kind: 'product' }],
    sentence: ['Cart', VERB, 'product', { field: 'productExternalId' }],
  },
  {
    type: 'PRODUCT_EXTERNAL_ID_ANY_MATCH',
    family: 'product',
    label: 'Any of several products',
    description: 'At least one product from a list is in the cart, or none of them is.',
    operators: [
      { value: 'EQUALS', label: 'contains any of' },
      { value: 'NOT_EQUALS', label: 'contains none of', negative: true },
    ],
    fields: [{ key: 'productExternalIds', label: 'Products', kind: 'products' }],
    sentence: ['Cart', VERB, 'the products', { field: 'productExternalIds' }],
  },
  {
    type: 'PRODUCT_EXTERNAL_ID_QUANTITY',
    family: 'product',
    label: 'Product quantity',
    description: 'How many units of a product the cart holds, across its lines.',
    operators: QUANTITY,
    fields: [
      { key: 'productExternalId', label: 'Product', kind: 'product' },
      { key: 'requiredQuantity', label: 'Quantity', kind: 'quantity', placeholder: '2' },
    ],
    sentence: [
      'Quantity of product',
      { field: 'productExternalId' },
      VERB,
      { field: 'requiredQuantity' },
    ],
  },
  {
    type: 'SINGLE_PRODUCT_SKU_MATCH',
    family: 'sku',
    label: 'SKU in cart',
    description: 'A product with a specific SKU is in the cart, or isn’t.',
    operators: CONTAINS,
    fields: [{ key: 'sku', label: 'SKU', kind: 'sku' }],
    sentence: ['Cart', VERB, 'SKU', { field: 'sku' }],
  },
  {
    type: 'PRODUCT_SKU_ANY_MATCH',
    family: 'sku',
    label: 'Any of several SKUs',
    description: 'At least one SKU from a list is in the cart, or none of them is.',
    operators: [
      { value: 'EQUALS', label: 'contains any of' },
      { value: 'NOT_EQUALS', label: 'contains none of', negative: true },
    ],
    fields: [{ key: 'skus', label: 'SKUs', kind: 'skus' }],
    sentence: ['Cart', VERB, 'the SKUs', { field: 'skus' }],
  },
  {
    type: 'PRODUCT_SKU_QUANTITY',
    family: 'sku',
    label: 'SKU quantity',
    description: 'How many units with a SKU the cart holds, across its lines.',
    operators: QUANTITY,
    fields: [
      { key: 'sku', label: 'SKU', kind: 'sku' },
      { key: 'requiredQuantity', label: 'Quantity', kind: 'quantity', placeholder: '2' },
    ],
    sentence: ['Quantity of SKU', { field: 'sku' }, VERB, { field: 'requiredQuantity' }],
  },
  {
    type: 'SINGLE_PRODUCT_CATEGORY_MATCH',
    family: 'category',
    label: 'Category in cart',
    description: 'A product from a category is in the cart, or none is.',
    operators: CONTAINS,
    fields: [{ key: 'category', label: 'Category', kind: 'category' }],
    sentence: ['Cart', VERB, 'a product from category', { field: 'category' }],
  },
  {
    type: 'PRODUCT_CATEGORY_ANY_MATCH',
    family: 'category',
    label: 'Any of several categories',
    description: 'A product from at least one of the categories is in the cart.',
    operators: [{ value: 'EQUALS', label: 'contains' }],
    fields: [{ key: 'categories', label: 'Categories', kind: 'categories' }],
    sentence: ['Cart', VERB, 'a product from any of the categories', { field: 'categories' }],
  },
  {
    type: 'SINGLE_PRODUCT_COLLECTION_MATCH',
    family: 'collection',
    label: 'Collection in cart',
    description: 'A product from a collection is in the cart, or none is.',
    operators: CONTAINS,
    fields: [{ key: 'collection', label: 'Collection', kind: 'collection' }],
    sentence: ['Cart', VERB, 'a product from collection', { field: 'collection' }],
  },
  {
    type: 'PRODUCT_COLLECTION_ANY_MATCH',
    family: 'collection',
    label: 'Any of several collections',
    description: 'A product from at least one of the collections is in the cart.',
    operators: [],
    fields: [{ key: 'collections', label: 'Collections', kind: 'collections' }],
    sentence: ['Cart contains a product from any of the collections', { field: 'collections' }],
  },
];

export const CONDITION_TYPES: ReadonlyMap<string, ConditionTypeSpec> = new Map(
  conditionTypes.map((spec) => [spec.type, spec]),
);

/** The condition types by family, in the order the picker shows them. */
export const CONDITION_TYPES_BY_FAMILY: readonly {
  readonly family: ConditionFamily;
  readonly types: readonly ConditionTypeSpec[];
}[] = (Object.keys(CONDITION_FAMILIES) as ConditionFamily[]).map((family) => ({
  family,
  types: conditionTypes.filter((spec) => spec.family === family),
}));

export type ActionFamily = 'item' | 'free' | 'order' | 'shipping';

export const ACTION_FAMILIES: Readonly<Record<ActionFamily, FamilySpec>> = {
  item: { label: 'Item discounts', icon: 'percent', tile: 'bg-blue-100 text-blue-700' },
  free: { label: 'Free items', icon: 'gift', tile: 'bg-pink-100 text-pink-700' },
  order: { label: 'Order discounts', icon: 'cart', tile: 'bg-emerald-100 text-emerald-700' },
  shipping: { label: 'Shipping', icon: 'truck', tile: 'bg-sky-100 text-sky-700' },
};

export interface ActionTypeSpec {
  readonly type: ActionType;
  readonly family: ActionFamily;
  readonly label: string;
  readonly description: string;
  readonly fields: readonly FieldSpec[];
  /** The benefit, in large type. */
  readonly headline: readonly SentencePart[];
  /** What the benefit applies to. */
  readonly target: readonly SentencePart[];
}

const DISCOUNT: FieldSpec = {
  key: 'discountPercentage',
  label: 'Discount',
  kind: 'percent',
  placeholder: '20',
};
const BUY: FieldSpec = { key: 'buyQuantity', label: 'Buy', kind: 'quantity', placeholder: '2' };
const FREE: FieldSpec = {
  key: 'freeQuantity',
  label: 'Get free',
  kind: 'quantity',
  placeholder: '1',
};

const actionTypes: readonly ActionTypeSpec[] = [
  {
    type: 'PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION',
    family: 'item',
    label: 'Percentage off a product',
    description: 'Takes a percentage off every line of the product.',
    fields: [{ key: 'targetProductExternalId', label: 'Product', kind: 'product' }, DISCOUNT],
    headline: [{ field: 'discountPercentage' }, 'off'],
    target: ['every line of product', { field: 'targetProductExternalId' }],
  },
  {
    type: 'PRODUCT_PERCENTAGE_DISCOUNT_ACTION',
    family: 'item',
    label: 'Percentage off a SKU',
    description: 'Takes a percentage off every line with the SKU.',
    fields: [{ key: 'targetSku', label: 'SKU', kind: 'sku' }, DISCOUNT],
    headline: [{ field: 'discountPercentage' }, 'off'],
    target: ['every line with SKU', { field: 'targetSku' }],
  },
  {
    type: 'PRODUCT_EXTERNAL_ID_BUY_X_GET_X_FREE_ACTION',
    family: 'free',
    label: 'Buy X, get Y free (product)',
    description: 'For every X units of the product bought, Y more are free, repeating.',
    fields: [{ key: 'targetProductExternalId', label: 'Product', kind: 'product' }, BUY, FREE],
    headline: ['Buy', { field: 'buyQuantity' }, 'get', { field: 'freeQuantity' }, 'free'],
    target: ['of product', { field: 'targetProductExternalId' }, 'repeating'],
  },
  {
    type: 'PRODUCT_SKU_BUY_X_GET_X_FREE_ACTION',
    family: 'free',
    label: 'Buy X, get Y free (SKU)',
    description: 'For every X units with the SKU bought, Y more are free, repeating.',
    fields: [{ key: 'targetSku', label: 'SKU', kind: 'sku' }, BUY, FREE],
    headline: ['Buy', { field: 'buyQuantity' }, 'get', { field: 'freeQuantity' }, 'free'],
    target: ['of SKU', { field: 'targetSku' }, 'repeating'],
  },
  {
    type: 'ORDER_PERCENTAGE_DISCOUNT',
    family: 'order',
    label: 'Percentage off the order',
    description: 'Takes a percentage off every line of the order.',
    fields: [DISCOUNT],
    headline: [{ field: 'discountPercentage' }, 'off'],
    target: ['the whole order'],
  },
  {
    type: 'ORDER_FLAT_DISCOUNT',
    family: 'order',
    label: 'Fixed amount off the order',
    description:
      'Takes an amount off the order, spread across the lines in proportion to their prices.',
    fields: [{ key: 'discountAmount', label: 'Amount', kind: 'amount', placeholder: '10.00' }],
    headline: [{ field: 'discountAmount' }, 'off'],
    target: ['the order, spread across its lines'],
  },
  {
    type: 'FREE_SHIPPING_ACTION',
    family: 'shipping',
    label: 'Free shipping',
    description: 'Shipping costs nothing.',
    fields: [],
    headline: ['Free shipping'],
    target: ['on the whole order'],
  },
  {
    type: 'SHIPPING_PERCENTAGE_DISCOUNT',
    family: 'shipping',
    label: 'Percentage off shipping',
    description: 'Takes a percentage off the shipping cost.',
    fields: [{ ...DISCOUNT, key: 'percentage' }],
    headline: [{ field: 'percentage' }, 'off'],
    target: ['shipping'],
  },
];

export const ACTION_TYPES: ReadonlyMap<string, ActionTypeSpec> = new Map(
  actionTypes.map((spec) => [spec.type, spec]),
);

export const ACTION_TYPE_LIST: readonly ActionTypeSpec[] = actionTypes;

const LIST_KINDS: ReadonlySet<FieldKind> = new Set([
  'products',
  'skus',
  'categories',
  'collections',
]);
const NUMBER_KINDS: ReadonlySet<FieldKind> = new Set([
  'quantity',
  'threshold',
  'amount',
  'percent',
]);

export function isListKind(kind: FieldKind): boolean {
  return LIST_KINDS.has(kind);
}

export function isNumberKind(kind: FieldKind): boolean {
  return NUMBER_KINDS.has(kind);
}

/** Whether the field holds product external IDs, which promo-x-core checks exist. */
export function isProductKind(kind: FieldKind): boolean {
  return kind === 'product' || kind === 'products';
}

/** Whether the field holds category external IDs, which promo-x-core checks exist. */
export function isCategoryKind(kind: FieldKind): boolean {
  return kind === 'category' || kind === 'categories';
}

export const SUBTOTAL_TYPES = [
  { value: 'GROSS', label: 'before discounts' },
  { value: 'NET', label: 'after earlier discounts' },
] as const;
