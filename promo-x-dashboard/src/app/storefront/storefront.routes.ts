import { Routes } from '@angular/router';
import { StorefrontDetailsPage } from './internal/details/storefront-details.page';
import { StorefrontListPage } from './internal/list/storefront-list.page';
import { StorefrontOverviewPage } from './internal/overview/storefront-overview.page';
import { StorefrontClient } from './internal/storefront.client';

/** Lazy-loaded by the app under `/storefronts`. Promo-x-core lets any authenticated caller read storefronts. */
export const storefrontRoutes: Routes = [
  {
    path: '',
    providers: [StorefrontClient],
    children: [
      { path: '', title: 'Storefronts', component: StorefrontListPage },
      {
        path: ':storefrontId',
        component: StorefrontDetailsPage,
        children: [
          { path: '', title: 'Storefront', component: StorefrontOverviewPage },
          {
            path: 'categories',
            loadChildren: () => import('../category').then((m) => m.categoryRoutes),
          },
          {
            path: 'products',
            loadChildren: () => import('../product').then((m) => m.productRoutes),
          },
          {
            path: 'promotion-rules',
            loadChildren: () => import('../promotion').then((m) => m.promotionRoutes),
          },
        ],
      },
    ],
  },
];
