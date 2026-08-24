package com.promox.core.promotion.internal.infrastructure.json;

import com.promox.core.promotion.internal.infrastructure.registry.ActionStrategyRegistry;
import com.promox.core.promotion.internal.infrastructure.registry.ConditionNodeRegistry;
import com.promox.core.promotion.internal.service.condition.*;
import com.promox.core.promotion.internal.service.action.*;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;


@Configuration
class PromotionJacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer conditionNodeSubtypeCustomizer(ConditionNodeRegistry registry) {
        return builder -> {
            NamedType[] namedTypes = registry.getAllSpecs().stream()
                .map(spec -> new NamedType(spec.implementationClass(), spec.typeName()))
                .toArray(NamedType[]::new);
            builder.registerSubtypes(namedTypes);
        };
    }

    @Bean
    public JsonMapperBuilderCustomizer actionStrategySubTypeCustomizer(ActionStrategyRegistry registry) {
        return builder -> {
            NamedType[] namedTypes = registry.getAllSpecs().stream()
                .map(spec -> new NamedType(spec.implementationClass(), spec.typeName()))
                .toArray(NamedType[]::new);
            builder.registerSubtypes(namedTypes);
        };
    }

    @Bean
    public HibernatePropertiesCustomizer hibernateJsonFormatMapperCustomizer(JsonMapper jsonMapper) {
        FormatMapper formatMapper = new Jackson3JsonFormatMapper(jsonMapper);
        return props -> props.put(AvailableSettings.JSON_FORMAT_MAPPER, formatMapper);
    }
}