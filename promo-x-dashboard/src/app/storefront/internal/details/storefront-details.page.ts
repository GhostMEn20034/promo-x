import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { isStorefrontId, StorefrontClient } from '../storefront.client';
import { WebsiteLink } from '../ui/website-link';
import { STOREFRONT_SECTIONS } from './storefront-sections';

/**
 * The frame of a storefront's pages: its name and website, and tabs for its sections. The section pages render
 * below the tabs.
 */
@Component({
  selector: 'app-storefront-details-page',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, WebsiteLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="mx-auto max-w-6xl px-6 py-8">
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
        <header class="mt-4">
          <h1 class="text-2xl font-semibold break-words text-gray-900">{{ details.name }}</h1>
          <div class="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gray-500">
            <span>ID {{ details.id }}</span>
            <app-website-link [website]="details.website" />
          </div>
        </header>

        <nav class="mt-6 border-b border-gray-200" aria-label="Storefront sections">
          <ul class="-mb-px flex gap-6 overflow-x-auto">
            @for (tab of tabs; track tab.path) {
              <li>
                <a
                  class="inline-flex items-center gap-2 border-b-2 border-transparent px-1 pb-3 text-sm font-medium whitespace-nowrap text-gray-500 hover:border-gray-300 hover:text-gray-900 aria-[current=page]:border-blue-600 aria-[current=page]:text-blue-700"
                  routerLinkActive
                  ariaCurrentWhenActive="page"
                  [routerLink]="tab.path"
                  [routerLinkActiveOptions]="{ exact: tab.path === '.' }"
                >
                  {{ tab.label }}
                  @if (tab.comingSoon) {
                    <span
                      class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-normal text-gray-500"
                      >Soon</span
                    >
                  }
                </a>
              </li>
            }
          </ul>
        </nav>

        <div class="mt-6">
          <router-outlet />
        </div>
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
  protected readonly tabs = [
    { path: '.', label: 'Overview', comingSoon: false },
    ...STOREFRONT_SECTIONS,
  ];

  protected readonly notFound = computed(() => {
    if (!isStorefrontId(this.storefrontId())) {
      return true;
    }
    const error = this.storefront.error();
    return error instanceof HttpErrorResponse && error.status === 404;
  });
}
