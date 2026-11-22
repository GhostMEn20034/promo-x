import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { AuditRuleFilter, UsageAuditList } from './usage-audit-list';

type By = 'applied' | 'matched';

/**
 * A rule's audited evaluations, on its page: closed until asked for, so nothing is loaded otherwise. Then the
 * evaluations that applied the rule, or, switched, those that matched it.
 */
@Component({
  selector: 'app-rule-evaluations',
  imports: [UsageAuditList],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
      aria-labelledby="rule-evaluations-heading"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 id="rule-evaluations-heading" class="text-base font-semibold text-gray-900">
            Evaluations
          </h3>
          <p class="mt-0.5 text-sm text-gray-500">
            The cart evaluations that applied this rule, or that matched it. Times are in your time
            zone.
          </p>
        </div>
        @if (open()) {
          <button
            type="button"
            class="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            aria-controls="rule-evaluations-list"
            [attr.aria-expanded]="true"
            (click)="open.set(false)"
          >
            Hide
          </button>
        } @else {
          <button
            type="button"
            class="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
            aria-controls="rule-evaluations-list"
            [attr.aria-expanded]="false"
            (click)="open.set(true)"
          >
            Show evaluations
          </button>
        }
      </div>

      @if (open()) {
        <div id="rule-evaluations-list" class="mt-4">
          <div class="flex flex-wrap items-center gap-3">
            <div
              class="inline-flex rounded-lg bg-gray-200/70 p-0.5"
              role="radiogroup"
              aria-label="Which evaluations"
            >
              @for (option of options; track option.by) {
                <button
                  type="button"
                  role="radio"
                  class="rounded-md px-3 py-1.5 text-sm"
                  [attr.aria-checked]="by() === option.by"
                  [class]="
                    by() === option.by
                      ? 'bg-white font-medium text-gray-900 shadow-sm'
                      : 'text-gray-600 hover:text-gray-900'
                  "
                  (click)="choose(option.by)"
                >
                  {{ option.label }}
                </button>
              }
            </div>
            <p class="text-xs text-gray-500">{{ hint() }}</p>
          </div>

          <app-usage-audit-list
            class="mt-4 block"
            [storefrontId]="storefrontId()"
            [filter]="filter()"
            [page]="page()"
            [ruleLink]="ruleLink"
            [emptyText]="emptyText()"
            (pageChange)="page.set($event)"
          />
        </div>
      }
    </section>
  `,
})
export class RuleEvaluations {
  readonly storefrontId = input.required<number>();
  readonly ruleId = input.required<number>();

  protected readonly options = [
    { by: 'applied', label: 'Applied it' },
    { by: 'matched', label: 'Matched it' },
  ] as const;
  protected readonly open = signal(false);
  protected readonly by = signal<By>('applied');
  protected readonly page = signal(1);
  /** From `/storefronts/:id/promotion-rules/:ruleId` to the other rules. */
  protected readonly ruleLink = ['..'];

  protected readonly filter = computed<AuditRuleFilter>(() => ({
    by: this.by(),
    ruleId: this.ruleId(),
  }));
  protected readonly hint = computed(() =>
    this.by() === 'applied'
      ? 'Its action changed the cart.'
      : 'Its conditions matched, whether or not it changed the cart.',
  );
  protected readonly emptyText = computed(() =>
    this.by() === 'applied'
      ? 'No evaluation has applied this rule yet.'
      : 'No evaluation has matched this rule yet.',
  );

  protected choose(by: By): void {
    this.by.set(by);
    this.page.set(1);
  }
}
