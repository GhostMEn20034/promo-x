import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../../infrastructure/http';
import { Storefront, StorefrontUpdate } from './storefront.model';

/**
 * promo-x-core's storefront endpoints. Reads are resources that follow their signals; writes are one-off
 * requests.
 *
 * The resource methods must be called in an injection context, such as a component's field initializer, and the
 * resource lives as long as that context.
 */
@Injectable()
export class StorefrontClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL).replace(/\/+$/, '')}/api/storefront`;

  /** One page of storefronts, sorted by ID. `page` is 1-based. */
  page(page: Signal<number>, size: number): HttpResourceRef<Page<Storefront> | undefined> {
    return httpResource<Page<Storefront>>(() => ({
      url: `${this.url}/`,
      params: { page: page(), size },
    }));
  }

  /** The storefront with this ID. Nothing is requested while the ID isn't a positive integer. */
  byId(id: Signal<number>): HttpResourceRef<Storefront | undefined> {
    return httpResource<Storefront>(() =>
      isStorefrontId(id()) ? `${this.url}/${id()}` : undefined,
    );
  }

  update(id: number, update: StorefrontUpdate): Observable<Storefront> {
    return this.http.put<Storefront>(`${this.url}/${id}`, update);
  }
}

export function isStorefrontId(id: number): boolean {
  return Number.isSafeInteger(id) && id > 0;
}
