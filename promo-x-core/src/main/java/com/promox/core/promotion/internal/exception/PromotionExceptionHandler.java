package com.promox.core.promotion.internal.exception;

import com.promox.core.infrastructure.web.exception.ApiErrorMapper;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;


@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PromotionExceptionHandler {

    @ExceptionHandler(PromotionNotFoundException.class)
    public ProblemDetail handleStorefrontNotFound(
        PromotionNotFoundException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setType(URI.create("https://api.example.com/problems/promotion-not-found"));
        problem.setTitle("Promotion not found");
        problem.setDetail(ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors", ApiErrorMapper.from(ex.getErrors()));

        return problem;
    }

    @ExceptionHandler(PromotionRuleUsageNotFoundException.class)
    public ProblemDetail handlePromotionRuleUsageNotFound(
        PromotionRuleUsageNotFoundException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setType(URI.create("https://api.example.com/problems/promotion-rule-usage-not-found"));
        problem.setTitle("Promotion rule usage not found");
        problem.setDetail(ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors", ApiErrorMapper.from(ex.getErrors()));

        return problem;
    }
}
