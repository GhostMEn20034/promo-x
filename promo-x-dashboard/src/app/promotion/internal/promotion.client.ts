import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../../infrastructure/http';
import { PromotionListQuery, toApiParams } from './list/promotion-list-query';
import {
  EvaluationRequest,
  EvaluationResult,
  NewPromotionRule,
  PromotionRuleDetails,
  PromotionRuleInput,
  PromotionRuleSummary,
  SavedPromotionRule,
  UsageAuditItem,
  UsageAuditQuery,
  UsageLimitsInput,
  UsageStats,
} from './promotion.model';

const STOREFRONT_HEADER = 'X-Storefront-Id';

/**
 * promo-x-core's promotion rule endpoints. Reads are resources that follow their signals; writes are one-off
 * requests.
 *
 * The resource methods must be called in an injection context, such as a component's field initializer, and the
 * resource lives as long as that context.
 */
@Injectable()
export class PromotionClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL).replace(/\/+$/, '')}/api/promotion`;

  /** One page of the storefront's rules, filtered and sorted as the query says. */
  page(
    storefrontId: Signal<number>,
    query: Signal<PromotionListQuery>,
    size: number,
  ): HttpResourceRef<Page<PromotionRuleSummary> | undefined> {
    return httpResource<Page<PromotionRuleSummary>>(() =>
      isId(storefrontId())
        ? {
            url: `${this.url}/`,
            params: toApiParams(query(), size),
            headers: { [STOREFRONT_HEADER]: String(storefrontId()) },
          }
        : undefined,
    );
  }

  /**
   * The storefront's confirmed usages from `from` (included) to `to` (excluded): totals and a count per UTC day.
   * Nothing is requested while the range is undefined.
   */
  usageStats(
    storefrontId: Signal<number>,
    range: Signal<{ readonly from: string; readonly to: string } | undefined>,
  ): HttpResourceRef<UsageStats | undefined> {
    return httpResource<UsageStats>(() => {
      const period = range();
      return isId(storefrontId()) && period
        ? {
            url: `${this.url}/usage/stats`,
            params: period,
            headers: { [STOREFRONT_HEADER]: String(storefrontId()) },
          }
        : undefined;
    });
  }

  /**
   * A page of the storefront's audited evaluations, newest first. A rule ID list keeps only the evaluations that
   * matched, or applied, any of those rules. Nothing is requested while the query is undefined.
   */
  usageAudit(
    storefrontId: Signal<number>,
    query: Signal<UsageAuditQuery | undefined>,
  ): HttpResourceRef<Page<UsageAuditItem> | undefined> {
    return httpResource<Page<UsageAuditItem>>(() => {
      const current = query();
      if (!isId(storefrontId()) || !current) {
        return undefined;
      }
      const params: Record<string, string | number | readonly (string | number)[]> = {
        page: current.page,
        size: current.size,
      };
      // Repeated parameters, `?appliedRuleIds=1&appliedRuleIds=2`
      if (current.matchedRuleIds?.length) {
        params['matchedRuleIds'] = current.matchedRuleIds;
      }
      if (current.appliedRuleIds?.length) {
        params['appliedRuleIds'] = current.appliedRuleIds;
      }
      return {
        url: `${this.url}/usage/audit/`,
        params,
        headers: { [STOREFRONT_HEADER]: String(storefrontId()) },
      };
    });
  }

  /** The rule with this ID and its products. Nothing is requested while the ID isn't a positive integer. */
  byId(id: Signal<number>): HttpResourceRef<PromotionRuleDetails | undefined> {
    return httpResource<PromotionRuleDetails>(() =>
      isId(id()) ? `${this.url}/${id()}` : undefined,
    );
  }

  create(storefrontId: number, rule: NewPromotionRule): Observable<SavedPromotionRule> {
    return this.http.post<SavedPromotionRule>(`${this.url}/`, rule, {
      headers: { [STOREFRONT_HEADER]: String(storefrontId) },
    });
  }

  /** Replaces everything but the usage limit. */
  update(id: number, rule: PromotionRuleInput): Observable<SavedPromotionRule> {
    return this.http.put<SavedPromotionRule>(`${this.url}/${id}`, rule);
  }

  updateUsageLimits(id: number, limits: UsageLimitsInput): Observable<SavedPromotionRule> {
    return this.http.patch<SavedPromotionRule>(`${this.url}/${id}/usage-limits`, limits);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Prices the cart with the storefront's active rules. */
  evaluate(storefrontId: number, cart: EvaluationRequest): Observable<EvaluationResult> {
    return this.http.post<EvaluationResult>(`${this.url}/evaluate`, cart, {
      headers: { [STOREFRONT_HEADER]: String(storefrontId) },
    });
  }
}

export function isId(id: number): boolean {
  return Number.isSafeInteger(id) && id > 0;
}
