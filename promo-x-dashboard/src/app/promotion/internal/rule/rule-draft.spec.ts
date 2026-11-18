import { ConditionNode } from '../promotion.model';
import { isProductKind } from './rule-catalog';
import {
  actionErrors,
  addChild,
  conditionErrors,
  fromAction,
  fromCondition,
  GroupDraft,
  keyAtPath,
  LeafDraft,
  leavesReferencing,
  newAction,
  newGroup,
  newLeaf,
  removeNode,
  rootFromCondition,
  toAction,
  toCondition,
  updateNode,
} from './rule-draft';

describe('rule drafts', () => {
  const tree: ConditionNode = {
    type: 'COMPOSITE',
    operator: 'AND',
    children: [
      { type: 'PROMO_CODE', code: 'SUMMER', operator: 'EQUALS' },
      {
        type: 'COMPOSITE',
        operator: 'OR',
        children: [
          {
            type: 'SUBTOTAL',
            threshold: 100.0,
            operator: 'GREATER_THAN_OR_EQUAL',
            subtotalType: 'NET',
          },
          {
            type: 'PRODUCT_EXTERNAL_ID_ANY_MATCH',
            productExternalIds: ['P-1', 'P-2'],
            operator: 'EQUALS',
          },
        ],
      },
    ],
  };

  function leaf(root: GroupDraft, ...path: number[]): LeafDraft {
    let node: GroupDraft | LeafDraft = root;
    for (const index of path) {
      node = (node as GroupDraft).children[index] as GroupDraft | LeafDraft;
    }
    return node as LeafDraft;
  }

  it('reads a tree as text values and writes it back unchanged', () => {
    const root = rootFromCondition(tree);

    expect(leaf(root, 1, 0).values).toEqual({ threshold: '100', subtotalType: 'NET' });
    expect(leaf(root, 1, 1).values).toEqual({ productExternalIds: ['P-1', 'P-2'] });
    expect(toCondition(root)).toEqual(tree);
  });

  it('wraps a single condition in a group, and unwraps it when sending', () => {
    const single: ConditionNode = { type: 'CUSTOMER_AUTHENTICATION_STATE', operator: 'NOT_EQUALS' };
    const root = rootFromCondition(single);

    expect(root.kind).toBe('group');
    expect(root.children).toHaveLength(1);
    expect(toCondition(root)).toEqual(single);
  });

  it('turns percentages into fractions and back', () => {
    const action = fromAction({
      type: 'PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION',
      targetProductExternalId: 'P-1',
      discountPercentage: 0.125,
    });

    expect(action.values).toEqual({ targetProductExternalId: 'P-1', discountPercentage: '12.5' });
    expect(toAction({ ...action, values: { ...action.values, discountPercentage: '7' } })).toEqual({
      type: 'PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION',
      targetProductExternalId: 'P-1',
      discountPercentage: 0.07,
    });
  });

  it('sends no operator for a type without one, and trims and dedupes lists', () => {
    const collections = {
      ...newLeaf('PRODUCT_COLLECTION_ANY_MATCH'),
      values: { collections: [' summer ', 'summer', 'sale'] },
    };

    expect(toCondition(newGroup('AND', [collections]))).toEqual({
      type: 'PRODUCT_COLLECTION_ANY_MATCH',
      collections: ['summer', 'sale'],
    });
  });

  it('finds what promo-x-core would reject', () => {
    const code = newLeaf('PROMO_CODE');
    const quantity = {
      ...newLeaf('PRODUCT_SKU_QUANTITY'),
      values: { sku: 'SKU-1', requiredQuantity: '1.5' },
    };
    const emptyGroup = newGroup('OR');
    const root = newGroup('AND', [code, quantity, emptyGroup]);

    const errors = conditionErrors(root);

    expect(errors.get(code.key)!.fields).toEqual({ code: 'Code is required.' });
    expect(errors.get(quantity.key)!.fields).toEqual({
      requiredQuantity: 'A whole number, 1 or more.',
    });
    expect(errors.get(emptyGroup.key)!.node).toBe('A group needs at least one condition.');
    const top = newGroup();
    expect(conditionErrors(top).get(top.key)!.node).toBe('Add at least one condition.');
  });

  it('checks the action fields', () => {
    expect(actionErrors(newAction())!.node).toBe('Choose what the rule does.');
    expect(actionErrors(newAction('FREE_SHIPPING_ACTION'))).toBeNull();

    const tooMuch = { type: 'SHIPPING_PERCENTAGE_DISCOUNT', values: { percentage: '120' } };
    expect(actionErrors(tooMuch)!.fields).toEqual({
      percentage: 'More than 0 and at most 100, with up to 2 decimals.',
    });
    const flat = { type: 'ORDER_FLAT_DISCOUNT', values: { discountAmount: '0' } };
    expect(actionErrors(flat)!.fields).toEqual({ discountAmount: 'A number greater than 0.' });
  });

  it('adds, changes and removes nodes without touching the others', () => {
    const code = newLeaf('PROMO_CODE');
    const nested = newGroup('OR');
    let root = newGroup('AND', [code, nested]);

    root = addChild(root, nested.key, newLeaf('SINGLE_PRODUCT_SKU_MATCH'));
    expect((root.children[1] as GroupDraft).children).toHaveLength(1);
    expect(root.children[0]).toBe(code);

    root = updateNode(root, code.key, (node) => ({ ...node, operator: 'NOT_EQUALS' }) as LeafDraft);
    expect((root.children[0] as LeafDraft).operator).toBe('NOT_EQUALS');

    root = removeNode(root, nested.key);
    expect(root.children.map((child) => child.key)).toEqual([code.key]);
  });

  it('finds the node an error path points at, in the tree that was sent', () => {
    const root = rootFromCondition(tree);

    expect(keyAtPath(root, 'conditionTree')).toBe(root.key);
    expect(keyAtPath(root, 'conditionTree.children.[1].children.[0]')).toBe(leaf(root, 1, 0).key);
    expect(keyAtPath(root, 'conditionTree.children[0]')).toBe(leaf(root, 0).key);
    expect(keyAtPath(root, 'conditionTree.children.[5]')).toBeNull();
    expect(keyAtPath(root, 'action')).toBeNull();

    // A single condition is sent on its own, so `conditionTree` is that condition
    const single = rootFromCondition({ type: 'PROMO_CODE', code: 'X', operator: 'EQUALS' });
    expect(keyAtPath(single, 'conditionTree')).toBe(single.children[0].key);
  });

  it('finds the leaves holding missing product external IDs', () => {
    const root = rootFromCondition(tree);

    const found = leavesReferencing(root, isProductKind, new Set(['P-2', 'P-9']));

    expect([...found]).toEqual([[leaf(root, 1, 1).key, { productExternalIds: ['P-2'] }]]);
  });

  it('keeps a type it doesn’t know, which can be shown but not saved', () => {
    const unknown = fromCondition({ type: 'WEATHER' as never, operator: 'EQUALS' });

    expect(unknown).toMatchObject({ kind: 'leaf', type: 'WEATHER', values: {} });
    expect(conditionErrors(newGroup('AND', [unknown])).get(unknown.key)!.node).toBe(
      'The dashboard can’t edit this kind of condition.',
    );
  });
});
