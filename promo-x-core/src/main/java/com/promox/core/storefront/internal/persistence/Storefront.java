package com.promox.core.storefront.internal.persistence;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "storefront")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Storefront {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private @Nullable String website;
}
