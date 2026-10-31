package com.promox.core.infrastructure.cache;

import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.Map;

/**
 * {@link RedisCacheManager} that creates {@link KeyLockingRedisCache}s, for both the configured caches and the
 * ones created at runtime, so {@code @Cacheable(sync = true)} locks per cache entry across all nodes.
 */
public class KeyLockingRedisCacheManager extends RedisCacheManager {
    private final RedisLockRegistry lockRegistry;

    public KeyLockingRedisCacheManager(
        RedisCacheWriter cacheWriter,
        RedisCacheConfiguration defaultCacheConfiguration,
        Map<String, RedisCacheConfiguration> initialCacheConfigurations,
        RedisLockRegistry lockRegistry
    ) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheConfigurations);
        this.lockRegistry = lockRegistry;
    }

    @Override
    protected RedisCache createRedisCache(@NonNull String name, RedisCacheConfiguration cacheConfiguration) {
        return new KeyLockingRedisCache(
            name,
            getCacheWriter(),
            cacheConfiguration != null ? cacheConfiguration : getDefaultCacheConfiguration(),
            lockRegistry
        );
    }
}
