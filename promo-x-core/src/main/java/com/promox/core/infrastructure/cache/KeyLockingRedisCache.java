package com.promox.core.infrastructure.cache;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

/**
 * {@link RedisCache} whose {@code @Cacheable(sync = true)} path locks a single entry instead of the whole cache.
 * <p>
 * On a miss, only callers loading the same cache name and key wait for each other, on this node and on every
 * other node sharing the Redis instance: the lock from {@link RedisLockRegistry} is a local lock per key backed
 * by a Redis lock per key. The first caller loads and caches the value; the others find it once they get the lock.
 */
public class KeyLockingRedisCache extends RedisCache {
    private static final Logger log = LoggerFactory.getLogger(KeyLockingRedisCache.class);

    private final RedisLockRegistry lockRegistry;

    protected KeyLockingRedisCache(
        String name,
        RedisCacheWriter cacheWriter,
        RedisCacheConfiguration cacheConfiguration,
        RedisLockRegistry lockRegistry
    ) {
        super(name, cacheWriter, cacheConfiguration);
        this.lockRegistry = lockRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return (T) cached.get();
        }

        // The prefixed cache key (e.g. "promotion-rules::42"), so locks of different caches never collide.
        Lock lock = lockRegistry.obtain(createCacheKey(key));
        lock.lock();
        try {
            // Another thread or node may have loaded the value while this one waited for the lock.
            cached = get(key);
            if (cached != null) {
                return (T) cached.get();
            }
            // loadCacheValue only calls the loader (wrapping failures in ValueRetrievalException), it doesn't store.
            T value = loadCacheValue(key, valueLoader);
            put(key, value);
            return value;
        } finally {
            unlock(lock, key);
        }
    }

    private void unlock(Lock lock, Object key) {
        try {
            lock.unlock();
        } catch (RuntimeException e) {
            // The lock expired in Redis because loading took longer than its TTL. The value is already loaded,
            // so the caller still gets it; other callers may have loaded it concurrently in the meantime.
            log.warn("Could not release the cache lock for '{}' in cache '{}'", key, getName(), e);
        }
    }
}
