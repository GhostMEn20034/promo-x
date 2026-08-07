package com.promox.core.category.internal.exception;

import com.promox.core.infrastructure.web.exception.ApiErrorMapper;
import com.promox.core.storefront.internal.exception.StorefrontNotFoundException;

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
public class CategoryExceptionHandler {

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleStorefrontNotFound(
            CategoryNotFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setType(URI.create("https://api.example.com/problems/category-not-found"));
        problem.setTitle("Category not found");
        problem.setDetail(ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors", ApiErrorMapper.from(ex.getErrors()));

        return problem;
    }
}
