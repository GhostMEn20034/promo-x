export interface StorefrontSection {
  /** The child route under `/storefronts/:storefrontId`. */
  readonly path: string;
  readonly label: string;
  readonly description: string;
  readonly icon: 'categories' | 'products' | 'promotions';
  /** Not connected to promo-x-core yet: the page is a placeholder. */
  readonly comingSoon: boolean;
}

/** A storefront's sections, shown as tabs and as the menu on its overview. */
export const STOREFRONT_SECTIONS: readonly StorefrontSection[] = [
  {
    path: 'categories',
    label: 'Categories',
    description: "The storefront's category tree, as synced from its catalog.",
    icon: 'categories',
    comingSoon: false,
  },
  {
    path: 'products',
    label: 'Products',
    description: 'Products with their SKUs and categories. Add them one by one or import a CSV.',
    icon: 'products',
    comingSoon: false,
  },
  {
    path: 'promotion-rules',
    label: 'Promotion rules',
    description: 'The rules evaluated against carts, by status, priority and short-circuit.',
    icon: 'promotions',
    comingSoon: false,
  },
];
