import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RuleIcon } from './rule-icon';

/** How many more times a rule can be applied: a bar of the remaining uses, or "Unlimited". */
@Component({
  selector: 'app-usage-meter',
  imports: [RuleIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    @if (hasUsageLimit()) {
      <p class="flex items-baseline gap-1.5" data-testid="usage-remaining">
        <span class="text-3xl font-semibold text-gray-900 tabular-nums">{{
          remaining()
        }}</span>&ngsp;
        <span class="text-sm text-gray-500">of {{ limit() }} uses left</span>
      </p>
      <div
        class="mt-3 h-2 overflow-hidden rounded-full bg-gray-100"
        role="meter"
        aria-label="Remaining uses"
        [attr.aria-valuemin]="0"
        [attr.aria-valuemax]="limit()"
        [attr.aria-valuenow]="remaining()"
      >
        <div
          class="h-full rounded-full transition-[width]"
          [class]="barColour()"
          [style.width.%]="percent()"
        ></div>
      </div>
      <p class="mt-2 text-xs" [class]="remaining() === 0 ? 'text-red-700' : 'text-gray-500'">
        @if (remaining() === 0) {
          Used up: the rule no longer applies.
        } @else {
          {{ used() }} used.
          @if (low()) {
            <span class="font-medium text-amber-700">Running low.</span>
          }
        }
      </p>
    } @else {
      <p class="flex items-center gap-2" data-testid="usage-remaining">
        <app-rule-icon class="size-8 text-gray-400" name="infinity" />
        <span class="text-xl font-semibold text-gray-900">Unlimited</span>
      </p>
      <p class="mt-2 text-xs text-gray-500">The rule can be applied any number of times.</p>
    }
  `,
})
export class UsageMeter {
  readonly hasUsageLimit = input.required<boolean>();
  readonly usageLimit = input.required<number | null>();
  readonly remainingUses = input.required<number | null>();

  protected readonly limit = computed(() => Math.max(this.usageLimit() ?? 0, 0));
  protected readonly remaining = computed(() => Math.max(this.remainingUses() ?? 0, 0));
  protected readonly used = computed(() => Math.max(this.limit() - this.remaining(), 0));
  protected readonly percent = computed(() =>
    this.limit() === 0 ? 0 : Math.min((this.remaining() / this.limit()) * 100, 100),
  );
  /** Less than a tenth left. */
  protected readonly low = computed(() => this.remaining() > 0 && this.percent() < 10);
  protected readonly barColour = computed(() =>
    this.remaining() === 0 ? 'bg-red-500' : this.low() ? 'bg-amber-500' : 'bg-emerald-500',
  );
}
