/**
 * The rule list's filters, sort and page, as kept in the URL's query parameters, and their translation to
 * `GET /api/promotion/`'s parameters.
 */

/** The sorts promo-x-core allows (`createdAt`, `updatedAt`, `priority`), with a direction. */
export const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'priority,desc', label: 'Evaluation order (highest priority first)' },
  { value: 'priority,asc', label: 'Lowest priority first' },
] as const;

export type SortValue = (typeof SORT_OPTIONS)[number]['value'];

/** promo-x-core's own default, so it's left out of the URL. */
export const DEFAULT_SORT: SortValue = 'createdAt,desc';

export interface PromotionListQuery {
  /** 1-based. */
  readonly page: number;
  readonly sort: SortValue;
  /** Null: active and inactive rules. */
  readonly isActive: boolean | null;
  /** Null: short-circuit and other rules. */
  readonly isShortCircuit: boolean | null;
  /** A UTC day, `YYYY-MM-DD`, included. */
  readonly createdFrom: string | null;
  /** A UTC day, `YYYY-MM-DD`, included. */
  readonly createdTo: string | null;
}

/** The URL's query parameters, as the router gives them: absent ones are undefined. */
export interface PromotionListParams {
  readonly page?: unknown;
  readonly sort?: unknown;
  readonly active?: unknown;
  readonly shortCircuit?: unknown;
  readonly createdFrom?: unknown;
  readonly createdTo?: unknown;
}

/** Reads the query from the URL. A value that isn't valid falls back to the default instead of failing. */
export function readQuery(params: PromotionListParams): PromotionListQuery {
  const page = Number(params.page);
  return {
    page: Number.isSafeInteger(page) && page >= 1 ? page : 1,
    sort: SORT_OPTIONS.some((option) => option.value === params.sort)
      ? (params.sort as SortValue)
      : DEFAULT_SORT,
    isActive: readBoolean(params.active),
    isShortCircuit: readBoolean(params.shortCircuit),
    createdFrom: readDay(params.createdFrom),
    createdTo: readDay(params.createdTo),
  };
}

/** The query parameters for a changed query, in `router.navigate`'s form: null removes a parameter. */
export function toUrlParams(query: PromotionListQuery): Record<string, string | number | null> {
  return {
    page: query.page === 1 ? null : query.page,
    sort: query.sort === DEFAULT_SORT ? null : query.sort,
    active: query.isActive === null ? null : String(query.isActive),
    shortCircuit: query.isShortCircuit === null ? null : String(query.isShortCircuit),
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
  };
}

/**
 * The parameters of `GET /api/promotion/`. The days become timestamps: `createdFrom` at the start of its UTC day
 * and `createdTo` at the end of its, so both days are included.
 */
export function toApiParams(
  query: PromotionListQuery,
  size: number,
): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {
    page: query.page,
    size,
    sort: query.sort,
  };
  if (query.isActive !== null) {
    params['isActive'] = query.isActive;
  }
  if (query.isShortCircuit !== null) {
    params['isShortCircuit'] = query.isShortCircuit;
  }
  if (query.createdFrom) {
    params['createdFrom'] = `${query.createdFrom}T00:00:00Z`;
  }
  if (query.createdTo) {
    params['createdTo'] = `${query.createdTo}T23:59:59.999999Z`;
  }
  return params;
}

export function hasFilters(query: PromotionListQuery): boolean {
  return (
    query.isActive !== null ||
    query.isShortCircuit !== null ||
    query.createdFrom !== null ||
    query.createdTo !== null
  );
}

/** Both days are set and the range is backwards, so no rule can match. */
export function hasInvalidRange(query: PromotionListQuery): boolean {
  return (
    query.createdFrom !== null && query.createdTo !== null && query.createdFrom > query.createdTo
  );
}

function readBoolean(value: unknown): boolean | null {
  return value === 'true' ? true : value === 'false' ? false : null;
}

function readDay(value: unknown): string | null {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  // Rejects days that don't exist, such as 2026-02-30
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value) ? value : null;
}
