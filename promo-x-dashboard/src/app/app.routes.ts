import { Routes } from '@angular/router';
import { authRoutes } from './infrastructure/auth';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'storefronts' },
  {
    path: 'storefronts',
    loadChildren: () => import('./storefront').then((m) => m.storefrontRoutes),
  },
  ...authRoutes,
  { path: '**', redirectTo: 'storefronts' },
];
