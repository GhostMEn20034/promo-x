import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Pager } from '../../../shared/ui';
import { CategoryClient } from '../category.client';
import { Category } from '../category.model';

const PAGE_SIZE = 10;

/**
 * A modal that lists a storefront's categories, page by page, to pick one as a parent. It opens as soon as it's
 * rendered, emits `picked` with the chosen category, and `closed` when the user dismisses it.
 *
 * The category being edited can't be picked, and neither can its direct children, since either would make it its
 * own ancestor. Deeper descendants aren't known from a page of categories, so promo-x-core would have to reject
 * those.
 */
@Component({
  selector: 'app-category-picker-dialog',
  imports: [Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-picker-dialog.html',
})
export class CategoryPickerDialog {
  readonly storefrontId = input.required<number>();
  /** The category that gets the parent. */
  readonly categoryId = input.required<number>();
  /** The parent picked so far, marked in the list. */
  readonly selectedId = input<number | null>(null);
  readonly picked = output<Category>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly page = signal(1);
  protected readonly categories = inject(CategoryClient).page(
    this.storefrontId,
    this.page,
    PAGE_SIZE,
  );

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  /** Why a category can't be picked, or null when it can. */
  protected unavailable(category: Category): string | null {
    if (category.id === this.categoryId()) {
      return 'This category';
    }
    if (category.parent?.id === this.categoryId()) {
      return 'Its child';
    }
    return null;
  }

  protected pick(category: Category): void {
    this.picked.emit(category);
  }

  protected cancel(): void {
    this.dialog().nativeElement.close();
  }
}
