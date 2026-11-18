import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { Observable } from 'rxjs';
import { Category, CategoryPickerDialog, CategoryRef } from '../../../category';
import { Product, ProductPickerDialog, ProductRef } from '../../../product';
import { problemDetailOf } from '../../../infrastructure/http';
import { trimmedLength } from '../../../shared/forms';
import { PromotionClient } from '../promotion.client';
import {
  ActionType,
  LeafConditionType,
  LogicalOperator,
  MAX_INT,
  Operator,
  PromotionRuleDetails,
  RULE_NAME_MAX_LENGTH,
  SavedPromotionRule,
} from '../promotion.model';
import {
  ACTION_FAMILIES,
  ACTION_TYPE_LIST,
  ACTION_TYPES,
  ActionTypeSpec,
  CONDITION_FAMILIES,
  CONDITION_TYPES,
  ConditionTypeSpec,
  FamilySpec,
  FieldKind,
  isCategoryKind,
  isListKind,
  isNumberKind,
  isProductKind,
  SUBTOTAL_TYPES,
} from '../rule/rule-catalog';
import {
  ActionDraft,
  actionErrors,
  addChild,
  conditionErrors,
  countConditions,
  FieldValue,
  fromAction,
  GroupDraft,
  keyAtPath,
  LeafDraft,
  leavesReferencing,
  newAction,
  newGroup,
  newLeaf,
  NodeErrors,
  referencing,
  removeNode,
  rootFromCondition,
  toAction,
  toCondition,
  updateNode,
} from '../rule/rule-draft';
import { RuleFlow } from '../ui/rule-flow';
import { RuleIcon } from '../ui/rule-icon';
import { ConditionPickerDialog } from './condition-picker-dialog';
import { TokenInput } from './token-input';

/** The owner of the action's fields, next to the condition keys. */
const ACTION = '$action';
/** Groups can be nested this deep below the top one. */
const MAX_DEPTH = 3;

/** The form's text fields promo-x-core's errors can point at. */
type BasicField = 'name' | 'priority' | 'usageLimit';

function positiveInt(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  if (value === null) {
    return null;
  }
  return Number.isInteger(value) && value >= 1 && value <= MAX_INT ? null : { positiveInt: true };
}

/**
 * The rule builder: name and flags, the condition tree (IF), the action (THEN), and on create the usage limit,
 * with a live read-out of the rule next to it. Creates a rule (`rule` is null) or replaces one; the usage limit
 * of an existing rule is changed on its details page instead.
 */
@Component({
  selector: 'app-rule-editor',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    RuleFlow,
    RuleIcon,
    TokenInput,
    ConditionPickerDialog,
    CategoryPickerDialog,
    ProductPickerDialog,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rule-editor.html',
})
export class RuleEditor implements OnInit {
  readonly storefrontId = input.required<number>();
  /** The rule to edit, or null to create one. */
  readonly rule = input<PromotionRuleDetails | null>(null);
  readonly saved = output<SavedPromotionRule>();
  readonly cancelled = output<void>();

  private readonly client = inject(PromotionClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [trimmedLength(1, RULE_NAME_MAX_LENGTH)]],
    priority: new FormControl<number | null>(null, [Validators.required, positiveInt]),
    isActive: [true],
    isShortCircuit: [false],
    hasUsageLimit: [false],
    /** Enabled, and so checked, only while `hasUsageLimit` is on. */
    usageLimit: new FormControl<number | null>({ value: null, disabled: true }, [
      Validators.required,
      positiveInt,
    ]),
  });
  protected readonly basics = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly condition = signal<GroupDraft>(newGroup('AND'));
  protected readonly action = signal<ActionDraft>(newAction());
  /** Set by the first Save, after which every problem is shown. */
  protected readonly attempted = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly serverConditionErrors = signal<ReadonlyMap<string, NodeErrors>>(new Map());
  private readonly serverActionErrors = signal<NodeErrors | null>(null);

  /** The group a condition is being added to. */
  protected readonly addingTo = signal<string | null>(null);
  /** The field whose categories are being picked. */
  protected readonly pickingCategories = signal<{
    readonly owner: string;
    readonly field: string;
    readonly multiple: boolean;
  } | null>(null);

  /** The field whose products are being picked. */
  protected readonly pickingProducts = signal<{
    readonly owner: string;
    readonly field: string;
    readonly multiple: boolean;
  } | null>(null);
  /**
   * The categories picked here, by external ID. The rule only has the external IDs of its categories, so those
   * are shown without a name until they're picked again.
   */
  protected readonly knownCategories = signal<ReadonlyMap<string, CategoryRef>>(new Map());
  /** The known categories of the field being picked, so the picker marks them. */
  protected readonly categoriesBeingPicked = computed<readonly CategoryRef[]>(() => {
    const target = this.pickingCategories();
    if (!target) {
      return [];
    }
    const value = this.valueOf(target.owner, target.field);
    return (typeof value === 'string' ? [value] : value).flatMap((externalId) => {
      const category = this.knownCategories().get(externalId);
      return category ? [category] : [];
    });
  });
  /** The products the fields hold, by external ID: the rule's, then the ones picked. */
  protected readonly knownProducts = signal<ReadonlyMap<string, ProductRef>>(new Map());
  /** The picked products of the field being picked, so the picker marks them. */
  protected readonly productsBeingPicked = computed<readonly ProductRef[]>(() => {
    const target = this.pickingProducts();
    if (!target) {
      return [];
    }
    const value = this.valueOf(target.owner, target.field);
    return (typeof value === 'string' ? [value] : value).flatMap((externalId) => {
      const product = this.knownProducts().get(externalId);
      return product ? [product] : [];
    });
  });

  protected readonly editing = computed(() => this.rule() !== null);
  protected readonly conditionCount = computed(() => countConditions(this.condition()));

  protected readonly conditionErrorsShown = computed(() => {
    const merged = new Map(this.serverConditionErrors());
    if (this.attempted()) {
      for (const [key, errors] of conditionErrors(this.condition())) {
        const server = merged.get(key);
        merged.set(key, {
          fields: { ...server?.fields, ...errors.fields },
          node: errors.node ?? server?.node ?? null,
        });
      }
    }
    return merged;
  });
  protected readonly actionErrorsShown = computed(() => {
    const client = this.attempted() ? actionErrors(this.action()) : null;
    const server = this.serverActionErrors();
    if (!client && !server) {
      return null;
    }
    return {
      fields: { ...server?.fields, ...client?.fields },
      node: client?.node ?? server?.node ?? null,
    };
  });

  protected readonly actionTypes = ACTION_TYPE_LIST;
  protected readonly actionFamilies = ACTION_FAMILIES;
  protected readonly subtotalTypes = SUBTOTAL_TYPES;
  protected readonly maxDepth = MAX_DEPTH;
  protected readonly actionOwner = ACTION;
  protected readonly isList = isListKind;
  protected readonly isNumber = isNumberKind;
  protected readonly isCategory = isCategoryKind;
  protected readonly isProduct = isProductKind;
  protected readonly categoryUnavailable = (category: Category): string | null =>
    category.externalId ? null : 'No external ID';

  constructor() {
    this.form.controls.hasUsageLimit.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((limited) => {
        const limit = this.form.controls.usageLimit;
        if (limited) {
          limit.enable();
        } else {
          limit.disable();
        }
      });
  }

  ngOnInit(): void {
    const rule = this.rule();
    if (!rule) {
      return;
    }
    // The usage limit isn't part of the update; it stays off and disabled here.
    this.form.patchValue({
      name: rule.name,
      priority: rule.priority,
      isActive: rule.isActive,
      isShortCircuit: rule.isShortCircuit,
    });
    this.condition.set(rootFromCondition(rule.conditionTree.node));
    this.action.set(fromAction(rule.action.action));
    this.knownProducts.set(new Map(rule.products.map((product) => [product.externalId, product])));
  }

  // Conditions

  protected addCondition(type: LeafConditionType): void {
    const group = this.addingTo();
    if (group) {
      this.condition.update((root) => addChild(root, group, newLeaf(type)));
    }
    this.addingTo.set(null);
  }

  /** A nested group combines the other way by default, as that's why groups are nested. */
  protected addGroup(group: GroupDraft): void {
    this.condition.update((root) =>
      addChild(root, group.key, newGroup(group.operator === 'AND' ? 'OR' : 'AND')),
    );
  }

  protected remove(key: string): void {
    this.condition.update((root) => removeNode(root, key));
    this.clearServerError(key);
  }

  protected setGroupOperator(key: string, operator: LogicalOperator): void {
    this.condition.update((root) =>
      updateNode(root, key, (node) => (node.kind === 'group' ? { ...node, operator } : node)),
    );
  }

  protected setOperator(key: string, operator: string): void {
    this.condition.update((root) =>
      updateNode(root, key, (node) =>
        node.kind === 'leaf' ? { ...node, operator: operator as Operator } : node,
      ),
    );
    this.clearServerError(key);
  }

  // Action

  /** Values of fields both types have, such as the discount, are kept. */
  protected chooseAction(type: ActionType): void {
    this.action.update((current) => {
      if (current.type === type) {
        return current;
      }
      const next = newAction(type);
      const values = Object.fromEntries(
        Object.entries(next.values).map(([key, empty]) => [key, current.values[key] ?? empty]),
      );
      return { type, values };
    });
    this.serverActionErrors.set(null);
  }

  // Fields of conditions and the action

  protected setValue(owner: string, field: string, value: FieldValue): void {
    if (owner === ACTION) {
      this.action.update((action) => ({ ...action, values: { ...action.values, [field]: value } }));
      this.serverActionErrors.set(null);
      return;
    }
    this.condition.update((root) =>
      updateNode(root, owner, (node) =>
        node.kind === 'leaf' ? { ...node, values: { ...node.values, [field]: value } } : node,
      ),
    );
    this.clearServerError(owner);
  }

  protected valueOf(owner: string, field: string): FieldValue {
    const values = owner === ACTION ? this.action().values : (this.leafOf(owner)?.values ?? {});
    return values[field] ?? '';
  }

  protected listOf(owner: string, field: string): readonly string[] {
    const value = this.valueOf(owner, field);
    return typeof value === 'string' ? [] : value;
  }

  protected errorOf(owner: string, field: string): string | null {
    const errors =
      owner === ACTION ? this.actionErrorsShown() : this.conditionErrorsShown().get(owner);
    return errors?.fields[field] ?? null;
  }

  protected nodeErrorOf(key: string): string | null {
    return this.conditionErrorsShown().get(key)?.node ?? null;
  }

  protected hasErrors(key: string): boolean {
    return this.conditionErrorsShown().has(key);
  }

  protected categoryOf(externalId: FieldValue): CategoryRef | null {
    return typeof externalId === 'string' ? (this.knownCategories().get(externalId) ?? null) : null;
  }

  /** Rules refer to categories by external ID, so that's what the field holds. */
  protected onCategoryPicked(category: Category): void {
    const target = this.pickingCategories();
    if (target && category.externalId) {
      this.rememberCategories([category]);
      this.setValue(target.owner, target.field, category.externalId);
    }
    this.pickingCategories.set(null);
  }

  /**
   * The picker returns every chosen category, the kept ones included, so it replaces the list. An external ID the
   * picker couldn't show as chosen, because its category wasn't picked here, stays until it's removed.
   */
  protected onCategoriesPicked(categories: readonly CategoryRef[]): void {
    const target = this.pickingCategories();
    if (target) {
      const unknown = this.listOf(target.owner, target.field).filter(
        (externalId) => !this.knownCategories().has(externalId),
      );
      this.rememberCategories(categories);
      const picked = categories.flatMap((category) =>
        category.externalId ? [category.externalId] : [],
      );
      this.setValue(target.owner, target.field, [...new Set([...picked, ...unknown])]);
    }
    this.pickingCategories.set(null);
  }

  private rememberCategories(categories: readonly CategoryRef[]): void {
    this.knownCategories.update((known) => {
      const next = new Map(known);
      for (const { id, externalId, name } of categories) {
        if (externalId) {
          next.set(externalId, { id, externalId, name });
        }
      }
      return next;
    });
  }

  protected productOf(externalId: FieldValue): ProductRef | null {
    return typeof externalId === 'string' ? (this.knownProducts().get(externalId) ?? null) : null;
  }

  protected onProductPicked(product: Product): void {
    const target = this.pickingProducts();
    if (target) {
      this.remember([product]);
      this.setValue(target.owner, target.field, product.externalId);
    }
    this.pickingProducts.set(null);
  }

  /**
   * The picker returns every chosen product, the kept ones included, so it replaces the list. An external ID
   * whose product isn't known, so the picker couldn't show it as chosen, stays until it's removed.
   */
  protected onProductsPicked(products: readonly ProductRef[]): void {
    const target = this.pickingProducts();
    if (target) {
      const unknown = this.listOf(target.owner, target.field).filter(
        (externalId) => !this.knownProducts().has(externalId),
      );
      this.remember(products);
      this.setValue(target.owner, target.field, [
        ...new Set([...products.map((product) => product.externalId), ...unknown]),
      ]);
    }
    this.pickingProducts.set(null);
  }

  protected removeFromList(owner: string, field: string, value: string): void {
    this.setValue(
      owner,
      field,
      this.listOf(owner, field).filter((item) => item !== value),
    );
  }

  private remember(products: readonly ProductRef[]): void {
    this.knownProducts.update((known) => {
      const next = new Map(known);
      for (const { id, externalId, sku, name } of products) {
        next.set(externalId, { id, externalId, sku, name });
      }
      return next;
    });
  }

  // Lookups for the template

  protected conditionSpecOf(leaf: LeafDraft): ConditionTypeSpec | null {
    return CONDITION_TYPES.get(leaf.type) ?? null;
  }

  protected conditionFamilyOf(spec: ConditionTypeSpec): FamilySpec {
    return CONDITION_FAMILIES[spec.family];
  }

  protected readonly actionSpec = computed<ActionTypeSpec | null>(() => {
    const type = this.action().type;
    return type ? (ACTION_TYPES.get(type) ?? null) : null;
  });

  protected fieldId(owner: string, field: string): string {
    return `rule-${owner === ACTION ? 'action' : owner}-${field}`;
  }

  protected isRef(kind: FieldKind): boolean {
    return !isNumberKind(kind) && kind !== 'subtotalType';
  }

  protected basicError(field: BasicField): string | null {
    const control = this.form.controls[field];
    if (!control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('server')) {
      return control.getError('server');
    }
    if (control.hasError('required')) {
      return field === 'name' ? 'Name is required.' : 'Required.';
    }
    if (control.hasError('length')) {
      return `At most ${control.getError('length').max} characters.`;
    }
    if (control.hasError('positiveInt')) {
      return 'A whole number, 1 or more.';
    }
    return null;
  }

  // Saving

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    const invalid =
      this.form.invalid ||
      conditionErrors(this.condition()).size > 0 ||
      actionErrors(this.action()) !== null;
    if (invalid) {
      this.formError.set('Some fields need attention.');
      return;
    }

    this.saving.set(true);
    this.formError.set(null);
    this.serverConditionErrors.set(new Map());
    this.serverActionErrors.set(null);
    this.request()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.saved.emit(saved);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.showError(error);
        },
      });
  }

  private request(): Observable<SavedPromotionRule> {
    const { name, priority, isActive, isShortCircuit, hasUsageLimit, usageLimit } =
      this.form.getRawValue();
    const body = {
      name: name.trim(),
      priority: priority!,
      isShortCircuit,
      isActive,
      conditionTree: toCondition(this.condition()),
      action: toAction(this.action()),
    };
    const rule = this.rule();
    if (rule) {
      return this.client.update(rule.id, body);
    }
    return this.client.create(this.storefrontId(), {
      ...body,
      hasUsageLimit,
      ...(hasUsageLimit ? { usageLimit: usageLimit! } : {}),
    });
  }

  /**
   * Errors in a condition or the action point at it (`conditionTree.children.[0]`, `action`), and missing
   * product or category external IDs are marked on the fields that hold them.
   */
  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 404) {
      this.formError.set('This rule no longer exists.');
      return;
    }
    if (status === 403) {
      this.formError.set(
        this.editing()
          ? "You're not allowed to edit rules."
          : "You're not allowed to create rules.",
      );
      return;
    }
    const problem = problemDetailOf(error);
    if (!problem) {
      this.formError.set("Couldn't save the rule. Try again.");
      return;
    }

    const conditions = new Map<string, NodeErrors>();
    let action = null as NodeErrors | null;
    const unmatched: string[] = [];
    let matched = false;
    const addToCondition = (key: string, fields: Record<string, string>, node: string | null) => {
      const current = conditions.get(key);
      conditions.set(key, {
        fields: { ...current?.fields, ...fields },
        node: node ?? current?.node ?? null,
      });
    };

    for (const item of problem.errors) {
      const missing = missingIds(item.code, item.errorContext);
      if (missing) {
        const { kind, ids } = missing;
        const message = (values: readonly string[]) =>
          `Not in this storefront: ${values.join(', ')}.`;
        let found = false;
        for (const [key, fields] of leavesReferencing(this.condition(), kind, ids)) {
          addToCondition(key, mapValues(fields, message), null);
          found = true;
        }
        const actionSpec = this.actionSpec();
        const actionHits = actionSpec
          ? referencing(actionSpec.fields, this.action().values, kind, ids)
          : {};
        if (Object.keys(actionHits).length > 0) {
          action = { fields: { ...action?.fields, ...mapValues(actionHits, message) }, node: null };
          found = true;
        }
        if (found) {
          matched = true;
        } else {
          unmatched.push(`${item.message}: ${[...ids].join(', ')}.`);
        }
        continue;
      }

      const path = item.path ?? '';
      const control = (['name', 'priority', 'usageLimit'] as const).find((field) => field === path);
      if (control) {
        this.form.controls[control].setErrors({ server: item.message });
        this.form.controls[control].markAsTouched();
        matched = true;
      } else if (path.startsWith('conditionTree') && keyAtPath(this.condition(), path)) {
        addToCondition(keyAtPath(this.condition(), path)!, {}, item.message);
        matched = true;
      } else if (path.startsWith('action')) {
        action = { fields: action?.fields ?? {}, node: item.message };
        matched = true;
      } else {
        unmatched.push(item.message);
      }
    }

    this.serverConditionErrors.set(conditions);
    this.serverActionErrors.set(action);
    this.formError.set(
      unmatched.length > 0 ? unmatched.join(' ') : matched ? 'Some fields need attention.' : null,
    );
  }

  private leafOf(key: string): LeafDraft | null {
    const find = (group: GroupDraft): LeafDraft | null => {
      for (const child of group.children) {
        const found = child.kind === 'group' ? find(child) : child.key === key ? child : null;
        if (found) {
          return found;
        }
      }
      return null;
    };
    return find(this.condition());
  }

  private clearServerError(key: string): void {
    if (this.serverConditionErrors().has(key)) {
      this.serverConditionErrors.update((errors) => {
        const next = new Map(errors);
        next.delete(key);
        return next;
      });
    }
  }
}

/** The external IDs a `*_NOT_FOUND` business error says are missing, and the kind of field that holds them. */
function missingIds(
  code: string,
  context: unknown,
): { readonly kind: (kind: FieldKind) => boolean; readonly ids: ReadonlySet<string> } | null {
  const key =
    code === 'PRODUCT_EXTERNAL_IDS_NOT_FOUND'
      ? 'missingProductExternalIds'
      : code === 'CATEGORY_EXTERNAL_IDS_NOT_FOUND'
        ? 'missingCategoryExternalIds'
        : null;
  if (!key || typeof context !== 'object' || context === null) {
    return null;
  }
  const ids = (context as Record<string, unknown>)[key];
  if (!Array.isArray(ids) || ids.length === 0) {
    return null;
  }
  return {
    kind: key === 'missingProductExternalIds' ? isProductKind : isCategoryKind,
    ids: new Set(ids.map(String)),
  };
}

function mapValues(
  record: Readonly<Record<string, readonly string[]>>,
  message: (values: readonly string[]) => string,
): Record<string, string> {
  return Object.fromEntries(Object.entries(record).map(([key, values]) => [key, message(values)]));
}
