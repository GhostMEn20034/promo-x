import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { isId, PromotionClient } from '../promotion.client';
import { RuleProduct, SavedPromotionRule } from '../promotion.model';
import { fromAction, newAction, newGroup, rootFromCondition } from '../rule/rule-draft';
import { RuleFlow } from '../ui/rule-flow';
import { RuleIcon } from '../ui/rule-icon';
import { UsageMeter } from '../ui/usage-meter';
import { DeleteRuleDialog } from './delete-rule-dialog';
import { UsageLimitsDialog } from './usage-limits-dialog';

/** `…/promotion-rules/:ruleId`: the rule drawn as IF → THEN, its usage, and for promotion managers, its changes. */
@Component({
  selector: 'app-promotion-rule-details-page',
  imports: [
    RouterLink,
    DatePipe,
    RuleFlow,
    RuleIcon,
    UsageMeter,
    UsageLimitsDialog,
    DeleteRuleDialog,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="text-sm text-blue-700 hover:underline" routerLink="..">← All rules</a>

    @if (notFound()) {
      <p class="mt-4 text-sm text-gray-600">This rule doesn't exist in this storefront.</p>
    } @else if (rule.error()) {
      <div role="alert" class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
        Couldn't load the rule.
        <button type="button" class="ml-2 font-medium underline" (click)="rule.reload()">
          Try again
        </button>
      </div>
    } @else if (rule.hasValue()) {
      @let details = rule.value();
      <header class="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="text-xl font-semibold break-words text-gray-900">{{ details.name }}</h2>
            @if (details.isActive) {
              <span
                class="rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700"
                >Active</span
              >
            } @else {
              <span class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-600"
                >Inactive</span
              >
            }
            @if (details.isShortCircuit) {
              <span class="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs text-amber-800"
                >Stops lower rules</span
              >
            }
          </div>
          <p class="mt-1 text-sm text-gray-500">
            ID {{ details.id }} · Priority
            <span class="font-medium text-gray-700 tabular-nums">{{ details.priority }}</span> ·
            Updated
            <time [attr.datetime]="details.updatedAt">{{
              details.updatedAt | date: 'medium'
            }}</time>
          </p>
        </div>
        @if (canManage()) {
          <div class="flex shrink-0 gap-2">
            <a
              class="inline-flex items-center gap-1.5 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
              routerLink="edit"
            >
              <app-rule-icon class="size-4" name="pencil" />
              Edit rule
            </a>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-md border border-red-200 bg-white px-3 py-1.5 text-sm text-red-700 hover:bg-red-50"
              (click)="deleting.set(true)"
            >
              <app-rule-icon class="size-4" name="trash" />
              Delete
            </button>
          </div>
        }
      </header>

      <div class="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
        <section
          class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
          aria-labelledby="rule-logic-heading"
        >
          <h3 id="rule-logic-heading" class="text-base font-semibold text-gray-900">
            How it works
          </h3>
          <app-rule-flow
            class="mt-4"
            [condition]="condition()"
            [action]="action()"
            [products]="productsByExternalId()"
            [productLink]="productLink"
          />
        </section>

        <div class="space-y-6">
          <section
            class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
            aria-labelledby="rule-usage-heading"
          >
            <div class="flex items-center justify-between gap-2">
              <h3 id="rule-usage-heading" class="text-base font-semibold text-gray-900">
                Usage limit
              </h3>
              @if (canManage()) {
                <button
                  type="button"
                  class="text-sm font-medium text-blue-700 hover:underline"
                  (click)="editingLimits.set(true)"
                >
                  Change
                </button>
              }
            </div>
            <app-usage-meter
              class="mt-3"
              [hasUsageLimit]="details.hasUsageLimit"
              [usageLimit]="details.usageLimit"
              [remainingUses]="details.remainingUses"
            />
            @if (details.hasUsageLimit) {
              <p class="mt-3 text-xs text-gray-400">
                Lowered shortly after each evaluation that applies the rule, so it can lag behind by
                a moment.
              </p>
            }
          </section>

          <section
            class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
            aria-labelledby="rule-evaluation-heading"
          >
            <h3 id="rule-evaluation-heading" class="text-base font-semibold text-gray-900">
              Evaluation
            </h3>
            <dl class="mt-3 space-y-3 text-sm">
              <div>
                <dt class="text-gray-500">Priority</dt>
                <dd class="text-gray-900">
                  <span class="font-medium tabular-nums">{{ details.priority }}</span>
                  <span class="text-gray-500"> · higher priorities run first</span>
                </dd>
              </div>
              <div>
                <dt class="text-gray-500">Status</dt>
                <dd class="text-gray-900">
                  {{
                    details.isActive ? 'Active: evaluated against carts' : 'Inactive: not evaluated'
                  }}
                </dd>
              </div>
              <div>
                <dt class="text-gray-500">Short-circuit</dt>
                <dd class="text-gray-900">
                  {{
                    details.isShortCircuit
                      ? 'Once it changes the cart, lower-priority rules are skipped'
                      : 'Lower-priority rules are evaluated after it'
                  }}
                </dd>
              </div>
              <div>
                <dt class="text-gray-500">Created</dt>
                <dd class="text-gray-900">
                  <time [attr.datetime]="details.createdAt">{{
                    details.createdAt | date: 'medium'
                  }}</time>
                </dd>
              </div>
            </dl>
          </section>

          @if (details.products.length > 0) {
            <section
              class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
              aria-labelledby="rule-products-heading"
            >
              <h3 id="rule-products-heading" class="text-base font-semibold text-gray-900">
                Products it refers to
              </h3>
              <ul class="mt-3 divide-y divide-gray-100 text-sm" data-testid="rule-products">
                @for (product of details.products; track product.id) {
                  <li class="py-2 first:pt-0 last:pb-0">
                    <a
                      class="font-medium text-blue-700 hover:underline"
                      [routerLink]="[...productLink, product.id]"
                      >{{ product.name }}</a
                    >
                    <p class="mt-0.5 font-mono text-xs break-all text-gray-500">
                      {{ product.externalId }} · SKU {{ product.sku }}
                    </p>
                  </li>
                }
              </ul>
            </section>
          }
        </div>
      </div>

      @if (editingLimits()) {
        <app-usage-limits-dialog
          [rule]="details"
          (saved)="onLimitsSaved($event)"
          (closed)="editingLimits.set(false)"
        />
      }
      @if (deleting()) {
        <app-delete-rule-dialog
          [rule]="details"
          (deleted)="onDeleted()"
          (closed)="deleting.set(false)"
        />
      }
    } @else {
      <p class="mt-4 text-sm text-gray-500">Loading rule…</p>
    }
  `,
})
export class PromotionRuleDetailsPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The `:ruleId` route parameter. */
  readonly ruleId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly rule = inject(PromotionClient).byId(this.ruleId);
  /** promo-x-core lets only promotion managers change rules. */
  protected readonly canManage = computed(() => this.auth.hasAnyRole([Role.PromotionManager]));
  protected readonly editingLimits = signal(false);
  protected readonly deleting = signal(false);
  /** From `/storefronts/:id/promotion-rules/:ruleId` to `/storefronts/:id/products/:productId`. */
  protected readonly productLink = ['../../products'];

  protected readonly condition = computed(() =>
    this.rule.hasValue() ? rootFromCondition(this.rule.value().conditionTree.node) : newGroup(),
  );
  protected readonly action = computed(() =>
    this.rule.hasValue() ? fromAction(this.rule.value().action.action) : newAction(),
  );
  protected readonly productsByExternalId = computed<ReadonlyMap<string, RuleProduct>>(
    () =>
      new Map(
        (this.rule.hasValue() ? this.rule.value().products : []).map((product) => [
          product.externalId,
          product,
        ]),
      ),
  );

  /** `GET /api/promotion/{id}` doesn't check the storefront, so another storefront's rule counts as missing. */
  protected readonly notFound = computed(() => {
    if (!isId(this.ruleId())) {
      return true;
    }
    const error = this.rule.error();
    if (error) {
      return error instanceof HttpErrorResponse && error.status === 404;
    }
    return this.rule.hasValue() && this.rule.value().storefrontId !== this.storefrontId();
  });

  /** The response has the products' external IDs instead of the products, so only the limits are taken. */
  protected onLimitsSaved(saved: SavedPromotionRule): void {
    this.rule.value.update((current) =>
      current
        ? {
            ...current,
            hasUsageLimit: saved.hasUsageLimit,
            usageLimit: saved.usageLimit,
            remainingUses: saved.remainingUses,
            updatedAt: saved.updatedAt,
          }
        : current,
    );
    this.editingLimits.set(false);
  }

  protected onDeleted(): void {
    this.deleting.set(false);
    void this.router.navigate(['..'], { relativeTo: this.route });
  }
}
