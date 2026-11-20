import { DecimalPipe, JsonPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { API_BASE_URL, problemDetailOf } from '../../../infrastructure/http';
import { ProductPickerDialog, ProductRef } from '../../../product';
import { TokenInput } from '../editor/token-input';
import { PromotionClient } from '../promotion.client';
import {
  EVALUATION_MAX_ITEMS,
  EvaluationRequest,
  EvaluationResult,
  RuleMention,
} from '../promotion.model';
import { CartLine, cartErrors, curlCommand, EMPTY_CART, TestCart, toRequest } from './test-cart';

/** Shown in the docs when the cart has no products yet. */
const EXAMPLE_ITEM = { productExternalId: 'YOUR-PRODUCT-ID', quantity: 1, unitPrice: 10 };

/**
 * `…/promotion-rules/evaluate`: prices a test cart with the storefront's active rules, the way a checkout would,
 * and documents how a checkout calls the endpoint. Uses count right away, so there's nothing to confirm.
 */
@Component({
  selector: 'app-promotion-evaluate-page',
  imports: [RouterLink, DecimalPipe, JsonPipe, TokenInput, ProductPickerDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './promotion-evaluate.page.html',
})
export class PromotionEvaluatePage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly client = inject(PromotionClient);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly apiUrl = inject(API_BASE_URL).replace(/\/+$/, '');
  protected readonly maxItems = EVALUATION_MAX_ITEMS;

  protected readonly cart = signal<TestCart>(EMPTY_CART);
  protected readonly picking = signal(false);
  /** Set by the first Evaluate, after which every problem is shown. */
  protected readonly attempted = signal(false);
  protected readonly evaluating = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The last evaluation: what was sent and what came back. */
  protected readonly last = signal<{
    readonly request: EvaluationRequest;
    readonly result: EvaluationResult;
  } | null>(null);
  protected readonly copied = signal(false);

  protected readonly errors = computed(() =>
    this.attempted() ? cartErrors(this.cart(), EVALUATION_MAX_ITEMS) : null,
  );
  protected readonly chosenProducts = computed(() => this.cart().lines.map((line) => line.product));
  /** The products of the last evaluation, by external ID, to show their names in the result. */
  private readonly productsByExternalId = computed(
    () => new Map(this.cart().lines.map((line) => [line.product.externalId, line.product])),
  );

  /** The request the docs show: the cart as it is, with an example line while it has none. */
  protected readonly exampleRequest = computed<EvaluationRequest>(() => {
    const request = toRequest(this.cart());
    return request.items.length > 0 ? request : { ...request, items: [EXAMPLE_ITEM] };
  });
  protected readonly curl = computed(() =>
    curlCommand(this.apiUrl, this.storefrontId(), this.exampleRequest()),
  );

  // The cart

  /** The picker returns every chosen product, so it replaces the lines, keeping the kept ones' numbers. */
  protected onProductsPicked(products: readonly ProductRef[]): void {
    this.cart.update((cart) => {
      const current = new Map(cart.lines.map((line) => [line.product.id, line]));
      return {
        ...cart,
        lines: products.map(
          (product) => current.get(product.id) ?? { product, quantity: '1', unitPrice: '' },
        ),
      };
    });
    this.picking.set(false);
  }

  protected setLine(productId: number, field: 'quantity' | 'unitPrice', value: string): void {
    this.cart.update((cart) => ({
      ...cart,
      lines: cart.lines.map((line) =>
        line.product.id === productId ? { ...line, [field]: value } : line,
      ),
    }));
  }

  protected removeLine(productId: number): void {
    this.cart.update((cart) => ({
      ...cart,
      lines: cart.lines.filter((line) => line.product.id !== productId),
    }));
  }

  protected set(field: 'customerId' | 'sessionId' | 'shippingCost', value: string): void {
    this.cart.update((cart) => ({ ...cart, [field]: value }));
  }

  protected setPromoCodes(promoCodes: readonly string[]): void {
    this.cart.update((cart) => ({ ...cart, promoCodes }));
  }

  protected lineTotal(line: CartLine): number | null {
    const quantity = Number(line.quantity);
    const price = Number(line.unitPrice);
    return line.unitPrice.trim() !== '' && Number.isFinite(quantity * price)
      ? quantity * price
      : null;
  }

  // Evaluating

  protected evaluate(): void {
    if (this.evaluating()) {
      return;
    }
    this.attempted.set(true);
    if (cartErrors(this.cart(), EVALUATION_MAX_ITEMS)) {
      return;
    }

    const request = toRequest(this.cart());
    this.evaluating.set(true);
    this.error.set(null);
    this.client
      .evaluate(this.storefrontId(), request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.evaluating.set(false);
          this.last.set({ request, result });
        },
        error: (error: unknown) => {
          this.evaluating.set(false);
          this.showError(error);
        },
      });
  }

  // The result

  protected productOf(externalId: string): ProductRef | null {
    return this.productsByExternalId().get(externalId) ?? null;
  }

  /** The matched rules, each marked whether its action changed the cart. */
  protected rulesOf(
    result: EvaluationResult,
  ): readonly (RuleMention & { readonly applied: boolean })[] {
    const applied = new Set(result.appliedRules.map((rule) => rule.ruleId));
    const matched = result.matchedRules.map((rule) => ({
      ...rule,
      applied: applied.has(rule.ruleId),
    }));
    // An applied rule is always matched, but just in case, none is left out
    const missing = result.appliedRules
      .filter((rule) => !matched.some((candidate) => candidate.ruleId === rule.ruleId))
      .map((rule) => ({ ...rule, applied: true }));
    return [...matched, ...missing];
  }

  protected savedPercent(result: EvaluationResult): number {
    return result.oldTotal > 0 ? (result.totalDiscount / result.oldTotal) * 100 : 0;
  }

  protected async copyCurl(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.curl());
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // No clipboard access: the command can still be selected by hand.
    }
  }

  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 403) {
      this.error.set("You're not allowed to evaluate carts.");
      return;
    }
    const problem = problemDetailOf(error);
    if (!problem) {
      this.error.set("Couldn't evaluate the cart. Try again.");
      return;
    }
    if (problem.code === 'PRODUCT_EXTERNAL_IDS_NOT_FOUND') {
      const context = problem.errors[0]?.errorContext as {
        missingProductExternalIds?: unknown;
      } | null;
      const missing = Array.isArray(context?.missingProductExternalIds)
        ? context.missingProductExternalIds.join(', ')
        : '';
      this.error.set(
        `These products are no longer in the storefront: ${missing || 'unknown'}. Remove them and try again.`,
      );
      return;
    }
    this.error.set(problem.errors.map((item) => item.message).join(' ') || problem.detail);
  }
}
