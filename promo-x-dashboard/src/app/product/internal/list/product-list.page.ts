import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { Pager } from '../../../shared/ui';
import { ProductFormDialog } from '../form/product-form-dialog';
import { ProductClient } from '../product.client';
import { Product } from '../product.model';
import { CategoryChips } from '../ui/category-chips';

const PAGE_SIZE = 20;

/** What the form dialog is open for: a new product, or the product being edited. */
type FormTarget =
  { readonly mode: 'create' } | { readonly mode: 'edit'; readonly product: Product };

@Component({
  selector: 'app-product-list-page',
  imports: [RouterLink, DatePipe, Pager, CategoryChips, ProductFormDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.page.html',
})
export class ProductListPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The 1-based `page` query parameter. */
  readonly page = input(1, { transform: toPageNumber });

  private readonly auth = inject(AuthApi);
  private readonly router = inject(Router);

  protected readonly products = inject(ProductClient).page(this.storefrontId, this.page, PAGE_SIZE);
  /** promo-x-core lets employees create products, and only admins update them. */
  protected readonly canCreate = computed(() => this.auth.hasAnyRole([Role.Employee]));
  protected readonly canEdit = computed(() => this.auth.hasAnyRole([Role.Admin]));
  protected readonly form = signal<FormTarget | null>(null);
  /** The product just created, announced above the list. */
  protected readonly created = signal<Product | null>(null);

  protected formProduct(target: FormTarget): Product | null {
    return target.mode === 'edit' ? target.product : null;
  }

  protected onSaved(target: FormTarget, product: Product): void {
    this.form.set(null);
    if (target.mode === 'edit') {
      this.products.value.update(
        (page) =>
          page && {
            ...page,
            content: page.content.map((row) => (row.id === product.id ? product : row)),
          },
      );
      return;
    }

    // The list is newest first, so a new product belongs at the top of the first page
    this.created.set(product);
    if (this.page() === 1) {
      this.products.reload();
    } else {
      void this.router.navigate([], { queryParams: { page: null }, queryParamsHandling: 'merge' });
    }
  }
}

function toPageNumber(value: unknown): number {
  const page = numberAttribute(value, 1);
  return Number.isSafeInteger(page) && page >= 1 ? page : 1;
}
