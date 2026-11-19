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
import { Pager } from '../../../shared/ui';
import { CategoryClient } from '../category.client';
import { Category } from '../category.model';
import { CategoryFormDialog } from '../form/category-form-dialog';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-category-list-page',
  imports: [RouterLink, Pager, CategoryFormDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-list.page.html',
})
export class CategoryListPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The 1-based `page` query parameter. */
  readonly page = input(1, { transform: toPageNumber });

  private readonly auth = inject(AuthApi);

  protected readonly categories = inject(CategoryClient).page(
    this.storefrontId,
    this.page,
    PAGE_SIZE,
  );
  /** promo-x-core lets only employees create and update categories; auditors only read them. */
  protected readonly canEdit = computed(() => this.auth.hasAnyRole([Role.Employee]));
  /** The form: creating a category, or editing one. */
  protected readonly form = signal<
    { readonly mode: 'create' } | { readonly mode: 'edit'; readonly category: Category } | null
  >(null);
  /** The category just created, for the confirmation. */
  protected readonly created = signal<Category | null>(null);

  protected onSaved(saved: Category): void {
    if (this.form()?.mode === 'create') {
      this.onCreated(saved);
    } else {
      this.onUpdated(saved);
    }
    this.form.set(null);
  }

  /**
   * Categories are listed by ID, so the new one goes at the end of the last page. The list is reloaded so it shows
   * up there, and the confirmation links to it from whatever page this is.
   */
  private onCreated(category: Category): void {
    this.created.set(category);
    this.categories.reload();
  }

  /** Replaces the edited row, and refreshes the parent shown on its child rows. */
  private onUpdated(updated: Category): void {
    this.categories.value.update(
      (page) =>
        page && {
          ...page,
          content: page.content.map((category) => {
            if (category.id === updated.id) {
              return updated;
            }
            if (category.parent?.id === updated.id) {
              return {
                ...category,
                parent: {
                  ...category.parent,
                  name: updated.name,
                  externalId: updated.externalId,
                  parentId: updated.parent?.id ?? null,
                },
              };
            }
            return category;
          }),
        },
    );
  }
}

function toPageNumber(value: unknown): number {
  const page = numberAttribute(value, 1);
  return Number.isSafeInteger(page) && page >= 1 ? page : 1;
}
