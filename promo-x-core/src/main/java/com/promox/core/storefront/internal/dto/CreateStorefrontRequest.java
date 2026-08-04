package com.promox.core.storefront.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CreateStorefrontRequest(
        @NotBlank
        @Size(max = 50)
        String name,
        @Size(min = 1, max = 255)
        String website
) {}
