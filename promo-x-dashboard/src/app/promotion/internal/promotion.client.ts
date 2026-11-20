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
  UsageLimitsInput,
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
