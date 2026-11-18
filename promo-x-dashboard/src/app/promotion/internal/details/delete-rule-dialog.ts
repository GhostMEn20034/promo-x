import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PromotionClient } from '../promotion.client';
import { PromotionRule } from '../promotion.model';

/** Asks to confirm deleting the rule, then deletes it. Emits `deleted` once it's gone, `closed` when dismissed. */
@Component({
  selector: 'app-delete-rule-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl bg-white p-0 shadow-xl backdrop:bg-black/40"
      aria-labelledby="delete-rule-title"
      aria-describedby="delete-rule-description"
      (close)="closed.emit()"
      (cancel)="onCancel($event)"
    >
      <div class="p-6">
        <h2 id="delete-rule-title" class="text-lg font-semibold text-gray-900">
          Delete this rule?
        </h2>
        <p id="delete-rule-description" class="mt-2 text-sm text-gray-600">
          <strong class="font-medium break-words text-gray-900">{{ rule().name }}</strong> will no
          longer be evaluated against carts. This can't be undone; to pause the rule instead, make
          it inactive.
        </p>

        @if (error(); as message) {
          <p role="alert" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {{ message }}
          </p>
        }

        <div class="mt-6 flex justify-end gap-3">
          <button
            type="button"
            class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-50 disabled:opacity-50"
            [disabled]="deleting()"
            (click)="dialog.close()"
          >
            Cancel
          </button>
          <button
            type="button"
            class="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
            [disabled]="deleting()"
            (click)="confirm()"
          >
            {{ deleting() ? 'Deleting…' : 'Delete rule' }}
          </button>
        </div>
      </div>
    </dialog>
  `,
})
export class DeleteRuleDialog {
  readonly rule = input.required<PromotionRule>();
  readonly deleted = output<void>();
  readonly closed = output<void>();

  private readonly client = inject(PromotionClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly deleting = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  protected onCancel(event: Event): void {
    if (this.deleting()) {
      event.preventDefault();
    }
  }

  protected confirm(): void {
    if (this.deleting()) {
      return;
    }
    this.deleting.set(true);
    this.error.set(null);
    this.client
      .delete(this.rule().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deleting.set(false);
          this.deleted.emit();
        },
        error: (error: unknown) => {
          this.deleting.set(false);
          const status = error instanceof HttpErrorResponse ? error.status : 0;
          // Already gone: what the user wanted
          if (status === 404) {
            this.deleted.emit();
            return;
          }
          this.error.set(
            status === 403
              ? "You're not allowed to delete rules."
              : "Couldn't delete the rule. Try again.",
          );
        },
      });
  }
}
