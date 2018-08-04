package com.satyam.cache;

import java.util.concurrent.Future;

public interface FutureCache<K, V> {

    Future<V> get(K key);

    Future<V> update(K key);

}
