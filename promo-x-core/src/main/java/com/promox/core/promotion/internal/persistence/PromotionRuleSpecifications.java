package com.promox.core.promotion.internal.persistence;

import com.promox.core.promotion.internal.dto.PromotionFilterRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PromotionRuleSpecifications {
    private PromotionRuleSpecifications() {}

    public static Specification<PromotionRule> withFilter(Long storefrontId, PromotionFilterRequest filter) {
        return (root, _, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory storefront filter
            predicates.add(criteriaBuilder.equal(root.get("storefrontId"), storefrontId));

            if (filter != null) {
                if (filter.createdFrom() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
                }
                if (filter.createdTo() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
                }
                if (filter.isShortCircuit() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("isShortCircuit"), filter.isShortCircuit()));
                }
                if (filter.isActive() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("isActive"), filter.isActive()));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
