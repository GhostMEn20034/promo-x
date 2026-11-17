import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../../infrastructure/http';
import { Category, CategoryUpdate } from './category.model';

/**
 * promo-x-core's category endpoints. Reads are resources that follow their signals; writes are one-off requests.
 *
 * The resource methods must be called in an injection context, such as a component's field initializer, and the
 * resource lives as long as that context.
 */
@Injectable()
export class CategoryClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL).replace(/\/+$/, '')}/api/category`;

  /** One page of the storefront's categories, sorted by ID. `page` is 1-based. */
  page(
    storefrontId: Signal<number>,
    page: Signal<number>,
    size: number,
  ): HttpResourceRef<Page<Category> | undefined> {
    return httpResource<Page<Category>>(() =>
      isId(storefrontId())
        ? {
            url: `${this.url}/`,
            params: { page: page(), size },
            headers: { 'X-Storefront-Id': String(storefrontId()) },
          }
        : undefined,
    );
  }

  /** The category with this ID. Nothing is requested while the ID isn't a positive integer. */
  byId(id: Signal<number>): HttpResourceRef<Category | undefined> {
    return httpResource<Category>(() => (isId(id()) ? `${this.url}/${id()}` : undefined));
  }

  update(id: number, update: CategoryUpdate): Observable<Category> {
    return this.http.put<Category>(`${this.url}/${id}`, update);
  }
}

export function isId(id: number): boolean {
  return Number.isSafeInteger(id) && id > 0;
}
