import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthApi, Role } from '../../../infrastructure/auth';
import { UsageStatsPanel } from '../../../promotion';
import { STOREFRONT_SECTIONS } from '../details/storefront-sections';

/** A storefront's landing page: its promotion usage, for those who may see it, then a menu of its sections. */
@Component({
  selector: 'app-storefront-overview-page',
  imports: [RouterLink, UsageStatsPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (canSeeUsage()) {
      <!-- Deferred, so the promotion module stays in its own lazy chunk -->
      @defer {
        <app-usage-stats-panel class="mb-10" [storefrontId]="storefrontId()" />
      } @placeholder {
        <p class="mb-10 text-sm text-gray-500">Loading usage statistics…</p>
      }
    }

    <section aria-labelledby="sections-heading">
      <h2 id="sections-heading" class="text-lg font-semibold text-gray-900">Sections</h2>
      <ul class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        @for (section of sections(); track section.path) {
          <li>
            <a
              class="group flex h-full gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-gray-300 hover:shadow-md focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
              [routerLink]="section.path"
            >
              <span
                class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700"
              >
                <svg
                  class="size-5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.75"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  @switch (section.icon) {
                    @case ('categories') {
                      <path
                        d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"
                      />
                    }
                    @case ('products') {
                      <path d="M21 8l-9-5-9 5 9 5 9-5z" />
                      <path d="M3 8v8l9 5 9-5V8" />
                      <path d="M12 13v8" />
                    }
                    @case ('promotions') {
                      <path d="M19 5L5 19" />
                      <circle cx="7" cy="7" r="2" />
                      <circle cx="17" cy="17" r="2" />
                    }
                    @case ('evaluations') {
                      <path d="M9 4h6v3H9z" />
                      <path
                        d="M15 5h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2"
                      />
                      <path d="M9 13l2 2 4-4" />
                    }
                  }
                </svg>
              </span>
              <span class="min-w-0">
                <span class="flex flex-wrap items-center gap-2">
                  <span class="font-medium text-gray-900 group-hover:text-blue-700">{{
                    section.label
                  }}</span>
                  @if (section.comingSoon) {
                    <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500"
                      >Coming soon</span
                    >
                  }
                </span>
                <span class="mt-1 block text-sm text-gray-500">{{ section.description }}</span>
              </span>
            </a>
          </li>
        }
      </ul>
    </section>
  `,
})
export class StorefrontOverviewPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly auth = inject(AuthApi);

  /** The sections the user may open. */
  protected readonly sections = computed(() =>
    STOREFRONT_SECTIONS.filter((section) => !section.roles || this.auth.hasAnyRole(section.roles)),
  );
  /** promo-x-core lets auditors, promotion managers and admins read the usage statistics. */
  protected readonly canSeeUsage = computed(() =>
    this.auth.hasAnyRole([Role.Auditor, Role.PromotionManager, Role.Admin]),
  );
}
