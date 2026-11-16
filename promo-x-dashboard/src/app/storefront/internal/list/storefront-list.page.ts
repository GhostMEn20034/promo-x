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
import { StorefrontCard } from '../card/storefront-card';
import { StorefrontEditDialog } from '../edit/storefront-edit-dialog';
import { StorefrontClient } from '../storefront.client';
import { Storefront } from '../storefront.model';

const PAGE_SIZE = 12;

@Component({
  selector: 'app-storefront-list-page',
  imports: [RouterLink, StorefrontCard, StorefrontEditDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './storefront-list.page.html',
})
export class StorefrontListPage {
  /** The 1-based `page` query parameter. */
  readonly page = input(1, { transform: toPageNumber });

  private readonly auth = inject(AuthApi);

  protected readonly storefronts = inject(StorefrontClient).page(this.page, PAGE_SIZE);
  /** promo-x-core lets only admins update storefronts. */
  protected readonly canEdit = computed(() => this.auth.hasAnyRole([Role.Admin]));
  protected readonly editing = signal<Storefront | null>(null);

  protected onSaved(updated: Storefront): void {
    this.storefronts.value.update(
      (page) =>
        page && {
          ...page,
          content: page.content.map((storefront) =>
            storefront.id === updated.id ? updated : storefront,
          ),
        },
    );
    this.editing.set(null);
  }
}

function toPageNumber(value: unknown): number {
  const page = numberAttribute(value, 1);
  return Number.isSafeInteger(page) && page >= 1 ? page : 1;
}
