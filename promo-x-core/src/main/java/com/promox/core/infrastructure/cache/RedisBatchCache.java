package com.promox.core.infrastructure.cache;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.util.ByteUtils;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Multi-key read-through for a cache managed by the {@link RedisCacheManager}, which Spring's
 * {@code Cache} abstraction can't do: one MGET for all keys, one loader call for the misses,
 * then one pipelined write-back (SET with EX, since MSET can't set a TTL). Keys use the cache's
 * prefix and TTL, so {@code @CacheEvict} and {@code Cache#clear()} work on these entries.
 */
@Component
public class RedisBatchCache {
    private final RedisConnectionFactory connectionFactory;
    private final RedisCacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public RedisBatchCache(
        RedisConnectionFactory connectionFactory,
        RedisCacheManager cacheManager,
        ObjectMapper objectMapper
    ) {
        this.connectionFactory = connectionFactory;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    /**
     * @param cacheName  cache configured in the cache manager (e.g. via {@code promox.cache.ttls})
     * @param ids        identifiers to resolve
     * @param keyMapper  maps an identifier to its cache key
     * @param valueType  type the cached values are deserialized to
     * @param missLoader loads the identifiers that weren't cached; identifiers it omits aren't cached
     * @return resolved values by identifier; unresolvable identifiers are absent
     */
    public <I, V> Map<I, V> getAll(
        String cacheName,
        Collection<I> ids,
        Function<I, String> keyMapper,
        Class<V> valueType,
        Function<Set<I>, Map<I, V>> missLoader
    ) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        RedisCacheConfiguration configuration = configurationFor(cacheName);
        JacksonJsonRedisSerializer<V> valueSerializer = new JacksonJsonRedisSerializer<>(objectMapper, valueType);

        // MGET returns values by position, so keys and results are both indexed from this one list.
        List<I> uniqueIds = List.copyOf(new HashSet<>(ids));
        byte[][] redisKeys = uniqueIds.stream()
            .map(id -> serializeKey(configuration, cacheName, keyMapper.apply(id)))
            .toArray(byte[][]::new);

        Map<I, V> result = readAll(uniqueIds, redisKeys, valueSerializer);

        Set<I> misses = new HashSet<>(uniqueIds);
        misses.removeAll(result.keySet());

        if (!misses.isEmpty()) {
            Map<I, V> loaded = missLoader.apply(Collections.unmodifiableSet(misses));
            if (!loaded.isEmpty()) {
                writeAll(configuration, cacheName, keyMapper, valueSerializer, loaded);
                result.putAll(loaded);
            }
        }
        return result;
    }

    /**
     * Evicts the entries for the given identifiers with a single UNLINK (non-blocking delete).
     *
     * @param cacheName cache configured in the cache manager
     * @param ids       identifiers whose entries to evict
     * @param keyMapper maps an identifier to its cache key (same mapping used for {@link #getAll})
     */
    public <I> void evictAll(String cacheName, Collection<I> ids, Function<I, String> keyMapper) {
        if (ids.isEmpty()) {
            return;
        }

        RedisCacheConfiguration configuration = configurationFor(cacheName);
        byte[][] redisKeys = new HashSet<>(ids).stream()
            .map(id -> serializeKey(configuration, cacheName, keyMapper.apply(id)))
            .toArray(byte[][]::new);

        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.keyCommands().unlink(redisKeys);
        }
    }

    private <I, V> Map<I, V> readAll(
        List<I> uniqueIds,
        byte[][] redisKeys,
        JacksonJsonRedisSerializer<V> valueSerializer
    ) {
        List<byte[]> rawValues;
        try (RedisConnection connection = connectionFactory.getConnection()) {
            rawValues = connection.stringCommands().mGet(redisKeys);
        }

        Map<I, V> hits = new HashMap<>();
        if (rawValues == null) {
            return hits;
        }
        for (int i = 0; i < uniqueIds.size(); i++) {
            byte[] raw = rawValues.get(i);
            if (raw != null) {
                hits.put(uniqueIds.get(i), valueSerializer.deserialize(raw));
            }
        }
        return hits;
    }

    private <I, V> void writeAll(
        RedisCacheConfiguration configuration,
        String cacheName,
        Function<I, String> keyMapper,
        JacksonJsonRedisSerializer<V> valueSerializer,
        Map<I, V> values
    ) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.openPipeline();
            try {
                values.forEach((id, value) -> {
                    if (value == null) {
                        return;
                    }
                    String key = keyMapper.apply(id);
                    connection.stringCommands().set(
                            serializeKey(configuration, cacheName, key),
                            valueSerializer.serialize(value),
                            SetCondition.upsert(),
                            expirationFor(configuration, key, value)
                    );
                });
            } finally {
                connection.closePipeline();
            }
        }
    }

    private RedisCacheConfiguration configurationFor(String cacheName) {
        RedisCacheConfiguration configuration = cacheManager.getCacheConfigurations().get(cacheName);
        if (configuration == null) {
            throw new IllegalArgumentException("Unknown Redis cache '%s'".formatted(cacheName));
        }
        return configuration;
    }

    // Mirrors RedisCache#createCacheKey so the keys match Spring Cache's for the same cache name.
    private static byte[] serializeKey(RedisCacheConfiguration configuration, String cacheName, String key) {
        String cacheKey = configuration.usePrefix() ? configuration.getKeyPrefixFor(cacheName) + key : key;
        return ByteUtils.getBytes(configuration.getKeySerializationPair().write(cacheKey));
    }

    private static Expiration expirationFor(RedisCacheConfiguration configuration, String key, Object value) {
        Duration ttl = configuration.getTtlFunction().getTimeToLive(key, value);
        return ttl == null || ttl.isZero() || ttl.isNegative() ? Expiration.persistent() : Expiration.from(ttl);
    }
}
