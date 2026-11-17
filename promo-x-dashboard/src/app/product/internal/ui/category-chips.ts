import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CategoryRef } from '../../../category';

/**
 * A product's categories as chips. With `max`, only that many are shown, followed by `+N`. With `linkTo`, each
 * chip links to `[...linkTo, category.id]`.
 */
@Component({
  selector: 'app-category-chips',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-wrap gap-1.5' },
  template: `
    @for (category of shown(); track category.id) {
      @if (linkTo(); as linkTo) {
        <a
          class="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs text-blue-700 hover:bg-blue-100"
          [routerLink]="[...linkTo, category.id]"
          >{{ category.name }}</a
        >
      } @else {
        <span class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs text-gray-700">{{
          category.name
        }}</span>
      }
    } @empty {
      <span class="text-gray-400">—</span>
    }
    @if (hidden() > 0) {
      <span
        class="rounded-full px-1.5 py-0.5 text-xs text-gray-500"
        [title]="hiddenNames()"
        [attr.aria-label]="hidden() + ' more: ' + hiddenNames()"
        >+{{ hidden() }}</span
      >
    }
  `,
})
export class CategoryChips {
  readonly categories = input.required<readonly CategoryRef[]>();
  readonly max = input<number | null>(null);
  readonly linkTo = input<readonly unknown[] | null>(null);

  protected readonly shown = computed(() => {
    const max = this.max();
    return max === null ? this.categories() : this.categories().slice(0, max);
  });
  protected readonly hidden = computed(() => this.categories().length - this.shown().length);
  protected readonly hiddenNames = computed(() =>
    this.categories()
      .slice(this.shown().length)
      .map((category) => category.name)
      .join(', '),
  );
}
