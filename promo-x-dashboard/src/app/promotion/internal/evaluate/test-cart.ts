import { ProductRef } from '../../../product';
import { EvaluationRequest } from '../promotion.model';
import { FieldSpec } from '../rule/rule-catalog';
import { fieldError } from '../rule/rule-draft';

/**
 * The test cart as the form holds it, with numbers as their text, and its translation to the body of
 * `POST /api/promotion/evaluate`.
 */

export interface CartLine {
  readonly product: ProductRef;
  readonly quantity: string;
  readonly unitPrice: string;
}

export interface TestCart {
  readonly lines: readonly CartLine[];
  readonly promoCodes: readonly string[];
  /** Blank for an anonymous guest. */
  readonly customerId: string;
  readonly sessionId: string;
  readonly shippingCost: string;
}

export const EMPTY_CART: TestCart = {
  lines: [],
  promoCodes: [],
  customerId: '',
  sessionId: '',
  shippingCost: '0',
};

const QUANTITY: FieldSpec = { key: 'quantity', label: 'Quantity', kind: 'quantity' };
const UNIT_PRICE: FieldSpec = { key: 'unitPrice', label: 'Unit price', kind: 'threshold' };
const SHIPPING: FieldSpec = { key: 'shippingCost', label: 'Shipping cost', kind: 'threshold' };

export interface CartErrors {
  /** By the line's product ID. */
  readonly lines: ReadonlyMap<number, { readonly quantity?: string; readonly unitPrice?: string }>;
  readonly shippingCost: string | null;
  readonly cart: string | null;
}

/** What promo-x-core would reject, or null when the cart can be sent. */
export function cartErrors(cart: TestCart, maxItems: number): CartErrors | null {
  const lines = new Map<number, { quantity?: string; unitPrice?: string }>();
  for (const line of cart.lines) {
    const quantity = fieldError(QUANTITY, line.quantity) ?? undefined;
    const unitPrice = fieldError(UNIT_PRICE, line.unitPrice) ?? undefined;
    if (quantity || unitPrice) {
      lines.set(line.product.id, { quantity, unitPrice });
    }
  }
  const shippingCost = fieldError(SHIPPING, cart.shippingCost);
  const cartError =
    cart.lines.length === 0
      ? 'Add at least one product.'
      : cart.lines.length > maxItems
        ? `At most ${maxItems} products.`
        : null;
  return lines.size > 0 || shippingCost || cartError
    ? { lines, shippingCost, cart: cartError }
    : null;
}

/**
 * The request for the cart. Usages count right away (`reserveUsage: false`), so there's nothing to confirm
 * afterwards. Values that don't parse yet become 0, for the example in the docs.
 */
export function toRequest(cart: TestCart): EvaluationRequest {
  const customerId = cart.customerId.trim();
  const sessionId = cart.sessionId.trim();
  return {
    ...(sessionId ? { sessionId } : {}),
    ...(customerId ? { customerId } : {}),
    promoCodes: cart.promoCodes,
    items: cart.lines.map((line) => ({
      productExternalId: line.product.externalId,
      quantity: toNumber(line.quantity),
      unitPrice: toNumber(line.unitPrice),
    })),
    shippingCost: toNumber(cart.shippingCost),
    reserveUsage: false,
  };
}

/** A curl command that sends the request, with the token left to the caller as `$TOKEN`. */
export function curlCommand(
  apiUrl: string,
  storefrontId: number,
  request: EvaluationRequest,
): string {
  const body = JSON.stringify(request, null, 2).replaceAll("'", "'\\''");
  return [
    `curl -X POST '${apiUrl.replace(/\/+$/, '')}/api/promotion/evaluate' \\`,
    `  -H "Authorization: Bearer $TOKEN" \\`,
    `  -H 'X-Storefront-Id: ${storefrontId}' \\`,
    `  -H 'Content-Type: application/json' \\`,
    `  -d '${body}'`,
  ].join('\n');
}

function toNumber(text: string): number {
  const number = Number(text.trim());
  return Number.isFinite(number) ? number : 0;
}
