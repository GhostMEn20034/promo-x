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
import { CategoryEditDialog } from '../edit/category-edit-dialog';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-category-list-page',
  imports: [RouterLink, Pager, CategoryEditDialog],
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
  /** promo-x-core lets only employees update categories; auditors only read them. */
  protected readonly canEdit = computed(() => this.auth.hasAnyRole([Role.Employee]));
  protected readonly editing = signal<Category | null>(null);

  /** Replaces the edited row, and refreshes the parent shown on its child rows. */
  protected onSaved(updated: Category): void {
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
    this.editing.set(null);
  }
}

function toPageNumber(value: unknown): number {
  const page = numberAttribute(value, 1);
  return Number.isSafeInteger(page) && page >= 1 ? page : 1;
}
