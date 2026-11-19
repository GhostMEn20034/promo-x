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
import { StorefrontFormDialog } from '../form/storefront-form-dialog';
import { StorefrontClient } from '../storefront.client';
import { Storefront } from '../storefront.model';

const PAGE_SIZE = 12;

@Component({
  selector: 'app-storefront-list-page',
  imports: [RouterLink, StorefrontCard, StorefrontFormDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './storefront-list.page.html',
})
export class StorefrontListPage {
  /** The 1-based `page` query parameter. */
  readonly page = input(1, { transform: toPageNumber });

  private readonly auth = inject(AuthApi);

  protected readonly storefronts = inject(StorefrontClient).page(this.page, PAGE_SIZE);
  /** promo-x-core lets only admins create and update storefronts. */
  protected readonly canEdit = computed(() => this.auth.hasAnyRole([Role.Admin]));
  /** The form: creating a storefront, or editing one. */
  protected readonly form = signal<
    { readonly mode: 'create' } | { readonly mode: 'edit'; readonly storefront: Storefront } | null
  >(null);
  /** The storefront just created, for the confirmation. */
  protected readonly created = signal<Storefront | null>(null);

  protected onSaved(saved: Storefront): void {
    if (this.form()?.mode === 'create') {
      this.onCreated(saved);
    } else {
      this.onUpdated(saved);
    }
    this.form.set(null);
  }

  /**
   * Storefronts are listed oldest first, so the new one goes at the end of the last page. The list is reloaded
   * so it shows up there, and the confirmation links to it from whatever page this is.
   */
  private onCreated(storefront: Storefront): void {
    this.created.set(storefront);
    this.storefronts.reload();
  }

  private onUpdated(updated: Storefront): void {
    this.storefronts.value.update(
      (page) =>
        page && {
          ...page,
          content: page.content.map((storefront) =>
            storefront.id === updated.id ? updated : storefront,
          ),
        },
    );
  }
}

function toPageNumber(value: unknown): number {
  const page = numberAttribute(value, 1);
  return Number.isSafeInteger(page) && page >= 1 ? page : 1;
}
