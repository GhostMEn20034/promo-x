import { Routes } from '@angular/router';
import { authRoutes } from './infrastructure/auth';

export const routes: Routes = [...authRoutes];
