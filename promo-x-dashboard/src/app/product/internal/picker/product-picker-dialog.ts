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
import { ProductClient } from '../product.client';
import { Product, ProductRef } from '../product.model';

const PAGE_SIZE = 10;

/**
 * A modal that lists a storefront's products, newest first and page by page, to choose from. It opens as soon as
 * it's rendered and emits `closed` when the user dismisses it.
 *
 * - Single (default): each row has a Choose button, which emits `picked`. The `selected` product is marked
 *   Current.
 * - Multiple: each row has a checkbox, and Done emits `pickedMany` with every checked product, including ones
 *   checked on other pages and the `selected` ones the user kept.
 *
 * It provides its own `ProductClient`, so other modules can use it outside the product routes.
 */
@Component({
  selector: 'app-product-picker-dialog',
  imports: [Pager],
  providers: [ProductClient],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-picker-dialog.html',
})
export class ProductPickerDialog {
  readonly storefrontId = input.required<number>();
  readonly heading = input('Choose a product');
  readonly multiple = input(false);
  /** The products chosen so far. */
  readonly selected = input<readonly ProductRef[]>([]);
  readonly picked = output<Product>();
  readonly pickedMany = output<readonly ProductRef[]>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly page = signal(1);
  protected readonly products = inject(ProductClient).page(this.storefrontId, this.page, PAGE_SIZE);

  /** The checked products in multiple mode, by ID, starting from `selected`. */
  protected readonly checked = linkedSignal(
    () => new Map(this.selected().map((product) => [product.id, product])),
  );
  protected readonly selectedIds = computed(
    () => new Set(this.selected().map((product) => product.id)),
  );

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  protected toggle(product: Product, checked: boolean): void {
    this.checked.update((current) => {
      const next = new Map(current);
      if (checked) {
        next.set(product.id, toRef(product));
      } else {
        next.delete(product.id);
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

function toRef({ id, externalId, sku, name }: Product): ProductRef {
  return { id, externalId, sku, name };
}
