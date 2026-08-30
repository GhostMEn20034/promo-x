package com.promox.core.promotion.internal.web;

import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.web.DefaultPromotionWebResponseDto;
import com.promox.core.promotion.internal.service.PromotionCommandHandler;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/promotion")
public class PromotionController {
    private final PromotionWebMapper promotionWebMapper;
    private final PromotionCommandHandler promotionCommandHandler;

    public PromotionController(PromotionWebMapper promotionWebMapper, PromotionCommandHandler promotionCommandHandler) {
        this.promotionWebMapper = promotionWebMapper;
        this.promotionCommandHandler = promotionCommandHandler;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public DefaultPromotionWebResponseDto createPromotionRule(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        @Valid @RequestBody CreatePromotionRuleRequest creationRequest
    ) {
       return promotionWebMapper.toWebResponse(
           promotionCommandHandler.createPromotionRule(storefrontId, creationRequest)
       );
    }
}
