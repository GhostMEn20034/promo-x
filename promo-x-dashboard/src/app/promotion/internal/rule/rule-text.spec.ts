import { fromAction, fromCondition, LeafDraft, newAction, newLeaf } from './rule-draft';
import { describeAction, describeCondition, segmentsText } from './rule-text';

describe('rule sentences', () => {
  function sentence(node: Parameters<typeof fromCondition>[0]): string {
    return segmentsText(describeCondition(fromCondition(node) as LeafDraft));
  }

  it('reads conditions out with their operator', () => {
    expect(sentence({ type: 'PROMO_CODE', code: 'SUMMER', operator: 'NOT_EQUALS' })).toBe(
      'Promo code is not SUMMER',
    );
    expect(
      sentence({
        type: 'SUBTOTAL',
        threshold: 100,
        operator: 'GREATER_THAN_OR_EQUAL',
        subtotalType: 'GROSS',
      }),
    ).toBe('Cart subtotal (before discounts) is at least 100');
    expect(sentence({ type: 'CUSTOMER_AUTHENTICATION_STATE', operator: 'EQUALS' })).toBe(
      'Customer is signed in',
    );
    expect(
      sentence({ type: 'PRODUCT_SKU_ANY_MATCH', skus: ['A', 'B'], operator: 'NOT_EQUALS' }),
    ).toBe('Cart contains none of the SKUs A, B');
  });

  it('marks an exclusion as negative', () => {
    const verb = describeCondition(
      fromCondition({
        type: 'SINGLE_PRODUCT_SKU_MATCH',
        sku: 'A',
        operator: 'NOT_EQUALS',
      }) as LeafDraft,
    ).find((segment) => segment.kind === 'verb');

    expect(verb).toEqual({ kind: 'verb', text: 'does not contain', negative: true });
  });

  it('shows a missing value as a placeholder', () => {
    const segments = describeCondition(newLeaf('PRODUCT_EXTERNAL_ID_QUANTITY'));

    expect(segmentsText(segments)).toBe('Quantity of product … is at least …');
    expect(segments[1]).toMatchObject({ kind: 'value', label: 'Product', values: [] });
  });

  it('reads actions out as a benefit and what it applies to', () => {
    const discount = describeAction(
      fromAction({
        type: 'PRODUCT_PERCENTAGE_DISCOUNT_ACTION',
        targetSku: 'SKU-1',
        discountPercentage: 0.2,
      }),
    );
    expect(segmentsText(discount.headline)).toBe('20% off');
    expect(segmentsText(discount.target)).toBe('every line with SKU SKU-1');

    const free = describeAction(
      fromAction({
        type: 'PRODUCT_EXTERNAL_ID_BUY_X_GET_X_FREE_ACTION',
        targetProductExternalId: 'P-1',
        buyQuantity: 2,
        freeQuantity: 1,
      }),
    );
    expect(segmentsText(free.headline)).toBe('Buy 2 get 1 free');

    expect(segmentsText(describeAction(newAction()).headline)).toBe('No action yet');
  });
});
