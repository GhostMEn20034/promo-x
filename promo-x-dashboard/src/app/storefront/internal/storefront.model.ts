export interface Storefront {
  readonly id: number;
  readonly name: string;
  readonly website: string | null;
}

/** The body of `PUT /storefront/{id}`. It replaces both fields, so a null `website` removes it. */
export interface StorefrontUpdate {
  readonly name: string;
  readonly website: string | null;
}

export const STOREFRONT_NAME_MAX_LENGTH = 50;
export const STOREFRONT_WEBSITE_MAX_LENGTH = 255;
