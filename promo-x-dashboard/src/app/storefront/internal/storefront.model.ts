export interface Storefront {
  readonly id: number;
  readonly name: string;
  readonly website: string | null;
}

/**
 * The body of `POST /api/storefront/` and `PUT /api/storefront/{id}`. An update replaces both fields, so a null
 * `website` removes it.
 */
export interface StorefrontInput {
  readonly name: string;
  readonly website: string | null;
}

export const STOREFRONT_NAME_MAX_LENGTH = 50;
export const STOREFRONT_WEBSITE_MAX_LENGTH = 255;
