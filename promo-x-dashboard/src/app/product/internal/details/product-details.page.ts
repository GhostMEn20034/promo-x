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
import { RouterLink } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { ProductFormDialog } from '../form/product-form-dialog';
import { isId, ProductClient } from '../product.client';
import { Product } from '../product.model';
import { CategoryChips } from '../ui/category-chips';

@Component({
  selector: 'app-product-details-page',
  imports: [RouterLink, DatePipe, CategoryChips, ProductFormDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="text-sm text-blue-700 hover:underline" routerLink="..">← All products</a>

    @if (notFound()) {
      <p class="mt-4 text-sm text-gray-600">This product doesn't exist in this storefront.</p>
    } @else if (product.error()) {
      <div role="alert" class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
        Couldn't load the product.
        <button type="button" class="ml-2 font-medium underline" (click)="product.reload()">
          Try again
        </button>
      </div>
    } @else if (product.hasValue()) {
      @let details = product.value();
      <article class="mt-4 rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <h2 class="min-w-0 text-xl font-semibold break-words text-gray-900">
            {{ details.name }}
          </h2>
          @if (canEdit()) {
            <button
              type="button"
              class="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
              (click)="editing.set(true)"
            >
              <svg
                class="size-4"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.75"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M16.86 3.49a2.1 2.1 0 0 1 2.97 2.97L7.5 18.79 3 20l1.21-4.5L16.86 3.49z" />
                <path d="M15 5.35l3.65 3.65" />
              </svg>
              Edit
            </button>
          }
        </div>
        <dl class="mt-6 grid grid-cols-[max-content_1fr] gap-x-8 gap-y-4 text-sm">
          <dt class="text-gray-500">ID</dt>
          <dd class="text-gray-900">{{ details.id }}</dd>
          <dt class="text-gray-500">SKU</dt>
          <dd class="font-mono text-xs break-all text-gray-900">{{ details.sku }}</dd>
          <dt class="text-gray-500">External ID</dt>
          <dd class="font-mono text-xs break-all text-gray-900">{{ details.externalId }}</dd>
          <dt class="text-gray-500">Categories</dt>
          <dd>
            <app-category-chips [categories]="details.categories" [linkTo]="categoriesLink" />
          </dd>
          <dt class="text-gray-500">Created</dt>
          <dd class="text-gray-900">
            <time [attr.datetime]="details.createdAt">{{
              details.createdAt | date: 'medium'
            }}</time>
          </dd>
          <dt class="text-gray-500">Updated</dt>
          <dd class="text-gray-900">
            <time [attr.datetime]="details.updatedAt">{{
              details.updatedAt | date: 'medium'
            }}</time>
          </dd>
        </dl>
      </article>

      @if (editing()) {
        <app-product-form-dialog
          [storefrontId]="details.storefrontId"
          [product]="details"
          (saved)="onSaved($event)"
          (closed)="editing.set(false)"
        />
      }
    } @else {
      <p class="mt-4 text-sm text-gray-500">Loading product…</p>
    }
  `,
})
export class ProductDetailsPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The `:productId` route parameter. */
  readonly productId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly auth = inject(AuthApi);

  protected readonly product = inject(ProductClient).byId(this.productId);
  /** promo-x-core lets only admins update products. */
  protected readonly canEdit = computed(() => this.auth.hasAnyRole([Role.Admin]));
  protected readonly editing = signal(false);
  /** From `/storefronts/:id/products/:productId` to `/storefronts/:id/categories/:categoryId`. */
  protected readonly categoriesLink = ['../../categories'];

  /**
   * `GET /api/product/{id}` doesn't check the storefront, so a product of another storefront is treated as
   * missing here instead of being shown under the wrong storefront.
   */
  protected readonly notFound = computed(() => {
    if (!isId(this.productId())) {
      return true;
    }
    const error = this.product.error();
    if (error) {
      return error instanceof HttpErrorResponse && error.status === 404;
    }
    return this.product.hasValue() && this.product.value().storefrontId !== this.storefrontId();
  });

  protected onSaved(updated: Product): void {
    this.product.value.set(updated);
    this.editing.set(false);
  }
}
