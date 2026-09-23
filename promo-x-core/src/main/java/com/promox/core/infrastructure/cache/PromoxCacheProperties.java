package com.promox.core.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Per-cache-name TTL overrides for the Redis {@link org.springframework.cache.CacheManager}.
 * Keys must match the Spring cache names used in {@code @Cacheable}/{@code @CacheEvict}.
 */
@ConfigurationProperties(prefix = "promox.cache")
public record PromoxCacheProperties(Map<String, Duration> ttls) {
    public PromoxCacheProperties {
        ttls = ttls == null ? Map.of() : Map.copyOf(ttls);
    }
}
