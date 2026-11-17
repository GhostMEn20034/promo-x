import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, Page } from '../../infrastructure/http';
import { Product, ProductInput, SavedProduct } from './product.model';

const STOREFRONT_HEADER = 'X-Storefront-Id';

/**
 * promo-x-core's product endpoints. Reads are resources that follow their signals; writes are one-off requests.
 *
 * The resource methods must be called in an injection context, such as a component's field initializer, and the
 * resource lives as long as that context.
 */
@Injectable()
export class ProductClient {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL).replace(/\/+$/, '')}/api/product`;

  /** One page of the storefront's products, newest first. `page` is 1-based. */
  page(
    storefrontId: Signal<number>,
    page: Signal<number>,
    size: number,
  ): HttpResourceRef<Page<Product> | undefined> {
    return httpResource<Page<Product>>(() =>
      isId(storefrontId())
        ? {
            url: `${this.url}/`,
            params: { page: page(), size },
            headers: { [STOREFRONT_HEADER]: String(storefrontId()) },
          }
        : undefined,
    );
  }

  /** The product with this ID. Nothing is requested while the ID isn't a positive integer. */
  byId(id: Signal<number>): HttpResourceRef<Product | undefined> {
    return httpResource<Product>(() => (isId(id()) ? `${this.url}/${id()}` : undefined));
  }

  create(storefrontId: number, product: ProductInput): Observable<SavedProduct> {
    return this.http.post<SavedProduct>(`${this.url}/`, product, {
      headers: { [STOREFRONT_HEADER]: String(storefrontId) },
    });
  }

  /** Adds all the products or none of them. */
  createMany(storefrontId: number, items: readonly ProductInput[]): Observable<void> {
    return this.http.post<void>(
      `${this.url}/bulk-insert`,
      { items },
      { headers: { [STOREFRONT_HEADER]: String(storefrontId) } },
    );
  }

  update(id: number, product: ProductInput): Observable<SavedProduct> {
    return this.http.put<SavedProduct>(`${this.url}/${id}`, product);
  }
}

export function isId(id: number): boolean {
  return Number.isSafeInteger(id) && id > 0;
}
