import { Routes } from '@angular/router';
import { Role, roleGuard } from '../infrastructure/auth';
import { PromotionRuleDetailsPage } from './internal/details/promotion-rule-details.page';
import { PromotionRuleCreatePage } from './internal/editor/promotion-rule-create.page';
import { PromotionRuleEditPage } from './internal/editor/promotion-rule-edit.page';
import { PromotionEvaluatePage } from './internal/evaluate/promotion-evaluate.page';
import { PromotionRuleListPage } from './internal/list/promotion-rule-list.page';
import { PromotionClient } from './internal/promotion.client';

/**
 * Lazy-loaded under a storefront, `/storefronts/:storefrontId/promotion-rules`. The pages read `:storefrontId`
 * from that parent route. promo-x-core lets promotion managers, auditors and employees read rules, and only
 * promotion managers create, change and delete them, and evaluate test carts.
 */
export const promotionRoutes: Routes = [
  {
    path: '',
    canActivate: [roleGuard(Role.PromotionManager, Role.Auditor, Role.Employee)],
    providers: [PromotionClient],
    children: [
      { path: '', title: 'Promotion rules', component: PromotionRuleListPage },
      {
        path: 'new',
        title: 'New promotion rule',
        canActivate: [roleGuard(Role.PromotionManager)],
        component: PromotionRuleCreatePage,
      },
      {
        path: 'evaluate',
        title: 'Test a cart',
        canActivate: [roleGuard(Role.PromotionManager)],
        component: PromotionEvaluatePage,
      },
      { path: ':ruleId', title: 'Promotion rule', component: PromotionRuleDetailsPage },
      {
        path: ':ruleId/edit',
        title: 'Edit promotion rule',
        canActivate: [roleGuard(Role.PromotionManager)],
        component: PromotionRuleEditPage,
      },
    ],
  },
];
