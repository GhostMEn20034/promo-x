package com.promox.core.storefront.internal.exception;

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
public class StorefrontExceptionHandler {

    @ExceptionHandler(StorefrontNotFoundException.class)
    public ProblemDetail handleStorefrontNotFound(
            StorefrontNotFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setType(URI.create("https://api.example.com/problems/storefront-not-found"));
        problem.setTitle("Storefront not found");
        problem.setDetail(ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors", ApiErrorMapper.from(ex.getErrors()));

        return problem;
    }
}
