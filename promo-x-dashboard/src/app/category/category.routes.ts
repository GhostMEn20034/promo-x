import { Routes } from '@angular/router';
import { Role, roleGuard } from '../infrastructure/auth';
import { CategoryClient } from './internal/category.client';
import { CategoryDetailsPage } from './internal/details/category-details.page';
import { CategoryListPage } from './internal/list/category-list.page';

/**
 * Lazy-loaded under a storefront, `/storefronts/:storefrontId/categories`. The pages read `:storefrontId` from
 * that parent route. promo-x-core lets employees and auditors read categories.
 */
export const categoryRoutes: Routes = [
  {
    path: '',
    canActivate: [roleGuard(Role.Employee, Role.Auditor)],
    providers: [CategoryClient],
    children: [
      { path: '', title: 'Categories', component: CategoryListPage },
      { path: ':categoryId', title: 'Category', component: CategoryDetailsPage },
    ],
  },
];
