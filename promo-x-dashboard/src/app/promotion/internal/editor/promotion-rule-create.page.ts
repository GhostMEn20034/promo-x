import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SavedPromotionRule } from '../promotion.model';
import { RuleEditor } from './rule-editor';

/** `…/promotion-rules/new`: builds a rule, then opens it. */
@Component({
  selector: 'app-promotion-rule-create-page',
  imports: [RouterLink, RuleEditor],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="text-sm text-blue-700 hover:underline" routerLink="..">← All rules</a>
    <h2 class="mt-4 text-lg font-semibold text-gray-900">New promotion rule</h2>
    <p class="mt-1 text-sm text-gray-500">
      Say when the rule applies and what it does; the preview reads it back as you go.
    </p>
    <app-rule-editor
      class="mt-6 block"
      [storefrontId]="storefrontId()"
      (saved)="open($event)"
      (cancelled)="back()"
    />
  `,
})
export class PromotionRuleCreatePage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected open(rule: SavedPromotionRule): void {
    void this.router.navigate(['..', rule.id], { relativeTo: this.route });
  }

  protected back(): void {
    void this.router.navigate(['..'], { relativeTo: this.route });
  }
}
