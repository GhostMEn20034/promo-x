package com.promox.core.product.internal.web;

import com.promox.core.product.ProductDto;
import com.promox.core.product.internal.dto.CreateSingleProductRequest;
import com.promox.core.product.internal.service.ProductCommandHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductCommandHandler productCommandHandler;

    public ProductController(ProductCommandHandler productCommandHandler) {
        this.productCommandHandler = productCommandHandler;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto createSingleProduct(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        @Valid @RequestBody CreateSingleProductRequest creationRequest
    ) {
        return productCommandHandler.createSingleProduct(storefrontId, creationRequest);
    }
}
