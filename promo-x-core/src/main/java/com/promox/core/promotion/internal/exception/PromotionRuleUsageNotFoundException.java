package com.promox.core.promotion.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;
import java.util.UUID;

public final class PromotionRuleUsageNotFoundException extends BusinessException {

    public PromotionRuleUsageNotFoundException(UUID usageId) {
        super(
            "Promotion Rule Usage with id '%s' was not found.".formatted(usageId),
            "PROMOTION_RULE_USAGE_NOT_FOUND",
            List.of(
                new BusinessError(
                    null,
                    "PROMOTION_RULE_USAGE_NOT_FOUND",
                    "Promotion Rule Usage does not exist.",
                    null
                )
            )
        );
    }
}
