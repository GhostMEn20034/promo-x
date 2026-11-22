import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Page } from '../../../infrastructure/http';
import { Pager } from '../../../shared/ui';
import { PromotionClient } from '../promotion.client';
import { AuditedRule, UsageAuditItem, UsageAuditQuery } from '../promotion.model';

const PAGE_SIZE = 20;

/** Which evaluations to list: all of them, or those that applied or matched one rule. */
export type AuditRuleFilter = {
  readonly by: 'applied' | 'matched';
  readonly ruleId: number;
} | null;

const STATUSES: Readonly<
  Record<UsageAuditItem['status'], { readonly label: string; readonly badge: string }>
> = {
  USED: { label: 'Used', badge: 'bg-green-50 text-green-700' },
  RESERVED: { label: 'Reserved', badge: 'bg-amber-50 text-amber-800' },
  CANCELED: { label: 'Canceled', badge: 'bg-gray-100 text-gray-600' },
  EXPIRED: { label: 'Expired', badge: 'bg-gray-100 text-gray-600' },
};

/**
 * Audited evaluations, newest first, a page at a time: when, the usage's status, the customer, the promo codes,
 * the rules applied (in order) and only matched, and the totals. Each row opens to the subtotal and shipping
 * breakdown. The parent owns the page, so it can keep it in the URL or not.
 */
@Component({
  selector: 'app-usage-audit-list',
  imports: [DatePipe, DecimalPipe, RouterLink, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './usage-audit-list.html',
})
export class UsageAuditList {
  readonly storefrontId = input.required<number>();
  /** 1-based. */
  readonly page = input(1);
  /** True: the pages are links that set the `page` query parameter. False: they emit `pageChange`. */
  readonly pageLinks = input(false);
  readonly filter = input<AuditRuleFilter>(null);
  /** The commands of the rule list, e.g. `['../promotion-rules']`; a rule's ID is added to open it. */
  readonly ruleLink = input.required<readonly unknown[]>();
  /** Shown when there's no evaluation to list. */
  readonly emptyText = input('No evaluation has applied a rule yet.');
  readonly pageChange = output<number>();

  private readonly query = computed<UsageAuditQuery>(() => {
    const filter = this.filter();
    const base = { page: this.page(), size: PAGE_SIZE };
    if (!filter) {
      return base;
    }
    return filter.by === 'applied'
      ? { ...base, appliedRuleIds: [filter.ruleId] }
      : { ...base, matchedRuleIds: [filter.ruleId] };
  });
  protected readonly audits = inject(PromotionClient).usageAudit(this.storefrontId, this.query);

  /** The last page loaded, kept on screen, dimmed, while another one loads. */
  protected readonly shown = linkedSignal<
    Page<UsageAuditItem> | undefined,
    Page<UsageAuditItem> | undefined
  >({
    source: () => (this.audits.hasValue() ? this.audits.value() : undefined),
    computation: (current, previous) => current ?? previous?.value,
  });
  protected readonly stale = computed(() => this.audits.isLoading() && this.shown() !== undefined);
  protected readonly forbidden = computed(() => {
    const error = this.audits.error();
    return error instanceof HttpErrorResponse && error.status === 403;
  });

  /** The usage whose row is open. */
  protected readonly expanded = signal<string | null>(null);
  protected readonly statuses = STATUSES;

  protected toggle(usageId: string): void {
    this.expanded.update((open) => (open === usageId ? null : usageId));
  }

  /** The rules that matched but changed nothing, in their matched order. */
  protected matchedOnly(audit: UsageAuditItem): readonly AuditedRule[] {
    const applied = new Set(audit.appliedRules.map((rule) => rule.ruleId));
    return audit.matchedRules.filter((rule) => !applied.has(rule.ruleId));
  }

  protected highlighted(rule: AuditedRule): boolean {
    return this.filter()?.ruleId === rule.ruleId;
  }

  protected onPageChange(page: number): void {
    this.expanded.set(null);
    this.pageChange.emit(page);
  }
}
