package com.promox.core.product;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Set;

public record DefaultProductDto(
    @JsonUnwrapped ProductBaseDto base,
    Set<Long> categoryIds
) {}
