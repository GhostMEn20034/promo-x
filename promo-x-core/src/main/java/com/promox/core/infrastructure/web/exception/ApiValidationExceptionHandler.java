package com.promox.core.infrastructure.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;


import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;



@RestControllerAdvice
public class ApiValidationExceptionHandler {

    /**
     * Handles validation errors for @Valid @RequestBody.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleRequestBodyValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = validationProblem();
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        List<ApiError> errors = new ArrayList<>();

        ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toRequestBodyError)
                .forEach(errors::add);

        ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(this::toRequestBodyError)
                .forEach(errors::add);

        problem.setProperty("errors", errors);

        return problem;
    }

    /**
     * Handles Bean Validation for controller method parameters.
     * <p>
     * <ul>
         * <li>@RequestParam  (query parameters)</li>
         * <li>@PathVariable  (URL path variables)</li>
         * <li>@RequestHeader (HTTP headers)</li>
         * <li>@CookieValue   (cookies) </li>
     * </ul>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {

        ProblemDetail problem = validationProblem();
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());

        List<ApiError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {

            MethodParameter parameter = result.getMethodParameter();

            ErrorSource source = resolveSource(parameter);
            String parameterName = parameter.getParameterName();

            result.getResolvableErrors().forEach(error ->
                    errors.add(new ApiError(
                            ErrorCategory.VALIDATION,
                            source,
                            parameterName,
                            ValidationErrorCodes.resolve(error),
                            error.getDefaultMessage()
                    ))
            );
        });

        problem.setProperty("errors", errors);

        return problem;
    }

    /**
     * Handles request parameter conversion failures.
     * These errors happen before Bean Validation.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = validationProblem();
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());

        ErrorSource source = resolveSource(ex.getParameter());

        String expectedType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName() : "Unknown";

        List<ApiError> errors = List.of(
                new ApiError(
                        ErrorCategory.VALIDATION,
                        source,
                        ex.getName(),
                        "TYPE_MISMATCH",
                        "Expected value of type '%s'."
                                .formatted(expectedType)
                )
        );
        
        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = validationProblem();
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());

        List<ApiError> errors = new ArrayList<>();

        Throwable cause = ex.getMostSpecificCause();

        if (cause instanceof InvalidFormatException invalidFormat) {

            String path = JacksonExceptionUtils.toJsonPath(invalidFormat);

            errors.add(new ApiError(
                    ErrorCategory.VALIDATION,
                    ErrorSource.REQUEST_BODY,
                    path,
                    "TYPE_MISMATCH",
                    "Invalid value type"
            ));
        } else {
            errors.add(new ApiError(
                    ErrorCategory.VALIDATION,
                    ErrorSource.REQUEST_BODY,
                    null,
                    "INVALID_JSON",
                    "Malformed JSON request."
            ));
        }

        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail validationProblem() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        // TODO: Replace hardcoded api example domain by some actual product domain
        problem.setType(URI.create("https://api.example.com/problems/validation-error"));
        problem.setTitle("Validation failed");
        problem.setDetail("One or more validation errors occurred.");

        // Top-level error category.
        problem.setProperty("code", "VALIDATION_ERROR");

        return problem;
    }

    private static ErrorSource resolveSource(MethodParameter parameter) {

        if (parameter.hasParameterAnnotation(RequestParam.class)) {
            return ErrorSource.QUERY_PARAMETER;
        }

        if (parameter.hasParameterAnnotation(PathVariable.class)) {
            return ErrorSource.PATH_VARIABLE;
        }

        if (parameter.hasParameterAnnotation(RequestHeader.class)) {
            return ErrorSource.HEADER;
        }

        if (parameter.hasParameterAnnotation(CookieValue.class)) {
            return ErrorSource.COOKIE;
        }

        if (parameter.hasParameterAnnotation(RequestBody.class)) {
            return ErrorSource.REQUEST_BODY;
        }

        return ErrorSource.UNKNOWN;
    }


    private ApiError toRequestBodyError(FieldError error) {
        return new ApiError(
                ErrorCategory.VALIDATION,
                ErrorSource.REQUEST_BODY,
                error.getField(),
                ValidationErrorCodes.resolve(error),
                error.getDefaultMessage()
        );
    }

    private ApiError toRequestBodyError(ObjectError error) {
        return new ApiError(
                ErrorCategory.VALIDATION,
                ErrorSource.REQUEST_BODY,
                null,
                ValidationErrorCodes.resolve(error),
                error.getDefaultMessage()
        );
    }
}
