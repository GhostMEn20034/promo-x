package com.promox.core.promotion.internal.web;

import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.service.condition.ConditionNode;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/promotion")
public class PromotionController {

    @PostMapping("/")
    public ConditionNode createPromotionRule(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        @Valid @RequestBody CreatePromotionRuleRequest creationRequest
    ) {
        return creationRequest.conditionTree();
    }
}
