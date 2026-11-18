import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CategoryRef } from '../../../category';
import { ProductRef } from '../../../product';
import {
  ACTION_FAMILIES,
  ACTION_TYPES,
  CONDITION_FAMILIES,
  CONDITION_TYPES,
  FamilySpec,
} from '../rule/rule-catalog';
import { ActionDraft, ConditionDraft, GroupDraft, LeafDraft, sentRoot } from '../rule/rule-draft';
import { describeAction, describeCondition, Segment } from '../rule/rule-text';
import { RuleIcon } from './rule-icon';
import { RuleSegments } from './rule-segments';

const UNKNOWN: FamilySpec = { label: 'Unknown', icon: 'layers', tile: 'bg-gray-100 text-gray-500' };

/**
 * A rule drawn as IF → THEN: the condition tree, each condition read out as a sentence and the groups joined by
 * ALL/ANY on a connector line, then the action as the outcome. It shows a half-finished draft too, with the
 * missing values as placeholders.
 */
@Component({
  selector: 'app-rule-flow',
  imports: [NgTemplateOutlet, RuleIcon, RuleSegments],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3">
      <div class="flex flex-col items-center">
        <span
          class="rounded-md bg-gray-900 px-2 py-1 text-[11px] font-bold tracking-widest text-white"
          >IF</span
        >
        <span
          class="mt-1 w-0.5 flex-1 rounded-full bg-linear-to-b from-gray-300 to-blue-400"
        ></span>
      </div>
      <div class="min-w-0 pb-6" data-testid="rule-conditions">
        @let shown = top();
        @if (shown.kind === 'group' && shown.children.length === 0) {
          <p
            class="rounded-lg border border-dashed border-gray-300 px-4 py-3 text-sm text-gray-400"
          >
            No conditions yet
          </p>
        } @else {
          <ng-container
            [ngTemplateOutlet]="nodeTemplate"
            [ngTemplateOutletContext]="{ $implicit: shown, depth: 0 }"
          />
        }
      </div>

      <div class="flex flex-col items-center">
        <span
          class="rounded-md bg-blue-600 px-2 py-1 text-[11px] font-bold tracking-widest text-white"
          >THEN</span
        >
      </div>
      <div class="min-w-0">
        @let outcome = describedAction();
        <div
          class="flex items-start gap-3 rounded-xl border border-blue-200 bg-linear-to-br from-blue-50 to-white p-4"
          data-testid="rule-action"
        >
          <span
            class="grid size-10 shrink-0 place-items-center rounded-lg p-2"
            [class]="actionFamily().tile"
          >
            <app-rule-icon class="size-6" [name]="actionFamily().icon" />
          </span>
          <div class="min-w-0">
            @if (actionLabel(); as label) {
              <p class="text-[11px] font-medium tracking-wide text-blue-700 uppercase">
                {{ label }}
              </p>
            }
            <p
              class="text-lg leading-snug font-semibold text-gray-900"
              data-testid="action-headline"
            >
              <app-rule-segments [segments]="outcome.headline" />
            </p>
            @if (outcome.target.length > 0) {
              <p class="mt-1 text-sm text-gray-600" data-testid="action-target">
                <app-rule-segments
                  [segments]="outcome.target"
                  [products]="products()"
                  [categories]="categories()"
                  [productLink]="productLink()"
                />
              </p>
            }
          </div>
        </div>
      </div>
    </div>

    <ng-template #nodeTemplate let-node let-depth="depth">
      @if (node.kind === 'group') {
        <div
          class="rounded-xl border border-gray-200 p-3"
          role="group"
          data-testid="condition-group"
          [class.bg-gray-50]="depth % 2 === 0"
          [class.bg-white]="depth % 2 === 1"
          [attr.aria-label]="(node.operator === 'AND' ? 'All' : 'Any') + ' of these conditions'"
        >
          <p class="flex items-center gap-2 text-xs text-gray-500">
            <span
              class="rounded-full px-2 py-0.5 font-bold tracking-wide"
              [class]="
                node.operator === 'AND'
                  ? 'bg-indigo-100 text-indigo-700'
                  : 'bg-orange-100 text-orange-700'
              "
              >{{ node.operator === 'AND' ? 'ALL' : 'ANY' }}</span
            >
            {{ node.operator === 'AND' ? 'of these must match' : 'of these is enough' }}
          </p>
          <ul class="mt-3">
            @for (child of node.children; track child.key; let last = $last) {
              <li class="relative pl-6" [class.pb-7]="!last">
                <span
                  class="absolute top-0 left-2 w-px bg-gray-300"
                  aria-hidden="true"
                  [class.h-full]="!last"
                  [class.h-6]="last"
                ></span>
                <span class="absolute top-6 left-2 h-px w-3 bg-gray-300" aria-hidden="true"></span>
                <ng-container
                  [ngTemplateOutlet]="nodeTemplate"
                  [ngTemplateOutletContext]="{ $implicit: child, depth: depth + 1 }"
                />
                @if (!last) {
                  <span
                    class="absolute bottom-3.5 left-2 -translate-x-1/2 translate-y-1/2 rounded-full border bg-white px-1.5 text-[10px] leading-4 font-bold"
                    aria-hidden="true"
                    [class]="
                      node.operator === 'AND'
                        ? 'border-indigo-200 text-indigo-600'
                        : 'border-orange-200 text-orange-600'
                    "
                    >{{ node.operator }}</span
                  >
                }
              </li>
            } @empty {
              <li class="text-sm text-gray-400 italic">No conditions in this group</li>
            }
          </ul>
        </div>
      } @else {
        @let family = familyOf(node);
        <div
          class="flex items-start gap-3 rounded-lg border border-gray-200 bg-white px-3 py-2 shadow-xs"
          data-testid="condition"
          [title]="descriptionOf(node)"
        >
          <span
            class="grid size-8 shrink-0 place-items-center rounded-md p-1.5"
            [class]="family.tile"
          >
            <app-rule-icon class="size-5" [name]="family.icon" />
          </span>
          <p class="min-w-0 py-1 text-sm text-gray-700" data-testid="condition-text">
            <app-rule-segments
              [segments]="describe(node)"
              [products]="products()"
              [categories]="categories()"
              [productLink]="productLink()"
            />
          </p>
        </div>
      }
    </ng-template>
  `,
})
export class RuleFlow {
  /** The top group. When it has a single condition, that condition is drawn on its own. */
  readonly condition = input.required<GroupDraft>();
  readonly action = input.required<ActionDraft>();
  /** The products the rule refers to, by external ID. */
  readonly products = input<ReadonlyMap<string, ProductRef>>(new Map());
  /** Categories whose names are known, by external ID. */
  readonly categories = input<ReadonlyMap<string, CategoryRef>>(new Map());
  /** The commands of the product list, e.g. `['../../products']`. Null: products aren't links. */
  readonly productLink = input<readonly unknown[] | null>(null);

  protected readonly top = computed<ConditionDraft>(() => sentRoot(this.condition()));
  protected readonly describedAction = computed(() => describeAction(this.action()));
  protected readonly actionFamily = computed(() => {
    const type = this.action().type;
    const spec = type ? ACTION_TYPES.get(type) : undefined;
    return spec ? ACTION_FAMILIES[spec.family] : UNKNOWN;
  });
  protected readonly actionLabel = computed(() => {
    const type = this.action().type;
    return type ? (ACTION_TYPES.get(type)?.label ?? null) : null;
  });

  protected describe(leaf: LeafDraft): readonly Segment[] {
    return describeCondition(leaf);
  }

  protected familyOf(leaf: LeafDraft): FamilySpec {
    const spec = CONDITION_TYPES.get(leaf.type);
    return spec ? CONDITION_FAMILIES[spec.family] : UNKNOWN;
  }

  protected descriptionOf(leaf: LeafDraft): string {
    return CONDITION_TYPES.get(leaf.type)?.description ?? '';
  }
}
