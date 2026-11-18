import {
  Action,
  ActionType,
  ConditionNode,
  LeafConditionType,
  LogicalOperator,
  MAX_INT,
  Operator,
} from '../promotion.model';
import {
  ACTION_TYPES,
  CONDITION_TYPES,
  FieldKind,
  FieldSpec,
  isListKind,
  isNumberKind,
} from './rule-catalog';

/**
 * A rule's conditions and action while they're edited, and when they're shown: every value is kept as the form
 * text (a percentage as `20` rather than `0.2`), so a half-finished rule can be shown too. Drafts are immutable;
 * the functions below return changed copies.
 */

export type FieldValue = string | readonly string[];
export type Values = Readonly<Record<string, FieldValue>>;

export interface GroupDraft {
  readonly kind: 'group';
  /** Identifies the node while it's edited. */
  readonly key: string;
  readonly operator: LogicalOperator;
  readonly children: readonly ConditionDraft[];
}

export interface LeafDraft {
  readonly kind: 'leaf';
  readonly key: string;
  /** A `LeafConditionType`, or a type the dashboard doesn't know, which can be shown but not saved. */
  readonly type: string;
  /** Null for a type without operators. */
  readonly operator: Operator | null;
  readonly values: Values;
}

export type ConditionDraft = GroupDraft | LeafDraft;

export interface ActionDraft {
  /** An `ActionType`, null until one is chosen. */
  readonly type: string | null;
  readonly values: Values;
}

/** What's wrong with a node: one message per field, and one about the node as a whole. */
export interface NodeErrors {
  readonly fields: Readonly<Record<string, string>>;
  readonly node: string | null;
}

let lastKey = 0;

function nextKey(): string {
  lastKey += 1;
  return `n${lastKey}`;
}

function emptyValues(fields: readonly FieldSpec[]): Values {
  return Object.fromEntries(
    fields.map((field) => [
      field.key,
      isListKind(field.kind) ? [] : field.kind === 'subtotalType' ? 'GROSS' : '',
    ]),
  );
}

export function newLeaf(type: LeafConditionType): LeafDraft {
  const spec = CONDITION_TYPES.get(type)!;
  return {
    kind: 'leaf',
    key: nextKey(),
    type,
    operator: spec.operators[0]?.value ?? null,
    values: emptyValues(spec.fields),
  };
}

export function newGroup(
  operator: LogicalOperator = 'AND',
  children: readonly ConditionDraft[] = [],
): GroupDraft {
  return { kind: 'group', key: nextKey(), operator, children };
}

export function newAction(type: ActionType | null = null): ActionDraft {
  return { type, values: type ? emptyValues(ACTION_TYPES.get(type)!.fields) : {} };
}

/** The editor always has a group at the top, so a rule with a single condition gets one around it. */
export function rootFromCondition(node: ConditionNode): GroupDraft {
  const draft = fromCondition(node);
  return draft.kind === 'group' ? draft : newGroup('AND', [draft]);
}

export function fromCondition(node: ConditionNode): ConditionDraft {
  if (node.type === 'COMPOSITE') {
    return newGroup(
      node.operator,
      (node.children ?? []).map((child) => fromCondition(child)),
    );
  }
  const fields = CONDITION_TYPES.get(node.type)?.fields ?? [];
  return {
    kind: 'leaf',
    key: nextKey(),
    type: node.type,
    operator: node.operator ?? null,
    values: readValues(fields, node),
  };
}

export function fromAction(action: Action): ActionDraft {
  const fields = ACTION_TYPES.get(action.type)?.fields ?? [];
  return { type: action.type, values: readValues(fields, action) };
}

function readValues(
  fields: readonly FieldSpec[],
  source: Readonly<Record<string, unknown>>,
): Values {
  return Object.fromEntries(
    fields.map((field) => [field.key, readValue(field.kind, source[field.key])]),
  );
}

function readValue(kind: FieldKind, raw: unknown): FieldValue {
  if (isListKind(kind)) {
    return Array.isArray(raw) ? raw.map(String) : [];
  }
  if (kind === 'subtotalType') {
    return raw === 'NET' ? 'NET' : 'GROSS';
  }
  if (kind === 'percent') {
    return typeof raw === 'number' ? String(Math.round(raw * 10_000) / 100) : '';
  }
  return typeof raw === 'number' || typeof raw === 'string' ? String(raw) : '';
}

// Checks

const INTEGER = /^\d+$/;
const DECIMAL = /^\d+(\.\d+)?$/;
const PERCENT = /^\d+(\.\d{1,2})?$/;

/** What's wrong with the value, as promo-x-core would reject it, or null. */
export function fieldError(field: FieldSpec, value: FieldValue): string | null {
  if (typeof value !== 'string') {
    if (value.length > 0) {
      return null;
    }
    return field.kind === 'products'
      ? 'Choose at least one product.'
      : field.kind === 'categories'
        ? 'Choose at least one category.'
        : 'Add at least one.';
  }
  const text = value.trim();
  if (field.kind === 'subtotalType') {
    return null;
  }
  if (text === '') {
    return field.kind === 'product'
      ? 'Choose a product.'
      : field.kind === 'category'
        ? 'Choose a category.'
        : `${field.label} is required.`;
  }
  switch (field.kind) {
    case 'quantity': {
      const number = Number(text);
      return INTEGER.test(text) && number >= 1 && number <= MAX_INT
        ? null
        : 'A whole number, 1 or more.';
    }
    case 'threshold':
      return DECIMAL.test(text) ? null : 'A number, 0 or more.';
    case 'amount':
      return DECIMAL.test(text) && Number(text) > 0 ? null : 'A number greater than 0.';
    case 'percent': {
      const number = Number(text);
      return PERCENT.test(text) && number > 0 && number <= 100
        ? null
        : 'More than 0 and at most 100, with up to 2 decimals.';
    }
    default:
      return null;
  }
}

function valuesErrors(fields: readonly FieldSpec[], values: Values): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of fields) {
    const error = fieldError(field, values[field.key] ?? '');
    if (error) {
      errors[field.key] = error;
    }
  }
  return errors;
}

/** The nodes that can't be saved as they are, by key. */
export function conditionErrors(root: GroupDraft): ReadonlyMap<string, NodeErrors> {
  const errors = new Map<string, NodeErrors>();
  const visit = (node: ConditionDraft, isRoot: boolean): void => {
    if (node.kind === 'group') {
      if (node.children.length === 0) {
        errors.set(node.key, {
          fields: {},
          node: isRoot ? 'Add at least one condition.' : 'A group needs at least one condition.',
        });
      }
      node.children.forEach((child) => visit(child, false));
      return;
    }
    const spec = CONDITION_TYPES.get(node.type);
    if (!spec) {
      errors.set(node.key, {
        fields: {},
        node: 'The dashboard can’t edit this kind of condition.',
      });
      return;
    }
    const fields = valuesErrors(spec.fields, node.values);
    const operatorMissing =
      spec.operators.length > 0 && !spec.operators.some((o) => o.value === node.operator);
    if (Object.keys(fields).length > 0 || operatorMissing) {
      errors.set(node.key, { fields, node: operatorMissing ? 'Choose how to compare.' : null });
    }
  };
  visit(root, true);
  return errors;
}

export function actionErrors(action: ActionDraft): NodeErrors | null {
  const spec = action.type ? ACTION_TYPES.get(action.type) : undefined;
  if (!spec) {
    return {
      fields: {},
      node: action.type
        ? 'The dashboard can’t edit this kind of action.'
        : 'Choose what the rule does.',
    };
  }
  const fields = valuesErrors(spec.fields, action.values);
  return Object.keys(fields).length > 0 ? { fields, node: null } : null;
}

// Sending

/**
 * The node sent as `conditionTree`: the top group, or its only child, so a rule with a single condition is saved
 * the way it was loaded.
 */
export function sentRoot(root: GroupDraft): ConditionDraft {
  return root.children.length === 1 ? root.children[0] : root;
}

/** The tree in promo-x-core's form. Call it only when `conditionErrors` is empty. */
export function toCondition(root: GroupDraft): ConditionNode {
  return serialize(sentRoot(root));
}

function serialize(node: ConditionDraft): ConditionNode {
  if (node.kind === 'group') {
    return { type: 'COMPOSITE', operator: node.operator, children: node.children.map(serialize) };
  }
  const spec = CONDITION_TYPES.get(node.type)!;
  return {
    type: spec.type,
    ...(node.operator ? { operator: node.operator } : {}),
    ...writeValues(spec.fields, node.values),
  };
}

/** The action in promo-x-core's form. Call it only when `actionErrors` is null. */
export function toAction(action: ActionDraft): Action {
  const spec = ACTION_TYPES.get(action.type!)!;
  return { type: spec.type, ...writeValues(spec.fields, action.values) };
}

function writeValues(fields: readonly FieldSpec[], values: Values): Record<string, unknown> {
  return Object.fromEntries(
    fields.map((field) => [field.key, writeValue(field.kind, values[field.key])]),
  );
}

function writeValue(kind: FieldKind, value: FieldValue | undefined): unknown {
  if (typeof value !== 'string') {
    return [...new Set((value ?? []).map((item) => item.trim()).filter((item) => item !== ''))];
  }
  const text = value.trim();
  if (kind === 'percent') {
    // 12.5 → 0.125, without floating-point noise such as 0.12500000000000003
    return Math.round(Number(text) * 100) / 10_000;
  }
  return isNumberKind(kind) ? Number(text) : text;
}

// Changing the tree

export function findNode(root: GroupDraft, key: string): ConditionDraft | null {
  if (root.key === key) {
    return root;
  }
  for (const child of root.children) {
    const found = child.kind === 'group' ? findNode(child, key) : child.key === key ? child : null;
    if (found) {
      return found;
    }
  }
  return null;
}

/** Replaces the node with this key by what `change` returns. */
export function updateNode(
  root: GroupDraft,
  key: string,
  change: (node: ConditionDraft) => ConditionDraft,
): GroupDraft {
  const visit = (node: ConditionDraft): ConditionDraft => {
    if (node.key === key) {
      return change(node);
    }
    if (node.kind === 'leaf') {
      return node;
    }
    const children = node.children.map(visit);
    return children.every((child, i) => child === node.children[i]) ? node : { ...node, children };
  };
  return visit(root) as GroupDraft;
}

export function addChild(root: GroupDraft, groupKey: string, child: ConditionDraft): GroupDraft {
  return updateNode(root, groupKey, (node) =>
    node.kind === 'group' ? { ...node, children: [...node.children, child] } : node,
  );
}

/** Removes the node with this key, anywhere below the top group. */
export function removeNode(root: GroupDraft, key: string): GroupDraft {
  const visit = (group: GroupDraft): GroupDraft => {
    const kept = group.children.filter((child) => child.key !== key);
    const children = kept.map((child) => (child.kind === 'group' ? visit(child) : child));
    return kept.length === group.children.length &&
      children.every((c, i) => c === group.children[i])
      ? group
      : { ...group, children };
  };
  return visit(root);
}

export function countConditions(node: ConditionDraft): number {
  return node.kind === 'leaf'
    ? 1
    : node.children.reduce((sum, child) => sum + countConditions(child), 0);
}

// Server errors

/**
 * The key of the node a `path` of promo-x-core's errors points at, such as `conditionTree` or
 * `conditionTree.children.[1].children.[0]`, given the tree that was sent. Null when it points elsewhere.
 */
export function keyAtPath(root: GroupDraft, path: string | null): string | null {
  if (!path?.startsWith('conditionTree')) {
    return null;
  }
  let node: ConditionDraft | undefined = sentRoot(root);
  for (const [, index] of path.matchAll(/\[(\d+)\]/g)) {
    node = node?.kind === 'group' ? node.children[Number(index)] : undefined;
  }
  return node?.key ?? null;
}

/**
 * For each leaf with a field of the given kind that holds one of `ids`, those IDs by field key. Used to point
 * at the external IDs promo-x-core says don't exist.
 */
export function leavesReferencing(
  root: GroupDraft,
  matches: (kind: FieldKind) => boolean,
  ids: ReadonlySet<string>,
): ReadonlyMap<string, Readonly<Record<string, readonly string[]>>> {
  const found = new Map<string, Record<string, readonly string[]>>();
  const visit = (node: ConditionDraft): void => {
    if (node.kind === 'group') {
      node.children.forEach(visit);
      return;
    }
    const fields = CONDITION_TYPES.get(node.type)?.fields ?? [];
    const hits = referencing(fields, node.values, matches, ids);
    if (Object.keys(hits).length > 0) {
      found.set(node.key, hits);
    }
  };
  visit(root);
  return found;
}

/** The values of the given kind that are in `ids`, by field key. */
export function referencing(
  fields: readonly FieldSpec[],
  values: Values,
  matches: (kind: FieldKind) => boolean,
  ids: ReadonlySet<string>,
): Record<string, readonly string[]> {
  const hits: Record<string, readonly string[]> = {};
  for (const field of fields) {
    if (!matches(field.kind)) {
      continue;
    }
    const value = values[field.key] ?? '';
    const listed = (typeof value === 'string' ? [value] : value).map((item) => item.trim());
    const missing = listed.filter((item) => ids.has(item));
    if (missing.length > 0) {
      hits[field.key] = missing;
    }
  }
  return hits;
}
