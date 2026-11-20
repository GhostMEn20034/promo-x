import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { Pager } from '../../../shared/ui';
import { PromotionClient } from '../promotion.client';
import {
  hasFilters,
  hasInvalidRange,
  PromotionListQuery,
  readQuery,
  SORT_OPTIONS,
  SortValue,
  toUrlParams,
} from './promotion-list-query';

const PAGE_SIZE = 20;

/**
 * A storefront's promotion rules, filtered by status, short-circuit and creation day, and sorted. The filters,
 * sort and page live in the URL, so a filtered list can be shared or bookmarked.
 */
@Component({
  selector: 'app-promotion-rule-list-page',
  imports: [DatePipe, RouterLink, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './promotion-rule-list.page.html',
})
export class PromotionRuleListPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  // The query parameters; `readQuery` validates them
  readonly page = input<unknown>();
  readonly sort = input<unknown>();
  readonly active = input<unknown>();
  readonly shortCircuit = input<unknown>();
  readonly createdFrom = input<unknown>();
  readonly createdTo = input<unknown>();

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthApi);

  protected readonly sortOptions = SORT_OPTIONS;
  protected readonly query = computed(() =>
    readQuery({
      page: this.page(),
      sort: this.sort(),
      active: this.active(),
      shortCircuit: this.shortCircuit(),
      createdFrom: this.createdFrom(),
      createdTo: this.createdTo(),
    }),
  );
  protected readonly filtered = computed(() => hasFilters(this.query()));
  protected readonly invalidRange = computed(() => hasInvalidRange(this.query()));
  protected readonly rules = inject(PromotionClient).page(this.storefrontId, this.query, PAGE_SIZE);
  /** promo-x-core lets only promotion managers create rules and evaluate carts. */
  protected readonly canCreate = computed(() => this.auth.hasAnyRole([Role.PromotionManager]));

  protected setStatus(value: string): void {
    this.update({ isActive: toBoolean(value) });
  }

  protected setShortCircuit(value: string): void {
    this.update({ isShortCircuit: toBoolean(value) });
  }

  protected setCreatedFrom(value: string): void {
    this.update({ createdFrom: value || null });
  }

  protected setCreatedTo(value: string): void {
    this.update({ createdTo: value || null });
  }

  protected setSort(value: string): void {
    this.update({ sort: value as SortValue });
  }

  protected clearFilters(): void {
    this.update({ isActive: null, isShortCircuit: null, createdFrom: null, createdTo: null });
  }

  /** Puts the changed query in the URL. Any change other than the page goes back to the first page. */
  private update(changes: Partial<PromotionListQuery>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toUrlParams({ ...this.query(), page: 1, ...changes }),
      queryParamsHandling: 'merge',
    });
  }
}

function toBoolean(value: string): boolean | null {
  return value === 'true' ? true : value === 'false' ? false : null;
}
