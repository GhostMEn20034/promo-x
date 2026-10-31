package com.promox.core.infrastructure.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface BatchCacheManager {
    <I, V> Map<I, V> getAll(
            String cacheName,
            Collection<I> ids,
            Function<I, String> keyMapper,
            Class<V> valueType,
            Function<Set<I>, Map<I, V>> missLoader
    );

    <I> void evictAll(
            String cacheName,
            Collection<I> ids,
            Function<I, String> keyMapper
    );
}
