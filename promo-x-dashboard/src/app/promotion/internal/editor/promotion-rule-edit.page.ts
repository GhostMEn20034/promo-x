import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { isId, PromotionClient } from '../promotion.client';
import { RuleEditor } from './rule-editor';

/** `…/promotion-rules/:ruleId/edit`: changes everything but the usage limit, then goes back to the rule. */
@Component({
  selector: 'app-promotion-rule-edit-page',
  imports: [RouterLink, RuleEditor],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="text-sm text-blue-700 hover:underline" routerLink="..">← Back to the rule</a>

    @if (notFound()) {
      <p class="mt-4 text-sm text-gray-600">This rule doesn't exist in this storefront.</p>
    } @else if (rule.error()) {
      <div role="alert" class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
        Couldn't load the rule.
        <button type="button" class="ml-2 font-medium underline" (click)="rule.reload()">
          Try again
        </button>
      </div>
    } @else if (rule.hasValue()) {
      <h2 class="mt-4 text-lg font-semibold break-words text-gray-900">
        Edit {{ rule.value().name }}
      </h2>
      <p class="mt-1 text-sm text-gray-500">
        ID {{ rule.value().id }}. The usage limit is changed on the rule's page.
      </p>
      <app-rule-editor
        class="mt-6 block"
        [storefrontId]="storefrontId()"
        [rule]="rule.value()"
        (saved)="back()"
        (cancelled)="back()"
      />
    } @else {
      <p class="mt-4 text-sm text-gray-500">Loading rule…</p>
    }
  `,
})
export class PromotionRuleEditPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The `:ruleId` route parameter. */
  readonly ruleId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly rule = inject(PromotionClient).byId(this.ruleId);

  /** `GET /api/promotion/{id}` doesn't check the storefront, so another storefront's rule counts as missing. */
  protected readonly notFound = computed(() => {
    if (!isId(this.ruleId())) {
      return true;
    }
    const error = this.rule.error();
    if (error) {
      return error instanceof HttpErrorResponse && error.status === 404;
    }
    return this.rule.hasValue() && this.rule.value().storefrontId !== this.storefrontId();
  });

  protected back(): void {
    void this.router.navigate(['..'], { relativeTo: this.route });
  }
}
