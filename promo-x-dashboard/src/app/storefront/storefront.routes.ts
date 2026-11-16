import { Routes } from '@angular/router';
import { StorefrontDetailsPage } from './internal/details/storefront-details.page';
import { StorefrontListPage } from './internal/list/storefront-list.page';
import { StorefrontClient } from './internal/storefront.client';

/** Lazy-loaded by the app under `/storefronts`. Promo-x-core lets any authenticated caller read storefronts. */
export const storefrontRoutes: Routes = [
  {
    path: '',
    providers: [StorefrontClient],
    children: [
      { path: '', title: 'Storefronts', component: StorefrontListPage },
      { path: ':storefrontId', title: 'Storefront', component: StorefrontDetailsPage },
    ],
  },
];
