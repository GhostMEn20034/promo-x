package com.promox.core.promotion.internal.web;

import com.promox.core.promotion.PromotionLookupApi;
import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.web.DefaultPromotionWebResponseDto;
import com.promox.core.promotion.internal.dto.web.DetailedPromotionWebResponseDto;
import com.promox.core.promotion.internal.exception.PromotionNotFoundException;
import com.promox.core.promotion.internal.service.PromotionCommandHandler;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/promotion")
public class PromotionController {
    private final PromotionWebMapper promotionWebMapper;
    private final PromotionCommandHandler promotionCommandHandler;
    private final PromotionLookupApi promotionLookupApi;

    public PromotionController(
        PromotionWebMapper promotionWebMapper,
        PromotionCommandHandler promotionCommandHandler,
        PromotionLookupApi promotionLookupApi
    ) {
        this.promotionWebMapper = promotionWebMapper;
        this.promotionCommandHandler = promotionCommandHandler;
        this.promotionLookupApi = promotionLookupApi;
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

    @PutMapping("/{promotionRuleId}")
    public DefaultPromotionWebResponseDto updatePromotionRule(
        @PathVariable Long promotionRuleId,
        @Valid @RequestBody UpdatePromotionRuleRequest updateRequest
    ) {
        var updatedPromotionRule = promotionCommandHandler.updatePromotionRule(promotionRuleId, updateRequest)
            .orElseThrow(() -> new PromotionNotFoundException(promotionRuleId));

        return promotionWebMapper.toWebResponse(updatedPromotionRule);
    }

    @GetMapping("/{promotionRuleId}")
    public DetailedPromotionWebResponseDto getPromotionRuleById(@PathVariable Long promotionRuleId) {
        var promotionRule = promotionLookupApi.findById(promotionRuleId)
            .orElseThrow(() -> new PromotionNotFoundException(promotionRuleId));

        return promotionWebMapper.toDetailedWebResponse(promotionRule);
    }
}
