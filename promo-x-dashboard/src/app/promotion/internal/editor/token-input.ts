import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { RuleIcon } from '../ui/rule-icon';

/**
 * A list of text values edited as chips. Enter, a comma or leaving the field adds what was typed; a pasted list
 * separated by commas or new lines adds each item; Backspace in the empty field removes the last chip.
 */
@Component({
  selector: 'app-token-input',
  imports: [RuleIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div
      class="flex min-h-10 flex-wrap items-center gap-1.5 rounded-md border bg-white px-2 py-1.5 focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-500/30"
      [class.border-gray-300]="!invalid()"
      [class.border-red-500]="invalid()"
    >
      @for (value of values(); track value) {
        <span
          class="inline-flex items-center gap-1 rounded-md bg-gray-100 py-0.5 pr-0.5 pl-2 font-mono text-xs text-gray-800"
          data-testid="token"
        >
          {{ value }}
          <button
            type="button"
            class="rounded p-0.5 text-gray-500 hover:bg-gray-200 hover:text-gray-900 disabled:opacity-50"
            [disabled]="disabled()"
            [attr.aria-label]="'Remove ' + value"
            (click)="remove(value)"
          >
            <app-rule-icon class="size-3" name="close" />
          </button>
        </span>
      }
      <input
        class="min-w-32 flex-1 border-0 bg-transparent px-1 py-0.5 font-mono text-sm outline-none placeholder:font-sans placeholder:text-gray-400"
        autocomplete="off"
        [id]="inputId()"
        [placeholder]="values().length === 0 ? placeholder() : 'Add another…'"
        [disabled]="disabled()"
        [value]="text()"
        [attr.aria-invalid]="invalid() ? true : null"
        [attr.aria-describedby]="describedBy()"
        (input)="text.set($any($event.target).value)"
        (keydown)="onKeydown($event)"
        (paste)="onPaste($event)"
        (blur)="commit()"
      />
    </div>
  `,
})
export class TokenInput {
  readonly values = input.required<readonly string[]>();
  readonly valuesChange = output<readonly string[]>();
  readonly inputId = input.required<string>();
  readonly placeholder = input('Type and press Enter');
  readonly invalid = input(false);
  readonly disabled = input(false);
  readonly describedBy = input<string | null>(null);

  protected readonly text = signal('');

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.commit();
    } else if (event.key === 'Backspace' && this.text() === '' && this.values().length > 0) {
      this.valuesChange.emit(this.values().slice(0, -1));
    }
  }

  protected onPaste(event: ClipboardEvent): void {
    const pasted = event.clipboardData?.getData('text') ?? '';
    if (/[,\n]/.test(pasted)) {
      event.preventDefault();
      this.add(pasted);
    }
  }

  protected commit(): void {
    this.add(this.text());
    this.text.set('');
  }

  protected remove(value: string): void {
    this.valuesChange.emit(this.values().filter((item) => item !== value));
  }

  private add(text: string): void {
    const added = text
      .split(/[,\n]/)
      .map((item) => item.trim())
      .filter((item) => item !== '' && !this.values().includes(item));
    if (added.length > 0) {
      this.valuesChange.emit([...this.values(), ...new Set(added)]);
    }
  }
}
