import { Routes } from '@angular/router';

export const FORBIDDEN_PATH = 'forbidden';

export const authRoutes: Routes = [
  {
    path: FORBIDDEN_PATH,
    title: 'Access denied',
    loadComponent: () => import('./internal/forbidden/forbidden.page').then((m) => m.ForbiddenPage),
  },
];
