package com.promox.core.promotion.internal.dto.validators;

import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CreatePromotionRuleRequestValidator implements ConstraintValidator<ValidPromotionRuleCreationRequest, CreatePromotionRuleRequest> {

    @Override
    public boolean isValid(CreatePromotionRuleRequest value, ConstraintValidatorContext context) {
        if (!Boolean.TRUE.equals(value.hasUsageLimit())) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (value.usageLimit() == null || value.usageLimit() < 1) {
            context.buildConstraintViolationWithTemplate("usageLimit must be at least 1 when usage limit is enabled")
                .addPropertyNode("usageLimit")
                .addConstraintViolation();
            valid = false;
        }

        if (value.remainingUses() == null || value.remainingUses() < 1) {
            context.buildConstraintViolationWithTemplate("remainingUses must be at least 1 when usage limit is enabled")
                .addPropertyNode("remainingUses")
                .addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}
