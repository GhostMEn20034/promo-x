package com.promox.core.infrastructure.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(PromoxCacheProperties.class)
public class RedisCacheConfig {
    // Lock keys in Redis are "<registry key>:<cache key>", e.g. "promox:cache-lock:promotion-rules::42".
    static final String CACHE_LOCK_REGISTRY_KEY = "promox:cache-lock";

    /**
     * Values use the default Java serialization, which keeps their exact types, so every cached value and
     * everything it holds must be {@link java.io.Serializable}.
     * <p>
     * Classes are read back with the application's class loader, as Boot's own Redis cache configuration does,
     * so DevTools' restart class loader doesn't turn a cache hit into a {@code ClassCastException}.
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration(ResourceLoader resourceLoader) {
        return RedisCacheConfiguration.defaultCacheConfig(resourceLoader.getClassLoader())
            .disableCachingNullValues();
    }

    @Bean
    public RedisLockRegistry cacheLockRegistry(
        RedisConnectionFactory connectionFactory,
        PromoxCacheProperties properties
    ) {
        return new RedisLockRegistry(connectionFactory, CACHE_LOCK_REGISTRY_KEY, properties.lockTtl());
    }

    // Replaces Boot's auto-configured cache manager, so spring.cache.redis.* properties no longer apply.
    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        RedisCacheConfiguration cacheConfiguration,
        PromoxCacheProperties properties,
        RedisLockRegistry cacheLockRegistry
    ) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        properties.ttls().forEach((cacheName, ttl) ->
            cacheConfigurations.put(cacheName, cacheConfiguration.entryTtl(ttl))
        );

        return new KeyLockingRedisCacheManager(
            cacheWriter(connectionFactory),
            cacheConfiguration,
            cacheConfigurations,
            cacheLockRegistry
        );
    }

    /**
     * Writes (put, evict, clear) reach Redis before the call returns. By default they are sent asynchronously,
     * so a value loaded under a {@link KeyLockingRedisCache} lock could still be in flight when the lock is
     * released, and the next caller would load it again; an eviction could also land after a later read.
     */
    static RedisCacheWriter cacheWriter(RedisConnectionFactory connectionFactory) {
        return RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites());
    }
}
