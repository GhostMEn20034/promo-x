package com.promox.core.promotion.internal.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CreatePromotionRuleRequestValidator.class)
@Documented
public @interface ValidPromotionRuleCreationRequest {
    String message() default "Invalid promotion rule request";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
