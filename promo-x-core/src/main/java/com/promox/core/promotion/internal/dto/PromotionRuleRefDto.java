package com.promox.core.promotion.internal.dto;


/**
 * A rule named in an audit, as the evaluation response names it.
 */
public record PromotionRuleRefDto(
    Long ruleId,
    String ruleName
) {}
