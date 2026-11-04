package com.promox.core.infrastructure.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.integration.redis.util.RedisLockRegistry;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(PromoxCacheProperties.class)
public class RedisCacheConfig {
    // Lock keys in Redis are "<registry key>:<cache key>", e.g. "promox:cache-lock:promotion-rules::42".
    static final String CACHE_LOCK_REGISTRY_KEY = "promox:cache-lock";

    @Bean
    public RedisCacheConfiguration cacheConfiguration(JsonMapper jsonMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                cacheValueSerializer(jsonMapper)))
            .disableCachingNullValues();
    }

    /**
     * Writes the class of every value into the JSON, so a cache hit is read back as the type that was cached;
     * without it, records come back as {@code LinkedHashMap}s. Only application and JDK classes may be named
     * in the JSON, so a tampered entry can't make Jackson instantiate an arbitrary class.
     * <p>
     * Final classes get no type, so a cached method must return a record, an application class or a mutable
     * collection such as {@code ArrayList}: a {@code List.of(...)} or {@code Stream.toList()} result can't be
     * read back, and a {@code Long} comes back as an {@code Integer}. Inside a record any field type works.
     */
    static GenericJacksonJsonRedisSerializer cacheValueSerializer(JsonMapper jsonMapper) {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.promox.")
            .allowIfSubType("java.")
            .build();

        return GenericJacksonJsonRedisSerializer.builder(jsonMapper::rebuild)
            .enableDefaultTyping(typeValidator)
            .build();
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
