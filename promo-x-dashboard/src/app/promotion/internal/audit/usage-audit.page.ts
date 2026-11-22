import { ChangeDetectionStrategy, Component, input, numberAttribute } from '@angular/core';
import { UsageAuditList } from './usage-audit-list';

/**
 * `/storefronts/:storefrontId/evaluations`: every audited evaluation of the storefront, newest first, a page at a
 * time, with the page in the URL.
 */
@Component({
  selector: 'app-usage-audit-page',
  imports: [UsageAuditList],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="evaluations-heading">
      <h2 id="evaluations-heading" class="text-lg font-semibold text-gray-900">Evaluations</h2>
      <p class="mt-1 max-w-3xl text-sm text-gray-500">
        Every cart evaluation that applied at least one rule: the rules it applied and those that
        only matched, the totals before and after, and its usage's status now. Times are in your
        time zone.
      </p>
      <app-usage-audit-list
        class="mt-4 block"
        [storefrontId]="storefrontId()"
        [page]="page()"
        [pageLinks]="true"
        [ruleLink]="ruleLink"
      />
    </section>
  `,
})
export class UsageAuditPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The 1-based `page` query parameter. */
  readonly page = input(1, { transform: toPageNumber });

  /** From `/storefronts/:id/evaluations` to `/storefronts/:id/promotion-rules/:ruleId`. */
  protected readonly ruleLink = ['../promotion-rules'];
}

function toPageNumber(value: unknown): number {
  const page = numberAttribute(value, 1);
  return Number.isSafeInteger(page) && page >= 1 ? page : 1;
}
