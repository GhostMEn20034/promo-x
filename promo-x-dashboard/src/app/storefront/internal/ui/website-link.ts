import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** A storefront's website with a globe icon, opening in a new tab. */
@Component({
  selector: 'app-website-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex min-w-0' },
  template: `
    @if (website(); as website) {
      <a
        class="inline-flex min-w-0 items-center gap-1.5 text-sm text-blue-700 hover:underline"
        target="_blank"
        rel="noopener noreferrer"
        [href]="href()"
      >
        <svg
          class="size-4 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M3 12h18" />
          <path
            d="M12 3c2.5 2.7 3.75 5.7 3.75 9S14.5 18.3 12 21c-2.5-2.7-3.75-5.7-3.75-9S9.5 5.7 12 3z"
          />
        </svg>
        <span class="truncate">{{ website }}</span>
      </a>
    } @else {
      <span class="inline-flex items-center gap-1.5 text-sm text-gray-400">
        <svg
          class="size-4 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.75"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="9" />
        </svg>
        No website
      </span>
    }
  `,
})
export class WebsiteLink {
  readonly website = input.required<string | null>();

  /**
   * The stored website may lack a scheme (`shop.example.com`), which a browser would treat as a relative link, so
   * `https://` is added. This also keeps a `javascript:` value from becoming a script URL.
   */
  protected readonly href = computed(() => {
    const website = this.website() ?? '';
    return /^https?:\/\//i.test(website) ? website : `https://${website}`;
  });
}
