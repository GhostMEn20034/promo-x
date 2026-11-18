import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CategoryRef } from '../../../category';
import { FieldKind, isCategoryKind, isNumberKind, isProductKind } from '../rule/rule-catalog';
import { Segment } from '../rule/rule-text';
import { ProductRef } from '../../../product';

/**
 * A condition or action read out as a sentence: text, the operator's verb (an exclusion in red), and the values
 * as tokens. A product the rule refers to shows its name and links to it when it's in `products`.
 */
@Component({
  selector: 'app-rule-segments',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1' },
  template: `
    @for (segment of segments(); track $index) {
      @switch (segment.kind) {
        @case ('text') {
          <span>{{ segment.text }}</span>&ngsp;
        }
        @case ('verb') {
          <span
            class="font-semibold"
            [class.text-rose-600]="segment.negative"
            [class.text-gray-900]="!segment.negative"
            >{{ segment.text }}</span>&ngsp;
        }
        @case ('value') {
          @for (value of segment.values; track $index) {
            @if (isNumber(segment.fieldKind)) {
              <strong class="font-semibold text-gray-900 tabular-nums">{{ value }}</strong>&ngsp;
            } @else if (productOf(segment.fieldKind, value); as product) {
              @if (productLink(); as link) {
                <a
                  class="inline-flex items-baseline gap-1 rounded-md bg-blue-50 px-1.5 py-0.5 text-xs font-medium text-blue-800 ring-1 ring-blue-200 ring-inset hover:bg-blue-100"
                  [routerLink]="[...link, product.id]"
                  [title]="'SKU ' + product.sku"
                  >{{ product.name }}
                  <code class="font-mono text-[11px] font-normal text-blue-600">{{
                    value
                  }}</code></a
                >&ngsp;
              } @else {
                <span
                  class="inline-flex items-baseline gap-1 rounded-md bg-blue-50 px-1.5 py-0.5 text-xs font-medium text-blue-800 ring-1 ring-blue-200 ring-inset"
                  >{{ product.name }}
                  <code class="font-mono text-[11px] font-normal text-blue-600">{{
                    value
                  }}</code></span
                >&ngsp;
              }
            } @else if (categoryOf(segment.fieldKind, value); as category) {
              <span
                class="inline-flex items-baseline gap-1 rounded-md bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-amber-900 ring-1 ring-amber-200 ring-inset"
                >{{ category.name }}
                <code class="font-mono text-[11px] font-normal text-amber-700">{{
                  value
                }}</code></span
              >&ngsp;
            } @else {
              <code
                class="rounded-md bg-gray-100 px-1.5 py-0.5 font-mono text-xs text-gray-900 ring-1 ring-gray-200 ring-inset"
                >{{ value }}</code>&ngsp;
            }
          } @empty {
            <span
              class="rounded-md border border-dashed border-gray-300 px-1.5 py-0.5 text-xs font-normal text-gray-400"
              >{{ segment.label }}</span>&ngsp;
          }
        }
      }
    }
  `,
})
export class RuleSegments {
  readonly segments = input.required<readonly Segment[]>();
  /** The products the rule refers to, by external ID. */
  readonly products = input<ReadonlyMap<string, ProductRef>>(new Map());
  /** Categories whose names are known, by external ID. The others show their external ID only. */
  readonly categories = input<ReadonlyMap<string, CategoryRef>>(new Map());
  /** The commands of the product list, e.g. `['../../products']`. Null: products aren't links. */
  readonly productLink = input<readonly unknown[] | null>(null);

  protected isNumber = isNumberKind;

  protected categoryOf(kind: FieldKind, value: string): CategoryRef | null {
    return isCategoryKind(kind) ? (this.categories().get(value) ?? null) : null;
  }

  protected productOf(kind: FieldKind, value: string): ProductRef | null {
    return isProductKind(kind) ? (this.products().get(value) ?? null) : null;
  }
}
