package com.promox.core.promotion;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Set;

@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
public class PromotionDeleteIntegrationTest extends IntegrationTestBase {
    private static final String DELETE_URL_PATH = "/promotion/{promotionRuleId}";

    @Autowired
    private PromotionRepository promotionRepository;

    @Nested
    @DisplayName("Happy Paths")
    class HappyPaths {

        @Test
        @DisplayName("Should return 204 NO CONTENT and delete promotion rule from database when entity exists")
        void deletePromotionRule_Success() throws Exception {
            PromotionRule rule = new PromotionRule();
            rule.setStorefrontId(100L);
            rule.setName("Temporary Promotion");
            rule.setPriority(1);
            rule.setShortCircuit(false);
            rule.setActive(true);
            rule.setConditionTree(new SingleProductConditionNode("PROD-001", Operator.EQUALS));
            rule.setAction(new OrderDiscountAction(BigDecimal.TEN));
            rule.setProductExternalIds(Set.of("PROD-001"));

            PromotionRule savedRule = promotionRepository.saveAndFlush(rule);

            mockMvc.perform(delete(DELETE_URL_PATH, savedRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

            assertThat(promotionRepository.findById(savedRule.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Error Paths")
    class ErrorPaths {

        @Test
        @DisplayName("Should return 404 NOT FOUND when promotion rule ID does not exist")
        void deletePromotionRule_NotFound() throws Exception {
            Long nonExistentId = 99999L;

            mockMvc.perform(delete(DELETE_URL_PATH, nonExistentId)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 BAD REQUEST when path variable ID is malformed")
        void deletePromotionRule_InvalidIdFormat() throws Exception {
            mockMvc.perform(delete("/promotion/{promotionRuleId}", "invalid-id-format")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @WithMockJwt(roles = {Roles.ADMIN, Roles.EMPLOYEE, Roles.AUDITOR, Roles.INTEGRATION_CLIENT})
        @DisplayName("Should return 403 FORBIDDEN and keep the promotion rule when the user is not a promotion manager")
        void deletePromotionRule_userIsNotPromotionManager_returnsForbidden() throws Exception {
            PromotionRule rule = new PromotionRule();
            rule.setStorefrontId(100L);
            rule.setName("Protected Promotion");
            rule.setPriority(1);
            rule.setShortCircuit(false);
            rule.setActive(true);
            rule.setConditionTree(new SingleProductConditionNode("PROD-001", Operator.EQUALS));
            rule.setAction(new OrderDiscountAction(BigDecimal.TEN));
            rule.setProductExternalIds(Set.of("PROD-001"));

            PromotionRule savedRule = promotionRepository.saveAndFlush(rule);

            mockMvc.perform(delete(DELETE_URL_PATH, savedRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

            assertThat(promotionRepository.findById(savedRule.getId())).isPresent();
        }
    }
}
