import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** A placeholder for a storefront section that isn't connected to promo-x-core yet. */
@Component({
  selector: 'app-coming-soon-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="rounded-xl border border-dashed border-gray-300 bg-white px-6 py-12 text-center"
      [attr.aria-label]="section()"
    >
      <h2 class="text-lg font-semibold text-gray-900">{{ section() }} are coming soon</h2>
      <p class="mt-2 text-sm text-gray-500">This section isn't connected to promo-x-core yet.</p>
    </section>
  `,
})
export class ComingSoonPage {
  /** The section's name, from the route's `data`. */
  readonly section = input.required<string>();
}
