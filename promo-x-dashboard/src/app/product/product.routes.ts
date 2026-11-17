import { Routes } from '@angular/router';
import { Role, roleGuard } from '../infrastructure/auth';
import { ProductBulkCreatePage } from './internal/bulk/product-bulk-create.page';
import { ProductDetailsPage } from './internal/details/product-details.page';
import { ProductListPage } from './internal/list/product-list.page';
import { ProductClient } from './internal/product.client';

/**
 * Lazy-loaded under a storefront, `/storefronts/:storefrontId/products`. The pages read `:storefrontId` from that
 * parent route. promo-x-core lets employees and auditors read products, employees create them and admins update
 * them.
 */
export const productRoutes: Routes = [
  {
    path: '',
    canActivate: [roleGuard(Role.Employee, Role.Auditor)],
    providers: [ProductClient],
    children: [
      { path: '', title: 'Products', component: ProductListPage },
      {
        path: 'bulk',
        title: 'Bulk import products',
        canActivate: [roleGuard(Role.Employee)],
        component: ProductBulkCreatePage,
      },
      { path: ':productId', title: 'Product', component: ProductDetailsPage },
    ],
  },
];
