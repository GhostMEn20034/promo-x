package com.promox.core.promotion.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;

public final class PromotionNotFoundException extends BusinessException {

    public PromotionNotFoundException(Long promotionRuleId) {
        super(
            "Promotion Rule with id '%d' was not found.".formatted(promotionRuleId),
            "PROMOTION_NOT_FOUND",
            List.of(
                new BusinessError(
                    null,
                    "PROMOTION_NOT_FOUND",
                    "Promotion Rule does not exist.",
                    null
                )
            )
        );
    }
}
