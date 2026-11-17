import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

type PagerItem = { readonly kind: 'page'; readonly page: number } | { readonly kind: 'gap' };

type PagerControl =
  | { readonly kind: 'gap' }
  | { readonly kind: 'current'; readonly page: number }
  | { readonly kind: 'page'; readonly page: number; readonly text: string; readonly label: string };

/**
 * Numbered pages (`‹ 1 … 4 5 6 … 12 ›`), 1-based. Renders nothing when there is only one page.
 *
 * By default the pages are links that set the `page` query parameter, for a page's main list. With
 * `[links]="false"` they're buttons that emit `pageChange` instead, for a list that isn't in the URL, such as one
 * in a dialog.
 */
@Component({
  selector: 'app-pager',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav class="flex flex-wrap items-center justify-center gap-1 text-sm" aria-label="Pages">
        @for (control of controls(); track $index) {
          @switch (control.kind) {
            @case ('gap') {
              <span class="px-1.5 text-gray-400" aria-hidden="true">…</span>
            }
            @case ('current') {
              <span
                class="min-w-8 rounded-md bg-blue-600 px-2.5 py-1.5 text-center font-medium text-white"
                aria-current="page"
                >{{ control.page }}</span
              >
            }
            @case ('page') {
              @if (links()) {
                <a
                  class="min-w-8 rounded-md px-2.5 py-1.5 text-center text-gray-700 hover:bg-gray-100"
                  routerLink="."
                  queryParamsHandling="merge"
                  [queryParams]="{ page: queryPage(control.page) }"
                  [attr.aria-label]="control.label"
                  >{{ control.text }}</a
                >
              } @else {
                <button
                  type="button"
                  class="min-w-8 rounded-md px-2.5 py-1.5 text-center text-gray-700 hover:bg-gray-100"
                  [attr.aria-label]="control.label"
                  (click)="pageChange.emit(control.page)"
                >
                  {{ control.text }}
                </button>
              }
            }
          }
        }
      </nav>
    }
  `,
})
export class Pager {
  /** The current page, 1-based. */
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  /** Links that set the `page` query parameter (true), or buttons that emit `pageChange` (false). */
  readonly links = input(true);
  /** The page the user picked, when `links` is false. */
  readonly pageChange = output<number>();

  protected readonly controls = computed(() => {
    const page = this.page();
    const controls: PagerControl[] = [];
    if (page > 1) {
      controls.push({ kind: 'page', page: page - 1, text: '‹', label: 'Previous page' });
    }
    for (const item of pagerItems(page, this.totalPages())) {
      controls.push(
        item.kind === 'gap'
          ? item
          : item.page === page
            ? { kind: 'current', page }
            : {
                kind: 'page',
                page: item.page,
                text: String(item.page),
                label: `Page ${item.page}`,
              },
      );
    }
    if (page < this.totalPages()) {
      controls.push({ kind: 'page', page: page + 1, text: '›', label: 'Next page' });
    }
    return controls;
  });

  /** Page 1 is the default, so it's left out of the URL. */
  protected queryPage(page: number): number | null {
    return page === 1 ? null : page;
  }
}

/**
 * The first and last page, and the pages around the current one, with gaps between them. Up to 7 pages are all
 * shown.
 */
export function pagerItems(page: number, totalPages: number): PagerItem[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => ({ kind: 'page', page: i + 1 }));
  }

  const shown = [...new Set([1, page - 1, page, page + 1, totalPages])]
    .filter((p) => p >= 1 && p <= totalPages)
    .sort((a, b) => a - b);

  const items: PagerItem[] = [];
  shown.forEach((p, i) => {
    const previous = shown[i - 1];
    if (previous !== undefined && p - previous > 1) {
      items.push(p - previous === 2 ? { kind: 'page', page: p - 1 } : { kind: 'gap' });
    }
    items.push({ kind: 'page', page: p });
  });
  return items;
}
