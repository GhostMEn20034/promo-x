package com.promox.core.infrastructure.cache;

import com.promox.core.shared.IntegrationTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the {@code @Cacheable(sync = true)} path ({@link Cache#get(Object, Callable)}) of
 * {@link KeyLockingRedisCache} against a real Redis.
 */
class KeyLockingRedisCacheIntegrationTest extends IntegrationTestBase {
    // A cache created at runtime, so the tests don't depend on a real cache's TTL or contents.
    private static final String CACHE_NAME = "key-locking-test";
    private static final int CALLERS = 8;
    // Long enough that callers started together overlap with the one that loads.
    private static final long LOAD_MILLIS = 300;

    @Autowired
    private RedisCacheManager cacheManager;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private RedisCacheConfiguration cacheConfiguration;

    @Autowired
    private PromoxCacheProperties cacheProperties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Cache cache;

    @BeforeEach
    void setUp() {
        cache = cacheManager.getCache(CACHE_NAME);
    }

    @Test
    @DisplayName("Should create caches that lock per entry")
    void cacheManager_createsKeyLockingCaches() {
        assertThat(cache).isInstanceOf(KeyLockingRedisCache.class);
    }

    @Test
    @DisplayName("Should load a missing entry once when many callers ask for it at the same time")
    void get_concurrentCallersOnSameKey_loadOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();

        List<Object> values = callConcurrently(CALLERS, () -> cache.get(1L, slowLoader(loads, "rules-1")));

        assertThat(loads).hasValue(1);
        assertThat(values).hasSize(CALLERS).containsOnly(List.of("rules-1"));
    }

    @Test
    @DisplayName("Should load a missing entry once when callers on two nodes ask for it at the same time")
    void get_concurrentCallersOnTwoNodes_loadOnce() throws Exception {
        // A second node: its own cache manager and lock registry (so its own local locks), sharing only Redis.
        RedisLockRegistry otherNodeLocks = new RedisLockRegistry(
            connectionFactory, RedisCacheConfig.CACHE_LOCK_REGISTRY_KEY, cacheProperties.lockTtl()
        );
        try {
            KeyLockingRedisCacheManager otherNode = new KeyLockingRedisCacheManager(
                RedisCacheConfig.cacheWriter(connectionFactory),
                cacheConfiguration,
                Map.of(),
                otherNodeLocks
            );
            otherNode.afterPropertiesSet();
            Cache otherNodeCache = otherNode.getCache(CACHE_NAME);

            AtomicInteger loads = new AtomicInteger();
            AtomicInteger caller = new AtomicInteger();
            List<Object> values = callConcurrently(CALLERS, () -> {
                // Half of the callers run on each node.
                Cache nodeCache = caller.getAndIncrement() % 2 == 0 ? cache : otherNodeCache;
                return nodeCache.get(1L, slowLoader(loads, "rules-1"));
            });

            assertThat(loads).hasValue(1);
            assertThat(values).hasSize(CALLERS).containsOnly(List.of("rules-1"));
        } finally {
            otherNodeLocks.destroy();
        }
    }

    @Test
    @DisplayName("Should not make a caller wait while a different key of the same cache is being loaded")
    void get_differentKeyWhileOtherKeyLoads_doesNotWait() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finishLoading = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> blocked = executor.submit(() -> cache.get(1L, blockingLoader(loading, finishLoading, "rules-1")));
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

                // Key 1's lock is held until finishLoading opens, so this only finishes if key 2 has its own lock.
                Object otherKey = executor.submit(() -> cache.get(2L, () -> List.of("rules-2"))).get(5, TimeUnit.SECONDS);

                assertThat(otherKey).isEqualTo(List.of("rules-2"));
                assertThat(blocked).isNotDone();
            } finally {
                finishLoading.countDown();
            }
            assertThat(blocked.get(5, TimeUnit.SECONDS)).isEqualTo(List.of("rules-1"));
        }
    }

    @Test
    @DisplayName("Should hold the entry's lock in Redis with the configured TTL while loading and remove it afterwards")
    void get_whileLoading_holdsRedisLockWithConfiguredTtl() throws Exception {
        String lockKey = RedisCacheConfig.CACHE_LOCK_REGISTRY_KEY + ":" + CACHE_NAME + "::1";
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finishLoading = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Object> load = executor.submit(() -> cache.get(1L, blockingLoader(loading, finishLoading, "rules-1")));
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

                assertThat(redisTemplate.hasKey(lockKey)).isTrue();
                assertThat(redisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS))
                    .isPositive()
                    .isLessThanOrEqualTo(cacheProperties.lockTtl().toMillis());
            } finally {
                finishLoading.countDown();
            }
            load.get(5, TimeUnit.SECONDS);
        }

        assertThat(redisTemplate.hasKey(lockKey)).isFalse();
    }

    @Test
    @DisplayName("Should return a cached entry without calling the loader")
    void get_cachedEntry_skipsLoader() {
        cache.put(1L, List.of("cached"));
        AtomicInteger loads = new AtomicInteger();

        Object value = cache.get(1L, () -> {
            loads.incrementAndGet();
            return List.of("loaded");
        });

        assertThat(value).isEqualTo(List.of("cached"));
        assertThat(loads).hasValue(0);
    }

    @Test
    @DisplayName("Should release the lock and cache nothing when loading fails, so the next caller can load")
    void get_loaderFails_releasesLockAndCachesNothing() {
        IllegalStateException failure = new IllegalStateException("database unavailable");

        assertThatThrownBy(() -> cache.get(1L, () -> {
            throw failure;
        }))
            .isInstanceOf(Cache.ValueRetrievalException.class)
            .hasCause(failure);

        assertThat(cache.get(1L)).isNull();
        // Would block forever if the failed call had kept the lock.
        assertThat(cache.get(1L, () -> List.of("rules-1"))).isEqualTo(List.of("rules-1"));
    }

    /** Runs {@code call} on {@code callers} threads released at the same moment and returns their results. */
    private static List<Object> callConcurrently(int callers, Callable<Object> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private static Callable<List<String>> slowLoader(AtomicInteger loads, String value) {
        return () -> {
            loads.incrementAndGet();
            Thread.sleep(LOAD_MILLIS);
            return List.of(value);
        };
    }

    /** Signals {@code loading} once it holds the entry's lock, then waits for {@code finishLoading}. */
    private static Callable<List<String>> blockingLoader(
        CountDownLatch loading,
        CountDownLatch finishLoading,
        String value
    ) {
        return () -> {
            loading.countDown();
            assertThat(finishLoading.await(10, TimeUnit.SECONDS)).isTrue();
            return List.of(value);
        };
    }
}
