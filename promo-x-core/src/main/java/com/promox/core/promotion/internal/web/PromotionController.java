package com.promox.core.promotion.internal.web;

import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.DefaultPromotionDto;
import com.promox.core.promotion.internal.service.PromotionCommandHandler;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/promotion")
public class PromotionController {
    private final PromotionCommandHandler promotionCommandHandler;

    public PromotionController(PromotionCommandHandler promotionCommandHandler) {
        this.promotionCommandHandler = promotionCommandHandler;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public DefaultPromotionDto createPromotionRule(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        @Valid @RequestBody CreatePromotionRuleRequest creationRequest
    ) {
       return promotionCommandHandler.createPromotionRule(storefrontId, creationRequest);
    }
}
