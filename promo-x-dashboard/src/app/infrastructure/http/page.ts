/**
 * A page of results as promo-x-core returns it. `number` is 0-based, while the `page` query parameter is 1-based.
 */
export interface Page<T> {
  readonly content: readonly T[];
  readonly number: number;
  readonly size: number;
  readonly numberOfElements: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}
