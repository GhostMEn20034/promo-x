import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { CategoryPickerDialog, CategoryRef } from '../../../category';
import { problemDetailOf } from '../../../infrastructure/http';
import { trimmedLength } from '../../../shared/forms';
import { ProductClient } from '../product.client';
import {
  Product,
  PRODUCT_FIELD_MAX_LENGTH,
  ProductInput,
  SavedProduct,
  withCategories,
} from '../product.model';

type Field = 'externalId' | 'sku' | 'name';

const FIELD_LABELS: Record<Field, string> = {
  externalId: 'External ID',
  sku: 'SKU',
  name: 'Name',
};

/**
 * A modal that creates a product (`product` is null) or edits one. It opens as soon as it's rendered, emits
 * `saved` with the product and its chosen categories, and `closed` when the user dismisses it.
 *
 * Categories are chosen in `CategoryPickerDialog`, opened on top.
 */
@Component({
  selector: 'app-product-form-dialog',
  imports: [ReactiveFormsModule, CategoryPickerDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-form-dialog.html',
})
export class ProductFormDialog implements OnInit {
  readonly storefrontId = input.required<number>();
  /** The product to edit, or null to create one. */
  readonly product = input<Product | null>(null);
  readonly saved = output<Product>();
  readonly closed = output<void>();

  private readonly client = inject(ProductClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly fields: readonly Field[] = ['name', 'sku', 'externalId'];
  protected readonly fieldLabels = FIELD_LABELS;
  protected readonly form = inject(NonNullableFormBuilder).group({
    externalId: ['', [trimmedLength(1, PRODUCT_FIELD_MAX_LENGTH)]],
    sku: ['', [trimmedLength(1, PRODUCT_FIELD_MAX_LENGTH)]],
    name: ['', [trimmedLength(1, PRODUCT_FIELD_MAX_LENGTH)]],
  });
  protected readonly categories = signal<readonly CategoryRef[]>([]);
  protected readonly categoriesError = signal<string | null>(null);
  protected readonly picking = signal(false);
  protected readonly saving = signal(false);
  /** An error that isn't about one field. */
  protected readonly formError = signal<string | null>(null);

  protected readonly editing = computed(() => this.product() !== null);

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  ngOnInit(): void {
    const product = this.product();
    if (product) {
      this.form.setValue({ externalId: product.externalId, sku: product.sku, name: product.name });
      this.categories.set(product.categories);
    }
  }

  protected onPicked(categories: readonly CategoryRef[]): void {
    this.categories.set(categories);
    this.categoriesError.set(null);
    this.picking.set(false);
  }

  protected removeCategory(id: number): void {
    this.categories.update((categories) => categories.filter((category) => category.id !== id));
    this.categoriesError.set(null);
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.formError.set(null);
    this.categoriesError.set(null);
    const chosen = this.categories();
    this.request(this.toInput())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.saved.emit(withCategories(saved, chosen));
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.showError(error);
        },
      });
  }

  protected cancel(): void {
    this.dialog().nativeElement.close();
  }

  /** Esc closes the dialog, but not while the request is in flight. */
  protected onCancel(event: Event): void {
    if (this.saving()) {
      event.preventDefault();
    }
  }

  protected errorOf(field: Field): string | null {
    const control = this.form.controls[field];
    if (!control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('server')) {
      return control.getError('server');
    }
    if (control.hasError('required')) {
      return `${FIELD_LABELS[field]} is required.`;
    }
    if (control.hasError('length')) {
      return `At most ${control.getError('length').max} characters.`;
    }
    return null;
  }

  private request(input: ProductInput): Observable<SavedProduct> {
    const product = this.product();
    return product
      ? this.client.update(product.id, input)
      : this.client.create(this.storefrontId(), input);
  }

  private toInput(): ProductInput {
    const { externalId, sku, name } = this.form.getRawValue();
    return {
      externalId: externalId.trim(),
      sku: sku.trim(),
      name: name.trim(),
      categoryIds: this.categories().map((category) => category.id),
    };
  }

  /**
   * Validation (400) and business (409) errors name their field, such as `externalId` for
   * `EXTERNAL_ID_ALREADY_EXISTS` or `categoryIds` for `CATEGORY_IDS_NOT_FOUND`, so they're shown under it.
   */
  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 404) {
      this.formError.set('This product no longer exists.');
      return;
    }
    if (status === 403) {
      this.formError.set(
        this.editing()
          ? "You're not allowed to edit products."
          : "You're not allowed to create products.",
      );
      return;
    }

    const problem = problemDetailOf(error);
    if (!problem) {
      this.formError.set("Couldn't save the product. Try again.");
      return;
    }

    const unmatched: string[] = [];
    for (const item of problem.errors) {
      if (item.path?.startsWith('categoryIds')) {
        this.categoriesError.set(item.message);
        continue;
      }
      const control = item.path ? this.form.get(item.path) : null;
      if (control) {
        control.setErrors({ server: item.message });
        control.markAsTouched();
      } else {
        unmatched.push(item.message);
      }
    }
    this.formError.set(unmatched.length > 0 ? unmatched.join(' ') : null);
  }
}
