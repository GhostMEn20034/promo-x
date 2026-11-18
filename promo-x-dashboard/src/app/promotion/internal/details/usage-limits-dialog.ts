import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
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
import {
  AbstractControl,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { problemDetailOf } from '../../../infrastructure/http';
import { PromotionClient } from '../promotion.client';
import { MAX_INT, PromotionRule, SavedPromotionRule, UsageLimitsInput } from '../promotion.model';

type RemainingMode = 'keep' | 'set';

function wholeNumber(min: number) {
  return (control: AbstractControl<number | null>): ValidationErrors | null => {
    const value = control.value;
    if (value === null) {
      return { required: true };
    }
    return Number.isInteger(value) && value >= min && value <= MAX_INT
      ? null
      : { wholeNumber: { min } };
  };
}

/**
 * A modal that turns a rule's usage limit on or off, changes the total, and either keeps the uses left or sets
 * them. It opens as soon as it's rendered, emits `saved` with the updated rule, and `closed` when dismissed.
 */
@Component({
  selector: 'app-usage-limits-dialog',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl bg-white p-0 shadow-xl backdrop:bg-black/40"
      aria-labelledby="usage-limits-title"
      (close)="closed.emit()"
      (cancel)="onCancel($event)"
    >
      <form class="p-6" novalidate [formGroup]="form" (ngSubmit)="submit()">
        <h2 id="usage-limits-title" class="text-lg font-semibold text-gray-900">Usage limit</h2>
        <p class="mt-1 text-sm break-words text-gray-500">{{ rule().name }}</p>

        <label class="mt-5 flex cursor-pointer items-start gap-3">
          <input
            id="usage-has-limit"
            type="checkbox"
            class="peer sr-only"
            formControlName="hasUsageLimit"
          />
          <span
            class="relative mt-0.5 h-5 w-9 shrink-0 rounded-full bg-gray-300 transition-colors peer-checked:bg-blue-600 peer-focus-visible:ring-2 peer-focus-visible:ring-blue-500 after:absolute after:top-0.5 after:left-0.5 after:size-4 after:rounded-full after:bg-white after:shadow after:transition-transform peer-checked:after:translate-x-4"
            aria-hidden="true"
          ></span>
          <span>
            <span class="block text-sm font-medium text-gray-900"
              >Limit how many times it applies</span
            >
            <span class="block text-xs text-gray-500">
              Turning the limit off keeps the numbers, for when it's turned back on.
            </span>
          </span>
        </label>

        @if (values().hasUsageLimit) {
          <div class="mt-5 space-y-4 border-l-2 border-gray-100 pl-4">
            <div class="max-w-48">
              <label class="block text-sm font-medium text-gray-700" for="usage-limit"
                >Total uses</label
              >
              <input
                id="usage-limit"
                type="number"
                min="1"
                step="1"
                inputmode="numeric"
                class="mt-1 w-full rounded-md border border-gray-300 px-3 py-2 text-sm tabular-nums focus:border-blue-500 focus:ring-2 focus:ring-blue-500/30 focus:outline-none aria-invalid:border-red-500"
                formControlName="usageLimit"
                [attr.aria-invalid]="errorOf('usageLimit') ? true : null"
                aria-describedby="usage-limit-error"
              />
              @if (errorOf('usageLimit'); as message) {
                <p id="usage-limit-error" class="mt-1 text-sm text-red-600">{{ message }}</p>
              }
            </div>

            <fieldset>
              <legend class="text-sm font-medium text-gray-700">Uses left</legend>
              <label class="mt-2 flex items-center gap-2 text-sm text-gray-700">
                <input type="radio" value="keep" formControlName="remainingMode" />
                @if (rule().hasUsageLimit) {
                  Keep the current count ({{ rule().remainingUses ?? 0 }} left)
                } @else {
                  Keep the stored count ({{ rule().remainingUses ?? 0 }})
                }
              </label>
              <label class="mt-2 flex flex-wrap items-center gap-2 text-sm text-gray-700">
                <input type="radio" value="set" formControlName="remainingMode" />
                Set to
                <input
                  id="usage-remaining"
                  type="number"
                  min="0"
                  step="1"
                  inputmode="numeric"
                  class="w-28 rounded-md border border-gray-300 px-2 py-1 text-sm tabular-nums focus:border-blue-500 focus:ring-2 focus:ring-blue-500/30 focus:outline-none disabled:bg-gray-50 disabled:text-gray-400 aria-invalid:border-red-500"
                  aria-label="Uses left"
                  formControlName="remainingUses"
                  [attr.aria-invalid]="errorOf('remainingUses') ? true : null"
                  aria-describedby="usage-remaining-error"
                />
                @if (values().remainingMode === 'set' && values().usageLimit) {
                  <button
                    type="button"
                    class="text-xs font-medium text-blue-700 hover:underline"
                    (click)="resetToLimit()"
                  >
                    Reset to {{ values().usageLimit }}
                  </button>
                }
              </label>
              @if (errorOf('remainingUses'); as message) {
                <p id="usage-remaining-error" class="mt-1 text-sm text-red-600">{{ message }}</p>
              }
              @if (keptAboveLimit()) {
                <p
                  class="mt-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800"
                  data-testid="above-limit"
                >
                  The {{ rule().remainingUses }} uses left would stay above the new total. Set them
                  to lower them.
                </p>
              }
            </fieldset>
          </div>
        }

        @if (formError(); as message) {
          <p role="alert" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {{ message }}
          </p>
        }

        <div class="mt-6 flex justify-end gap-3">
          <button
            type="button"
            class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 disabled:opacity-50"
            [disabled]="saving()"
            (click)="dialog.close()"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            [disabled]="saving()"
          >
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </form>
    </dialog>
  `,
})
export class UsageLimitsDialog implements OnInit {
  readonly rule = input.required<PromotionRule>();
  readonly saved = output<SavedPromotionRule>();
  readonly closed = output<void>();

  private readonly client = inject(PromotionClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      hasUsageLimit: [false],
      usageLimit: new FormControl<number | null>(null, [wholeNumber(1)]),
      remainingMode: ['keep' as RemainingMode],
      remainingUses: new FormControl<number | null>({ value: null, disabled: true }, [
        wholeNumber(0),
      ]),
    },
    { validators: [remainingWithinLimit] },
  );
  protected readonly values = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
    this.form.controls.remainingMode.valueChanges.pipe(takeUntilDestroyed()).subscribe((mode) => {
      if (mode === 'set') {
        this.form.controls.remainingUses.enable();
      } else {
        this.form.controls.remainingUses.disable();
      }
    });
  }

  ngOnInit(): void {
    const rule = this.rule();
    // An unlimited rule's stored count is likely stale, so turning the limit on starts by setting it.
    this.form.setValue({
      hasUsageLimit: rule.hasUsageLimit,
      usageLimit: rule.usageLimit,
      remainingMode: rule.hasUsageLimit ? 'keep' : 'set',
      remainingUses: rule.hasUsageLimit ? rule.remainingUses : rule.usageLimit,
    });
  }

  /** The current count is kept, but it's above the new total. */
  protected keptAboveLimit(): boolean {
    const { hasUsageLimit, usageLimit, remainingMode } = this.values();
    const remaining = this.rule().remainingUses;
    return (
      hasUsageLimit === true &&
      remainingMode === 'keep' &&
      typeof usageLimit === 'number' &&
      remaining !== null &&
      remaining > usageLimit
    );
  }

  protected resetToLimit(): void {
    this.form.controls.remainingUses.setValue(this.form.controls.usageLimit.value);
  }

  protected errorOf(field: 'usageLimit' | 'remainingUses'): string | null {
    const control = this.form.controls[field];
    if (field === 'remainingUses' && this.form.hasError('aboveLimit') && control.enabled) {
      return `At most the total uses (${this.form.controls.usageLimit.value}).`;
    }
    if (!control.errors || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('server')) {
      return control.getError('server');
    }
    if (control.hasError('required')) {
      return 'Required.';
    }
    return field === 'usageLimit' ? 'A whole number, 1 or more.' : 'A whole number, 0 or more.';
  }

  protected onCancel(event: Event): void {
    if (this.saving()) {
      event.preventDefault();
    }
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    const limited = this.form.controls.hasUsageLimit.value;
    // With the limit off, only the switch matters: the numbers are sent back as they are.
    if (limited && this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.formError.set(null);
    this.client
      .updateUsageLimits(this.rule().id, this.toInput())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.saved.emit(saved);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.showError(error);
        },
      });
  }

  private toInput(): UsageLimitsInput {
    const { hasUsageLimit, usageLimit, remainingMode, remainingUses } = this.form.getRawValue();
    if (!hasUsageLimit) {
      return {
        hasUsageLimit: false,
        usageLimit: this.rule().usageLimit ?? 1,
        mutateRemainingUses: false,
      };
    }
    return remainingMode === 'set'
      ? {
          hasUsageLimit,
          usageLimit: usageLimit!,
          mutateRemainingUses: true,
          remainingUses: remainingUses!,
        }
      : { hasUsageLimit, usageLimit: usageLimit!, mutateRemainingUses: false };
  }

  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 404) {
      this.formError.set('This rule no longer exists.');
      return;
    }
    if (status === 403) {
      this.formError.set("You're not allowed to change usage limits.");
      return;
    }
    const problem = problemDetailOf(error);
    if (!problem) {
      this.formError.set("Couldn't save the usage limit. Try again.");
      return;
    }
    const unmatched: string[] = [];
    for (const item of problem.errors) {
      const control =
        item.path === 'usageLimit' || item.path === 'remainingUses'
          ? this.form.controls[item.path]
          : null;
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

/** Setting the uses left above the total would be capped by promo-x-core, so it's caught here instead. */
function remainingWithinLimit(group: AbstractControl): ValidationErrors | null {
  const limit = group.get('usageLimit')?.value as number | null;
  const remaining = group.get('remainingUses');
  if (!remaining?.enabled || remaining.value === null || limit === null) {
    return null;
  }
  return remaining.value > limit ? { aboveLimit: true } : null;
}
