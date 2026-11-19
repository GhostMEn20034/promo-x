import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  computed,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Observable } from 'rxjs';
import { problemDetailOf } from '../../../infrastructure/http';
import { StorefrontClient } from '../storefront.client';
import {
  Storefront,
  STOREFRONT_NAME_MAX_LENGTH,
  STOREFRONT_WEBSITE_MAX_LENGTH,
  StorefrontInput,
} from '../storefront.model';

type Field = 'name' | 'website';

/**
 * A modal that creates a storefront (`storefront` is null) or edits one's name and website. It opens as soon as
 * it's rendered, emits `saved` with the storefront promo-x-core returns, and `closed` when the user dismisses it.
 */
@Component({
  selector: 'app-storefront-form-dialog',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './storefront-form-dialog.html',
})
export class StorefrontFormDialog implements OnInit {
  /** The storefront to edit, or null to create one. */
  readonly storefront = input<Storefront | null>(null);
  readonly saved = output<Storefront>();
  readonly closed = output<void>();

  private readonly client = inject(StorefrontClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [notBlank, Validators.maxLength(STOREFRONT_NAME_MAX_LENGTH)]],
    website: ['', [Validators.maxLength(STOREFRONT_WEBSITE_MAX_LENGTH)]],
  });
  protected readonly saving = signal(false);
  /** An error that isn't about one field. */
  protected readonly formError = signal<string | null>(null);

  protected readonly editing = computed(() => this.storefront() !== null);

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  ngOnInit(): void {
    const storefront = this.storefront();
    if (storefront) {
      this.form.setValue({ name: storefront.name, website: storefront.website ?? '' });
    }
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
    this.request(this.toInput())
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
      return 'Name is required.';
    }
    if (control.hasError('maxlength')) {
      return `At most ${control.getError('maxlength').requiredLength} characters.`;
    }
    return null;
  }

  private request(input: StorefrontInput): Observable<Storefront> {
    const storefront = this.storefront();
    return storefront ? this.client.update(storefront.id, input) : this.client.create(input);
  }

  /** The backend rejects an empty website (it must be 1–255 characters), so a blank one is sent as null. */
  private toInput(): StorefrontInput {
    const { name, website } = this.form.getRawValue();
    return { name: name.trim(), website: website.trim() || null };
  }

  private showError(error: unknown): void {
    const problem = problemDetailOf(error);

    if (problem?.code === 'VALIDATION_ERROR') {
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
      return;
    }

    const status = error instanceof HttpErrorResponse ? error.status : 0;
    this.formError.set(
      status === 404
        ? 'This storefront no longer exists.'
        : status === 403
          ? `You're not allowed to ${this.editing() ? 'edit' : 'create'} storefronts.`
          : "Couldn't save the storefront. Try again.",
    );
  }
}

function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() === '' ? { required: true } : null;
}
