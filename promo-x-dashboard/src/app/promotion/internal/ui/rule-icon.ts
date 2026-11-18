import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconName } from '../rule/rule-catalog';

export type RuleIconName = IconName | 'group' | 'plus' | 'trash' | 'pencil' | 'infinity' | 'close';

const PATHS: Readonly<Record<RuleIconName, readonly string[]>> = {
  ticket: [
    'M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z',
    'M14 5v2M14 11v2M14 17v2',
  ],
  user: ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z'],
  receipt: ['M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2z', 'M9 8h6M9 12h6M9 16h3'],
  box: ['M21 8l-9-5-9 5 9 5 9-5z', 'M3 8v8l9 5 9-5V8', 'M12 13v8'],
  barcode: ['M4 5v14M8 5v14M11 5v14M15 5v14M18 5v14M21 5v14', 'M2 5v14'],
  folder: ['M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'],
  layers: ['M12 3l9 5-9 5-9-5 9-5z', 'M3 13l9 5 9-5'],
  percent: [
    'M19 5L5 19',
    'M7 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
    'M17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  ],
  gift: [
    'M3 8h18v4H3z',
    'M5 12v9h14v-9',
    'M12 8v13',
    'M12 8S10.5 3 8 3a2.5 2.5 0 0 0 0 5',
    'M12 8s1.5-5 4-5a2.5 2.5 0 0 1 0 5',
  ],
  cart: [
    'M3 4h2l2.4 11h11.2L21 8H6.2',
    'M9 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
    'M18 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  ],
  truck: [
    'M2 6h12v10H2z',
    'M14 10h4l3 3v3h-7',
    'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
    'M17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  ],
  group: ['M4 6h16', 'M8 12h12', 'M8 18h12', 'M4 6v12h4', 'M4 12h4'],
  plus: ['M12 5v14M5 12h14'],
  trash: ['M4 7h16', 'M10 11v6M14 11v6', 'M6 7l1 13h10l1-13', 'M9 7V4h6v3'],
  pencil: [
    'M16.86 3.49a2.1 2.1 0 0 1 2.97 2.97L7.5 18.79 3 20l1.21-4.5L16.86 3.49z',
    'M15 5.35l3.65 3.65',
  ],
  infinity: [
    'M12 12c-2-2.67-4-4-6-4a4 4 0 1 0 0 8c2 0 4-1.33 6-4zm0 0c2 2.67 4 4 6 4a4 4 0 0 0 0-8c-2 0-4 1.33-6 4z',
  ],
  close: ['M6 6l12 12M18 6L6 18'],
};

/** A small line icon. Decorative: it's hidden from assistive technology. */
@Component({
  selector: 'app-rule-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex shrink-0', 'aria-hidden': 'true' },
  template: `
    <svg
      class="size-full"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.75"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      @for (path of paths(); track $index) {
        <path [attr.d]="path" />
      }
    </svg>
  `,
})
export class RuleIcon {
  readonly name = input.required<RuleIconName>();

  protected readonly paths = computed(() => PATHS[this.name()]);
}
