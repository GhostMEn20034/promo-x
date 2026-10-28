package com.promox.core.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Settings for the Redis {@link org.springframework.cache.CacheManager}.
 *
 * @param ttls    per-cache-name TTL overrides; keys must match the Spring cache names used in
 *                {@code @Cacheable}/{@code @CacheEvict}
 * @param lockTtl how long the per-entry lock taken by {@code @Cacheable(sync = true)} lives in Redis; must be
 *                longer than the slowest value load, or another node may load the same entry concurrently
 */
@ConfigurationProperties(prefix = "promox.cache")
public record PromoxCacheProperties(Map<String, Duration> ttls, @DefaultValue("30s") Duration lockTtl) {
    public PromoxCacheProperties {
        ttls = ttls == null ? Map.of() : Map.copyOf(ttls);
    }
}
