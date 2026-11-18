import {
  ACTION_TYPES,
  CONDITION_TYPES,
  FieldKind,
  FieldSpec,
  SentencePart,
  SUBTOTAL_TYPES,
} from './rule-catalog';
import { ActionDraft, LeafDraft, Values } from './rule-draft';

/** A piece of a condition or action read out as a sentence. */
export type Segment =
  | { readonly kind: 'text'; readonly text: string }
  | { readonly kind: 'verb'; readonly text: string; readonly negative: boolean }
  /** A field's values, empty while the field hasn't been filled in. */
  | {
      readonly kind: 'value';
      readonly fieldKind: FieldKind;
      readonly label: string;
      readonly values: readonly string[];
    };

/** "Promo code is SUMMER", as segments. */
export function describeCondition(leaf: LeafDraft): readonly Segment[] {
  const spec = CONDITION_TYPES.get(leaf.type);
  if (!spec) {
    return [{ kind: 'text', text: `Unknown condition ${leaf.type}` }];
  }
  const operator = spec.operators.find((option) => option.value === leaf.operator);
  return read(spec.sentence, spec.fields, leaf.values, {
    text: operator?.label ?? leaf.operator ?? '',
    negative: operator?.negative ?? false,
  });
}

/** The action's benefit ("20% off") and what it applies to ("every line of product PROD-1"). */
export function describeAction(action: ActionDraft): {
  readonly headline: readonly Segment[];
  readonly target: readonly Segment[];
} {
  const spec = action.type ? ACTION_TYPES.get(action.type) : undefined;
  if (!spec) {
    return {
      headline: [
        { kind: 'text', text: action.type ? `Unknown action ${action.type}` : 'No action yet' },
      ],
      target: [],
    };
  }
  return {
    headline: read(spec.headline, spec.fields, action.values, null),
    target: read(spec.target, spec.fields, action.values, null),
  };
}

/** The segments as plain text, e.g. for a label. */
export function segmentsText(segments: readonly Segment[]): string {
  return segments
    .map((segment) =>
      segment.kind === 'value'
        ? segment.values.length > 0
          ? segment.values.join(', ')
          : '…'
        : segment.text,
    )
    .join(' ');
}

/** How a value is shown: a percentage with its sign. */
export function displayValue(kind: FieldKind, value: string): string {
  return kind === 'percent' ? `${value}%` : value;
}

function read(
  parts: readonly SentencePart[],
  fields: readonly FieldSpec[],
  values: Values,
  verb: { readonly text: string; readonly negative: boolean } | null,
): readonly Segment[] {
  return parts.flatMap((part): Segment[] => {
    if (typeof part === 'string') {
      return [{ kind: 'text', text: part }];
    }
    if ('verb' in part) {
      return verb ? [{ kind: 'verb', ...verb }] : [];
    }
    const field = fields.find((candidate) => candidate.key === part.field);
    if (!field) {
      return [];
    }
    const value = values[field.key] ?? '';
    if (field.kind === 'subtotalType') {
      const type = SUBTOTAL_TYPES.find((option) => option.value === value) ?? SUBTOTAL_TYPES[0];
      return [{ kind: 'text', text: `(${type.label})` }];
    }
    const listed = (typeof value === 'string' ? [value] : value)
      .map((item) => item.trim())
      .filter((item) => item !== '')
      .map((item) => displayValue(field.kind, item));
    return [{ kind: 'value', fieldKind: field.kind, label: field.label, values: listed }];
  });
}
