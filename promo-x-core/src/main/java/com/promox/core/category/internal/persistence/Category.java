package com.promox.core.category.internal.persistence;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "category")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nullable
    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    // (Read-only mapping to prevent JPA column collisions)
    @Nullable
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "parent_id", insertable = false, updatable = false)
    private Long parentId;

    @Nullable
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    public void setParent(@Nullable Category parent) {
        this.parent = parent;
        this.parentId = (parent != null) ? parent.getId() : null;
    }

    /**
     * Always returns the live ID if parent is attached,
     * fallback to raw DB parentId field if proxy/entity wasn't loaded.
     */
    @Nullable
    public Long getParentId() {
        if (this.parent != null) {
            return this.parent.getId();
        }
        return this.parentId;
    }
}
