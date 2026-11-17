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
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { problemDetailOf } from '../../../infrastructure/http';
import { trimmedLength } from '../../../shared/forms';
import { CategoryClient } from '../category.client';
import {
  Category,
  CATEGORY_EXTERNAL_ID_MAX_LENGTH,
  CATEGORY_NAME_MAX_LENGTH,
  CATEGORY_NAME_MIN_LENGTH,
  CategoryRef,
  CategoryUpdate,
} from '../category.model';
import { CategoryPickerDialog } from '../picker/category-picker-dialog';

type Field = 'name' | 'externalId' | 'parentId';

/** What the form shows of the chosen parent. */
type ParentPreview = CategoryRef;

/**
 * A modal that edits a category's name, external ID and parent. It opens as soon as it's rendered, emits `saved`
 * with the category promo-x-core returns, and `closed` when the user dismisses it.
 *
 * The parent is shown as a preview; changing it opens `CategoryPickerDialog` on top.
 */
@Component({
  selector: 'app-category-edit-dialog',
  imports: [ReactiveFormsModule, CategoryPickerDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './category-edit-dialog.html',
})
export class CategoryEditDialog implements OnInit {
  readonly category = input.required<Category>();
  readonly saved = output<Category>();
  readonly closed = output<void>();

  private readonly client = inject(CategoryClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [trimmedLength(CATEGORY_NAME_MIN_LENGTH, CATEGORY_NAME_MAX_LENGTH)]],
    externalId: ['', [trimmedLength(1, CATEGORY_EXTERNAL_ID_MAX_LENGTH)]],
    /** Set only through the picker, so it needs no validation of its own. */
    parentId: [null as number | null],
  });
  protected readonly parent = signal<ParentPreview | null>(null);
  protected readonly picking = signal(false);
  protected readonly pickerSelection = computed(() => {
    const parent = this.parent();
    return parent ? [parent] : [];
  });
  /**
   * The category itself and its direct children can't be its parent, since either would make it its own ancestor.
   * Deeper descendants aren't known from a page of categories.
   */
  protected readonly parentUnavailable = (candidate: Category): string | null => {
    if (candidate.id === this.category().id) {
      return 'This category';
    }
    return candidate.parent?.id === this.category().id ? 'Its child' : null;
  };
  protected readonly saving = signal(false);
  /** An error that isn't about one field. */
  protected readonly formError = signal<string | null>(null);

  private readonly externalId = toSignal(this.form.controls.externalId.valueChanges, {
    initialValue: '',
  });
  /** promo-x-core deactivates the active rules that refer to the old external ID, so the user is told. */
  protected readonly externalIdChanged = computed(
    () => this.externalId().trim() !== (this.category().externalId ?? ''),
  );

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  ngOnInit(): void {
    const { name, externalId, parent } = this.category();
    this.form.setValue({ name, externalId: externalId ?? '', parentId: parent?.id ?? null });
    this.parent.set(parent);
  }

  protected onPicked(picked: Category): void {
    this.setParent({ id: picked.id, name: picked.name, externalId: picked.externalId });
    this.picking.set(false);
  }

  /** Makes the category top-level. */
  protected removeParent(): void {
    this.setParent(null);
  }

  private setParent(parent: ParentPreview | null): void {
    this.parent.set(parent);
    this.form.controls.parentId.setValue(parent?.id ?? null);
    this.form.controls.parentId.markAsDirty();
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
    this.client
      .update(this.category().id, this.toUpdate())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.saved.emit(updated);
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
      return field === 'name' ? 'Name is required.' : 'External ID is required.';
    }
    if (control.hasError('length')) {
      const { min, max } = control.getError('length');
      return `Must be ${min}–${max} characters.`;
    }
    return null;
  }

  /** PUT replaces every field, so the parent is always sent; null makes the category top-level. */
  private toUpdate(): CategoryUpdate {
    const { name, externalId, parentId } = this.form.getRawValue();
    return { name: name.trim(), externalId: externalId.trim(), parentId };
  }

  /**
   * Validation (400) and business (409) errors name the field they're about, such as `externalId` for
   * `CATEGORY_ALREADY_EXISTS` or `parentId` for `PARENT_CATEGORY_NOT_FOUND`, so they're shown under it.
   */
  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 404) {
      this.formError.set('This category no longer exists.');
      return;
    }
    if (status === 403) {
      this.formError.set("You're not allowed to edit categories.");
      return;
    }

    const problem = problemDetailOf(error);
    if (!problem) {
      this.formError.set("Couldn't save the category. Try again.");
      return;
    }

    const unmatched: string[] = [];
    for (const item of problem.errors) {
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
