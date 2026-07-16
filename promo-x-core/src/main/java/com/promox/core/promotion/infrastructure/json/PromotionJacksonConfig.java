package com.promox.core.promotion.infrastructure.json;

import com.promox.core.promotion.internal.condition.*;
import com.promox.core.promotion.internal.action.*;

import org.springframework.context.annotation.Bean;
import tools.jackson.databind.jsontype.NamedType;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;


@Configuration
class PromotionJacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer conditionNodeSubtypeCustomizer() {
        return builder -> builder.registerSubtypes(
                new NamedType(CompositeConditionNode.class, CompositeConditionNode.TYPE_NAME),
                new NamedType(PromoCodeConditionNode.class, PromoCodeConditionNode.TYPE_NAME),
                new NamedType(SubtotalConditionNode.class, SubtotalConditionNode.TYPE_NAME),
                new NamedType(
                        CustomerAuthenticationStateConditionNode.class,
                        CustomerAuthenticationStateConditionNode.TYPE_NAME
                ),

                // SKU Matches
                new NamedType(SingleProductSkuConditionNode.class, SingleProductSkuConditionNode.TYPE_NAME),
                new NamedType(ProductSkuAnyMatchConditionNode.class, ProductSkuAnyMatchConditionNode.TYPE_NAME),

                // Product ID Matches
                new NamedType(SingleProductIdConditionNode.class, SingleProductIdConditionNode.TYPE_NAME),
                new NamedType(ProductIdAnyMatchConditionNode.class, ProductIdAnyMatchConditionNode.TYPE_NAME),

                // Category Matches
                new NamedType(SingleProductCategoryConditionNode.class, SingleProductCategoryConditionNode.TYPE_NAME),
                new NamedType(ProductCategoryAnyMatchConditionNode.class, ProductCategoryAnyMatchConditionNode.TYPE_NAME),

                // Collection Matches
                new NamedType(SingleProductCollectionConditionNode.class, SingleProductCollectionConditionNode.TYPE_NAME),
                new NamedType(ProductCollectionAnyMatchConditionNode.class, ProductCollectionAnyMatchConditionNode.TYPE_NAME),

                // Quantity Matches
                new NamedType(ProductIdQuantityConditionNode.class, ProductIdQuantityConditionNode.TYPE_NAME),
                new NamedType(ProductSkuQuantityConditionNode.class, ProductSkuQuantityConditionNode.TYPE_NAME)
        );
    }

    @Bean
    public JsonMapperBuilderCustomizer actionStrategySubTypeCustomizer() {
        return builder -> builder.registerSubtypes(
                new NamedType(OrderDiscountAction.class, OrderDiscountAction.TYPE_NAME),
                new NamedType(OrderPercentageDiscountAction.class, OrderPercentageDiscountAction.TYPE_NAME),
                new NamedType(ShippingPercentageDiscountAction.class, ShippingPercentageDiscountAction.TYPE_NAME),
                new NamedType(FreeShippingAction.class, FreeShippingAction.TYPE_NAME),
                new NamedType(ProductIdPercentageDiscountAction.class, ProductIdPercentageDiscountAction.TYPE_NAME),
                new NamedType(ProductSKUPercentageDiscountAction.class, ProductSKUPercentageDiscountAction.TYPE_NAME),
                new NamedType(ProductIdBuyXGetYFreeAction.class, ProductIdBuyXGetYFreeAction.TYPE_NAME),
                new NamedType(ProductSKUBuyXGetYFreeAction.class, ProductSKUBuyXGetYFreeAction.TYPE_NAME)
        );
    }
}
