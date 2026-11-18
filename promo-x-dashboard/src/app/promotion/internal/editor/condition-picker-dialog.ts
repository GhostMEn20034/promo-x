import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  output,
  viewChild,
} from '@angular/core';
import { LeafConditionType } from '../promotion.model';
import { CONDITION_FAMILIES, CONDITION_TYPES_BY_FAMILY } from '../rule/rule-catalog';
import { RuleIcon } from '../ui/rule-icon';

/** A modal listing the condition types by family, to add one. It opens as soon as it's rendered. */
@Component({
  selector: 'app-condition-picker-dialog',
  imports: [RuleIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="m-auto max-h-[calc(100%-2rem)] w-[calc(100%-2rem)] max-w-3xl rounded-xl bg-white p-0 shadow-xl backdrop:bg-black/40"
      aria-labelledby="condition-picker-title"
      (close)="closed.emit()"
    >
      <div
        class="sticky top-0 flex items-start justify-between gap-4 border-b border-gray-100 bg-white px-6 py-4"
      >
        <div>
          <h2 id="condition-picker-title" class="text-lg font-semibold text-gray-900">
            Add a condition
          </h2>
          <p class="mt-0.5 text-sm text-gray-500">What should the cart or the shopper match?</p>
        </div>
        <button
          type="button"
          class="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900"
          aria-label="Close"
          (click)="dialog.close()"
        >
          <app-rule-icon class="size-5" name="close" />
        </button>
      </div>

      <div class="space-y-6 px-6 py-5">
        @for (group of groups; track group.family) {
          @let family = families[group.family];
          <section [attr.aria-labelledby]="'family-' + group.family">
            <h3
              class="flex items-center gap-2 text-xs font-semibold tracking-wide text-gray-500 uppercase"
              [id]="'family-' + group.family"
            >
              <span class="grid size-6 place-items-center rounded-md p-1" [class]="family.tile">
                <app-rule-icon class="size-4" [name]="family.icon" />
              </span>
              {{ family.label }}
            </h3>
            @if (family.note) {
              <p class="mt-1 text-xs text-gray-500">{{ family.note }}</p>
            }
            <div class="mt-2 grid gap-2 sm:grid-cols-2">
              @for (spec of group.types; track spec.type) {
                <button
                  type="button"
                  class="rounded-lg border border-gray-200 px-3 py-2.5 text-left hover:border-blue-300 hover:bg-blue-50/50 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
                  [attr.data-type]="spec.type"
                  (click)="pick(spec.type)"
                >
                  <span class="block text-sm font-medium text-gray-900">{{ spec.label }}</span>
                  <span class="mt-0.5 block text-xs text-gray-500">{{ spec.description }}</span>
                </button>
              }
            </div>
          </section>
        }
      </div>
    </dialog>
  `,
})
export class ConditionPickerDialog {
  readonly picked = output<LeafConditionType>();
  readonly closed = output<void>();

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly groups = CONDITION_TYPES_BY_FAMILY;
  protected readonly families = CONDITION_FAMILIES;

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  protected pick(type: LeafConditionType): void {
    this.picked.emit(type);
    this.dialog().nativeElement.close();
  }
}
