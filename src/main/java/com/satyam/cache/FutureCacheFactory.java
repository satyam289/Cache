package com.satyam.cache;

import java.util.function.Function;

public interface FutureCacheFactory<K, V> {

    FutureCache<K, V> createSynchronizedFutureValueCache(int cacheSize, Function<K, V> cacheFunction);

}
