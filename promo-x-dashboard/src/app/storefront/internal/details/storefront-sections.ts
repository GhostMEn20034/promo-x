import { Role } from '../../../infrastructure/auth';

export interface StorefrontSection {
  /** The child route under `/storefronts/:storefrontId`. */
  readonly path: string;
  readonly label: string;
  readonly description: string;
  readonly icon: 'categories' | 'products' | 'promotions' | 'evaluations';
  /** Not connected to promo-x-core yet: the page is a placeholder. */
  readonly comingSoon: boolean;
  /** Only users with one of these roles see the section. Every user sees one without. */
  readonly roles?: readonly Role[];
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
  {
    path: 'evaluations',
    label: 'Evaluations',
    description: 'Every cart evaluation that applied a rule: its rules, totals and usage status.',
    icon: 'evaluations',
    comingSoon: false,
    // As the promotion module's route guard: promo-x-core lets these roles read the usage audit
    roles: [Role.PromotionManager, Role.Auditor, Role.Admin],
  },
];
