import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Storefront } from '../storefront.model';
import { WebsiteLink } from '../ui/website-link';

/**
 * A storefront in the list. The whole card opens the storefront's page; the website link and the edit button sit
 * above that link, so they work on their own.
 */
@Component({
  selector: 'app-storefront-card',
  imports: [RouterLink, WebsiteLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article
      class="relative flex h-full flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-gray-300 hover:shadow-md focus-within:ring-2 focus-within:ring-blue-500"
    >
      <div class="flex items-start justify-between gap-3">
        <h2 class="min-w-0 text-lg font-semibold break-words text-gray-900">
          <a
            class="outline-none after:absolute after:inset-0 after:rounded-xl"
            [routerLink]="['/storefronts', storefront().id]"
          >
            {{ storefront().name }}
          </a>
        </h2>
        @if (canEdit()) {
          <button
            type="button"
            class="relative z-10 -m-1.5 shrink-0 rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
            [attr.aria-label]="'Edit ' + storefront().name"
            title="Edit"
            (click)="edit.emit()"
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
          </button>
        }
      </div>
      <p class="mt-1 text-xs text-gray-500">ID {{ storefront().id }}</p>
      <app-website-link class="relative z-10 mt-4 self-start" [website]="storefront().website" />
    </article>
  `,
})
export class StorefrontCard {
  readonly storefront = input.required<Storefront>();
  /** Shows the edit button. */
  readonly canEdit = input(false);
  readonly edit = output<void>();
}
