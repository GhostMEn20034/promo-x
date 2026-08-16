package com.promox.core.category.internal.persistence;

import lombok.*;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;


/**
 * <b>Database Index Notes:</b>
 * The unique partial index on {@code (storefront_id, external_id)}
 * is defined in DB migration script:
 * {@code src/main/resources/db/migration/V0.1__category_table.sql}
 */
@Entity
@Table(
    name = "category",
    indexes = {
        @Index(
            name = "idx_category_storefront_external_id",
            columnList = "storefront_id, external_id",
            unique = true
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "external_id")
    private @Nullable String externalId;

    @Column(nullable = false)
    private String name;

    // (Read-only mapping to prevent JPA column collisions)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "parent_id", insertable = false, updatable = false)
    private @Nullable Long parentId;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private @Nullable Category parent;

    public void setParent(@Nullable Category parent) {
        this.parent = parent;
        this.parentId = (parent != null) ? parent.getId() : null;
    }

    /**
     * Always returns the live ID if parent is attached,
     * fallback to raw DB parentId field if proxy/entity wasn't loaded.
     */

    public @Nullable Long getParentId() {
        if (this.parent != null) {
            return this.parent.getId();
        }
        return this.parentId;
    }

    @Column(name = "storefront_id", nullable = false)
    private Long storefrontId;
}
