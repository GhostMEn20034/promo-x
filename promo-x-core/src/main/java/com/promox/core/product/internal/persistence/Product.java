package com.promox.core.product.internal.persistence;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Getter;
import lombok.Setter;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(
    name = "product",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uniq_product_storefront_external_id",
            columnNames = {"storefront_id", "external_id"}
        )
    }
)
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "external_id")
    private @Nullable String externalId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(
            name = "product_category",
            joinColumns = @JoinColumn(name = "product_id")
    )
    @Column(name = "category_id", nullable = false)
    private Set<Long> categoryIds = new HashSet<>();

    @Column(name = "storefront_id", nullable = false)
    private Long storefrontId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
