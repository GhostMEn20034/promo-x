import { CategoryRef } from '../../category';

/** A product with its categories, as the list and details endpoints return it. */
export interface Product {
  readonly id: number;
  /** The calling system's ID of the product, unique within the storefront. Carts and rules refer to it. */
  readonly externalId: string;
  readonly sku: string;
  readonly name: string;
  readonly storefrontId: number;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly categories: readonly CategoryRef[];
}

/** What's needed to show a product that was chosen, such as one a promotion rule refers to. */
export type ProductRef = Pick<Product, 'id' | 'externalId' | 'sku' | 'name'>;

/** A product as create and update return it: with `categoryIds` instead of `categories`. */
export interface SavedProduct extends Omit<Product, 'categories'> {
  readonly categoryIds: readonly number[];
}

/** The body of `POST /api/product/`, `PUT /api/product/{id}` (which replaces every field) and of a bulk item. */
export interface ProductInput {
  readonly externalId: string;
  readonly sku: string;
  readonly name: string;
  readonly categoryIds: readonly number[];
}

export const PRODUCT_FIELD_MAX_LENGTH = 255;
export const PRODUCT_BULK_MAX_ITEMS = 1000;

/** The saved product with the chosen categories, in the order promo-x-core returned their IDs. */
export function withCategories(saved: SavedProduct, chosen: readonly CategoryRef[]): Product {
  const { categoryIds, ...product } = saved;
  const byId = new Map(chosen.map((category) => [category.id, category]));
  return {
    ...product,
    categories: categoryIds.flatMap((id) => {
      const category = byId.get(id);
      return category ? [category] : [];
    }),
  };
}
