package com.promox.core.infrastructure.web.exception;

import com.promox.core.infrastructure.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;


@RestControllerAdvice
public class ApiBusinessExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        // TODO: Replace hardcoded api example domain by some actual product domain
        problem.setType(URI.create("https://api.example.com/problems/business-rule-violation"));
        problem.setTitle("Business rule violation");
        problem.setDetail(ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors",  ApiErrorMapper.from(ex.getErrors()));

        return problem;
    }
}
