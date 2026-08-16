package com.promox.core.infrastructure.web.exception;

import com.promox.core.infrastructure.exception.BusinessError;

import java.util.List;

public final class ApiErrorMapper {
    private ApiErrorMapper() {}

    public static ApiError from(BusinessError error) {
        return new ApiError(
                ErrorCategory.BUSINESS,
                ErrorSource.DOMAIN,
                error.path(),
                error.code(),
                error.message(),
                error.errorContext()
        );
    }

    public static List<ApiError> from(List<BusinessError> errors) {
        return errors.stream()
                .map(ApiErrorMapper::from)
                .toList();
    }
}
