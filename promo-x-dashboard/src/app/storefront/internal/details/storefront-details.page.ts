import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { isStorefrontId, StorefrontClient } from '../storefront.client';
import { WebsiteLink } from '../ui/website-link';

@Component({
  selector: 'app-storefront-details-page',
  imports: [RouterLink, WebsiteLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto max-w-3xl px-6 py-8">
      <a class="text-sm text-blue-700 hover:underline" routerLink="/storefronts"
        >← All storefronts</a
      >

      @if (notFound()) {
        <p class="mt-6 text-gray-600">This storefront doesn't exist.</p>
      } @else if (storefront.error()) {
        <div role="alert" class="mt-6 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
          Couldn't load the storefront.
          <button type="button" class="ml-2 font-medium underline" (click)="storefront.reload()">
            Try again
          </button>
        </div>
      } @else if (storefront.hasValue()) {
        @let details = storefront.value();
        <article class="mt-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <h1 class="text-2xl font-semibold break-words text-gray-900">{{ details.name }}</h1>
          <dl class="mt-6 grid grid-cols-[max-content_1fr] gap-x-8 gap-y-4 text-sm">
            <dt class="text-gray-500">ID</dt>
            <dd class="text-gray-900">{{ details.id }}</dd>
            <dt class="text-gray-500">Website</dt>
            <dd class="min-w-0"><app-website-link [website]="details.website" /></dd>
          </dl>
        </article>
      } @else {
        <p class="mt-6 text-gray-500">Loading storefront…</p>
      }
    </section>
  `,
})
export class StorefrontDetailsPage {
  /** The `:storefrontId` route parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });

  protected readonly storefront = inject(StorefrontClient).byId(this.storefrontId);

  protected readonly notFound = computed(() => {
    if (!isStorefrontId(this.storefrontId())) {
      return true;
    }
    const error = this.storefront.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });
}
