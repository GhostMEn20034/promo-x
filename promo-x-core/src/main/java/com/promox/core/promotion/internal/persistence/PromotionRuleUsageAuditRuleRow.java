package com.promox.core.promotion.internal.persistence;

import java.util.UUID;


/**
 * A rule an audited usage matched or applied, with the rule's current name.
 */
public record PromotionRuleUsageAuditRuleRow(
    UUID usageId,
    Long ruleId,
    String ruleName
) {}
