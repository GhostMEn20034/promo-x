package com.promox.core.promotion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record TimestampRange(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
) {
    /**
     * Checked by Bean Validation as the {@code validRange} property. A missing bound is reported by {@code @NotNull}.
     */
    @AssertTrue(message = "from must not be after to")
    public boolean isValidRange() {
        return from == null || to == null || !from.isAfter(to);
    }
}
