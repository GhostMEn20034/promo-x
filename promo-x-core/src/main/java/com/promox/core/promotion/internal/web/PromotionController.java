package com.promox.core.promotion.internal.web;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.promotion.PromotionLookupApi;
import com.promox.core.promotion.TimestampRange;
import com.promox.core.promotion.internal.dto.*;
import com.promox.core.promotion.internal.dto.web.DefaultPromotionWebResponseDto;
import com.promox.core.promotion.internal.dto.web.DetailedPromotionWebResponseDto;
import com.promox.core.promotion.internal.exception.PromotionNotFoundException;
import com.promox.core.promotion.internal.exception.PromotionRuleUsageNotFoundException;
import com.promox.core.promotion.internal.service.PromotionCommandHandler;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/promotion")
@PreAuthorize("hasRole('" + Roles.PROMOTION_MANAGER + "')")
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
            promotionCommandHandler.create(storefrontId, creationRequest)
        );
    }

    @PutMapping("/{promotionRuleId}")
    public DefaultPromotionWebResponseDto updatePromotionRule(
        @PathVariable Long promotionRuleId,
        @Valid @RequestBody UpdatePromotionRuleRequest updateRequest
    ) {
        var updatedPromotionRule = promotionCommandHandler.updateById(promotionRuleId, updateRequest)
            .orElseThrow(() -> new PromotionNotFoundException(promotionRuleId));

        return promotionWebMapper.toWebResponse(updatedPromotionRule);
    }

    @PatchMapping("/{promotionRuleId}/usage-limits")
    public DefaultPromotionWebResponseDto updateUsageLimits(
            @PathVariable Long promotionRuleId,
            @Valid @RequestBody UpdateUsageLimitsRequest updateRequest
    ) {
        var updatedPromotionRule = promotionCommandHandler.updateUsageLimitsById(promotionRuleId, updateRequest)
                .orElseThrow(() -> new PromotionNotFoundException(promotionRuleId));

        return promotionWebMapper.toWebResponse(updatedPromotionRule);
    }

    @GetMapping("/{promotionRuleId}")
    @PreAuthorize("hasAnyRole('" + Roles.PROMOTION_MANAGER + "', '" + Roles.AUDITOR + "', '" + Roles.EMPLOYEE + "')")
    public DetailedPromotionWebResponseDto getPromotionRuleById(@PathVariable Long promotionRuleId) {
        var promotionRule = promotionLookupApi.findById(promotionRuleId)
            .orElseThrow(() -> new PromotionNotFoundException(promotionRuleId));

        return promotionWebMapper.toDetailedWebResponse(promotionRule);
    }

    @GetMapping("/")
    @PreAuthorize("hasAnyRole('" + Roles.PROMOTION_MANAGER + "', '" + Roles.AUDITOR + "', '" + Roles.EMPLOYEE + "')")
    public Page<PromotionSummaryDto> getPromotionRules(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        PromotionFilterRequest filter,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return promotionLookupApi.findAll(storefrontId, filter, pageable);
    }

    @DeleteMapping("/{promotionRuleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePromotionRule(@PathVariable Long promotionRuleId) {
        promotionCommandHandler.deleteById(promotionRuleId)
            .orElseThrow(() -> new PromotionNotFoundException(promotionRuleId));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('" + Roles.INTEGRATION_CLIENT + "', '" + Roles.PROMOTION_MANAGER + "')")
    public PromotionEvaluationResponse evaluatePromotionRules(
            @RequestHeader("X-Storefront-Id") Long storefrontId,
            @Valid @RequestBody PromotionEvaluationRequest evaluationRequest
    ) {
        return promotionCommandHandler.evaluate(storefrontId, evaluationRequest);
    }

    @PostMapping("/usage/{usageId}/confirm")
    @PreAuthorize("hasAnyRole('" + Roles.INTEGRATION_CLIENT + "', '" + Roles.PROMOTION_MANAGER + "')")
    public PromotionRuleUsageDto confirmPromotionRuleUsage(@PathVariable UUID usageId) {
        return promotionCommandHandler.confirmUsage(usageId)
                .orElseThrow(() -> new PromotionRuleUsageNotFoundException(usageId));
    }

    @PostMapping("/usage/{usageId}/cancel")
    @PreAuthorize("hasAnyRole('" + Roles.INTEGRATION_CLIENT + "', '" + Roles.PROMOTION_MANAGER + "')")
    public PromotionRuleUsageDto cancelPromotionRuleUsage(@PathVariable UUID usageId) {
        return promotionCommandHandler.cancelUsage(usageId)
                .orElseThrow(() -> new PromotionRuleUsageNotFoundException(usageId));
    }

    @GetMapping("/usage/stats")
    @PreAuthorize("hasAnyRole('" + Roles.AUDITOR + "', '" + Roles.PROMOTION_MANAGER + "', '" + Roles.ADMIN + "')")
    public PromotionRuleUsageStatsResponse getUsageStats(
            @RequestHeader("X-Storefront-Id") Long storefrontId,
            @Valid TimestampRange timestampRange
            ) {
        return promotionLookupApi.getUsageStats(storefrontId, timestampRange);
    }
}
