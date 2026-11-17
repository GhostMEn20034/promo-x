/** A category's parent, as promo-x-core embeds it in the category. */
export interface ParentCategory {
  readonly id: number;
  readonly externalId: string | null;
  readonly name: string;
  /** The parent's own parent, if any. */
  readonly parentId: number | null;
  readonly storefrontId: number;
}

export interface Category {
  readonly id: number;
  /** The calling system's ID of the category, unique within the storefront. Rules refer to categories by it. */
  readonly externalId: string | null;
  readonly name: string;
  /** Null for a top-level category. */
  readonly parent: ParentCategory | null;
  readonly storefrontId: number;
}

/** What's needed to show a category that was chosen, such as a parent or a product's category. */
export type CategoryRef = Pick<Category, 'id' | 'name' | 'externalId'>;

/** The body of `PUT /api/category/{id}`. It replaces every field, so a null `parentId` makes it top-level. */
export interface CategoryUpdate {
  readonly name: string;
  readonly externalId: string;
  readonly parentId: number | null;
}

export const CATEGORY_NAME_MIN_LENGTH = 2;
export const CATEGORY_NAME_MAX_LENGTH = 50;
export const CATEGORY_EXTERNAL_ID_MAX_LENGTH = 512;
