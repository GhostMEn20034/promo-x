import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Pager } from '../../../shared/ui';
import { CategoryClient } from '../category.client';
import { Category, CategoryRef } from '../category.model';

const PAGE_SIZE = 10;

/**
 * A modal that lists a storefront's categories, page by page, to choose from. It opens as soon as it's rendered
 * and emits `closed` when the user dismisses it.
 *
 * - Single (default): each row has a Choose button, which emits `picked`. The `selected` category is marked
 *   Current.
 * - Multiple: each row has a checkbox, and Done emits `pickedMany` with every checked category, including ones
 *   checked on other pages and the `selected` ones the user kept.
 *
 * `unavailable` says why a category can't be chosen (shown instead of the button or checkbox), or returns null.
 *
 * It provides its own `CategoryClient`, so other modules can use it outside the category routes.
 */
@Component({
  selector: 'app-category-picker-dialog',
  imports: [Pager],
  providers: [CategoryClient],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-picker-dialog.html',
})
export class CategoryPickerDialog {
  readonly storefrontId = input.required<number>();
  readonly heading = input('Choose a category');
  readonly multiple = input(false);
  /** The categories chosen so far. */
  readonly selected = input<readonly CategoryRef[]>([]);
  readonly unavailable = input<(category: Category) => string | null>(() => null);
  readonly picked = output<Category>();
  readonly pickedMany = output<readonly CategoryRef[]>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly page = signal(1);
  protected readonly categories = inject(CategoryClient).page(
    this.storefrontId,
    this.page,
    PAGE_SIZE,
  );

  /** The checked categories in multiple mode, by ID, starting from `selected`. */
  protected readonly checked = linkedSignal(
    () => new Map(this.selected().map((category) => [category.id, category])),
  );
  protected readonly selectedIds = computed(
    () => new Set(this.selected().map((category) => category.id)),
  );

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  protected toggle(category: Category, checked: boolean): void {
    this.checked.update((current) => {
      const next = new Map(current);
      if (checked) {
        next.set(category.id, category);
      } else {
        next.delete(category.id);
      }
      return next;
    });
  }

  protected done(): void {
    this.pickedMany.emit([...this.checked().values()]);
  }

  protected cancel(): void {
    this.dialog().nativeElement.close();
  }
}
