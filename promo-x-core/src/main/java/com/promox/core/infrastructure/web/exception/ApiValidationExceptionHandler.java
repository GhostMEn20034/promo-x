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
import com.promox.core.infrastructure.exception.InvalidValueException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.ValueInstantiationException;


import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



@RestControllerAdvice
public class ApiValidationExceptionHandler {

    /**
     * Handles validation errors of objects bound from the request: {@code @Valid @RequestBody} and
     * {@code @Valid} objects bound from query parameters, such as a date range.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBoundObjectValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = validationProblem();
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("timestamp", Instant.now());
        List<ApiError> errors = new ArrayList<>();
        ErrorSource source = resolveBoundObjectSource(ex.getParameter());

        ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> toBoundObjectError(error, source))
                .forEach(errors::add);

        ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> toBoundObjectError(error, source))
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
                            error.getDefaultMessage(),
                            null
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

        List<ApiError> errors = List.of(
                new ApiError(
                        ErrorCategory.VALIDATION,
                        source,
                        ex.getName(),
                        "TYPE_MISMATCH",
                        "Invalid value type",
                        null
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

        problem.setProperty("errors", List.of(toUnreadableBodyError(ex)));
        return problem;
    }

    /**
     * Explains why the body couldn't be read, from the most specific cause Jackson gives:
     * <ul>
     *     <li>a value rejected by the object being built ({@link InvalidValueException}), with its own code;</li>
     *     <li>an unknown or missing {@code type} of a polymorphic object, such as a condition or an action;</li>
     *     <li>a value of the wrong type, or not one of an enum's values;</li>
     *     <li>any other object that couldn't be built from the given values;</li>
     *     <li>otherwise, JSON that isn't well-formed.</li>
     * </ul>
     */
    private static ApiError toUnreadableBodyError(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        JacksonException jacksonException = findCause(ex, JacksonException.class);
        String path = jacksonException != null ? JacksonExceptionUtils.toJsonPath(jacksonException) : null;

        if (cause instanceof InvalidValueException invalidValue) {
            return bodyError(path, invalidValue.getErrorCode(), invalidValue.getMessage(), null);
        }

        if (cause instanceof InvalidTypeIdException invalidTypeId) {
            Map<String, Object> context = knownTypeIds(invalidTypeId)
                    .<Map<String, Object>>map(knownTypes -> Map.of("allowedTypes", knownTypes))
                    .orElse(null);

            return invalidTypeId.getTypeId() == null
                    ? bodyError(path, "MISSING_TYPE", "The 'type' property is required.", context)
                    : bodyError(path, "UNKNOWN_TYPE", "Unknown type '%s'.".formatted(invalidTypeId.getTypeId()), context);
        }

        if (cause instanceof InvalidFormatException invalidFormat) {
            Class<?> targetType = invalidFormat.getTargetType();

            if (targetType != null && targetType.isEnum()) {
                List<String> allowedValues = Arrays.stream(targetType.getEnumConstants())
                        .map(constant -> ((Enum<?>) constant).name())
                        .toList();
                return bodyError(
                        path,
                        "TYPE_MISMATCH",
                        "Invalid value '%s'.".formatted(invalidFormat.getValue()),
                        Map.of("allowedValues", allowedValues)
                );
            }
            return bodyError(path, "TYPE_MISMATCH", "Invalid value type", null);
        }

        // Thrown while building an object for any other reason; its message may describe internals, so it isn't shown.
        if (findCause(ex, ValueInstantiationException.class) != null) {
            return bodyError(path, "INVALID_VALUE", "One or more values of this object are invalid.", null);
        }

        return bodyError(null, "INVALID_JSON", "Malformed JSON request.", null);
    }

    private static ApiError bodyError(String path, String code, String message, Map<String, Object> context) {
        return new ApiError(ErrorCategory.VALIDATION, ErrorSource.REQUEST_BODY, path, code, message, context);
    }

    private static final Pattern KNOWN_TYPE_IDS = Pattern.compile("known type ids = \\[([^\\]]*)]");

    /** The type IDs Jackson lists as valid in the exception's message, if it lists them. */
    private static Optional<List<String>> knownTypeIds(InvalidTypeIdException exception) {
        Matcher matcher = KNOWN_TYPE_IDS.matcher(String.valueOf(exception.getOriginalMessage()));
        if (!matcher.find() || matcher.group(1).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Arrays.stream(matcher.group(1).split(","))
                .map(String::trim)
                .toList());
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
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


    /**
     * A {@code @RequestBody} object comes from the body; any other bound object (no annotation, or
     * {@code @ModelAttribute}) is bound from the query parameters.
     */
    private static ErrorSource resolveBoundObjectSource(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestBody.class)
                ? ErrorSource.REQUEST_BODY
                : ErrorSource.QUERY_PARAMETER;
    }

    private ApiError toBoundObjectError(FieldError error, ErrorSource source) {
        return new ApiError(
                ErrorCategory.VALIDATION,
                source,
                error.getField(),
                ValidationErrorCodes.resolve(error),
                error.getDefaultMessage(),
                null
        );
    }

    private ApiError toBoundObjectError(ObjectError error, ErrorSource source) {
        return new ApiError(
                ErrorCategory.VALIDATION,
                source,
                null,
                ValidationErrorCodes.resolve(error),
                error.getDefaultMessage(),
                null
        );
    }
}
