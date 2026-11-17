import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { CategoryClient, isId } from '../category.client';

@Component({
  selector: 'app-category-details-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="text-sm text-blue-700 hover:underline" routerLink="..">← All categories</a>

    @if (notFound()) {
      <p class="mt-4 text-sm text-gray-600">This category doesn't exist in this storefront.</p>
    } @else if (category.error()) {
      <div role="alert" class="mt-4 rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
        Couldn't load the category.
        <button type="button" class="ml-2 font-medium underline" (click)="category.reload()">
          Try again
        </button>
      </div>
    } @else if (category.hasValue()) {
      @let details = category.value();
      <article class="mt-4 rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <h2 class="text-xl font-semibold break-words text-gray-900">{{ details.name }}</h2>
        <dl class="mt-6 grid grid-cols-[max-content_1fr] gap-x-8 gap-y-4 text-sm">
          <dt class="text-gray-500">ID</dt>
          <dd class="text-gray-900">{{ details.id }}</dd>
          <dt class="text-gray-500">External ID</dt>
          <dd class="font-mono text-xs break-all text-gray-900">{{ details.externalId ?? '—' }}</dd>
          <dt class="text-gray-500">Parent</dt>
          <dd>
            @if (details.parent; as parent) {
              <a class="text-blue-700 hover:underline" [routerLink]="['..', parent.id]">{{
                parent.name
              }}</a>
              <span class="ml-1 text-gray-400">#{{ parent.id }}</span>
            } @else {
              <span class="text-gray-500">None, it's a top-level category</span>
            }
          </dd>
        </dl>
      </article>
    } @else {
      <p class="mt-4 text-sm text-gray-500">Loading category…</p>
    }
  `,
})
export class CategoryDetailsPage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });
  /** The `:categoryId` route parameter. */
  readonly categoryId = input.required<number, unknown>({ transform: numberAttribute });

  protected readonly category = inject(CategoryClient).byId(this.categoryId);

  /**
   * `GET /api/category/{id}` doesn't check the storefront, so a category of another storefront is treated as
   * missing here instead of being shown under the wrong storefront.
   */
  protected readonly notFound = computed(() => {
    if (!isId(this.categoryId())) {
      return true;
    }
    const error = this.category.error();
    if (error) {
      return error instanceof HttpErrorResponse && error.status === 404;
    }
    return this.category.hasValue() && this.category.value().storefrontId !== this.storefrontId();
  });
}
