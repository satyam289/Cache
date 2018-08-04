package com.satyam.cache;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Component
public final class FutureCacheFactoryImpl<K, V> implements FutureCacheFactory<K, V> {

	@Override
	public FutureCache<K, V> createSynchronizedFutureValueCache(int cacheSize, Function<K, V> cacheFunction) {
		return new SynchronizedFutureValueCache<>(cacheSize, cacheFunction);
	}

	@SuppressWarnings("hiding")
	private final class SynchronizedFutureValueCache<K, V> implements FutureCache<K, V> {

		private final int cacheSize;
		private final Function<K, V> cacheFunction;
		private final Map<K, KeyAndFutureValue<K, V>> map;
		private final Deque<KeyAndFutureValue<K, V>> deque;
		private final Lock cacheLock;
		private final ExecutorService executorService;

		private SynchronizedFutureValueCache(int cacheSize, Function<K, V> cacheFunction) {
			if (cacheSize <= 0 || cacheFunction == null) {
				throw new IllegalArgumentException();
			}
			this.cacheSize = cacheSize;
			this.cacheFunction = cacheFunction;
			this.map = new HashMap<>(cacheSize);
			this.deque = new ArrayDeque<>(cacheSize);
			this.cacheLock = new ReentrantLock();
			this.executorService = Executors.newCachedThreadPool();
		}

		@Override
		public final Future<V> get(K key) {
			if (key == null) {
				throw new IllegalArgumentException();
			}
			return getKeyAndFutureValue(key).getFutureValue();
		}

		@Override
		public final Future<V> update(K key) {
			if (key == null) {
				throw new IllegalArgumentException();
			}
			return updateKeyAndFutureValue(key).getFutureValue();
		}

		private KeyAndFutureValue<K, V> getKeyAndFutureValue(K key) {
			cacheLock.lock();
			try {
				KeyAndFutureValue<K, V> keyAndFutureValue = map.get(key);
				if (keyAndFutureValue != null) {
					deque.remove(keyAndFutureValue);
					deque.offerFirst(keyAndFutureValue);
					return keyAndFutureValue;
				} else {
					keyAndFutureValue = new KeyAndFutureValue<>(key,
							executorService.submit(() -> cacheFunction.apply(key)));
					if (map.size() == cacheSize) {
						map.remove(deque.removeLast().getKey());
					}
					map.put(key, keyAndFutureValue);
					deque.offerFirst(keyAndFutureValue);
					return keyAndFutureValue;
				}
			} finally {
				cacheLock.unlock();
			}
		}

		private KeyAndFutureValue<K, V> updateKeyAndFutureValue(K key) {
			cacheLock.lock();
			try {
				KeyAndFutureValue<K, V> keyAndFutureValue = map.get(key);
				deque.remove(keyAndFutureValue);
				keyAndFutureValue = new KeyAndFutureValue<>(key,
						executorService.submit(() -> cacheFunction.apply(key)));
				map.put(key, keyAndFutureValue);
				deque.offerFirst(keyAndFutureValue);
				return keyAndFutureValue;
			} finally {
				cacheLock.unlock();
			}
		}

	}

	private final class KeyAndFutureValue<K, V> {

		private final K key;
		private final Future<V> futureValue;

		private KeyAndFutureValue(K key, Future<V> futureValue) {
			if (key == null || futureValue == null) {
				throw new IllegalArgumentException();
			}
			this.key = key;
			this.futureValue = futureValue;
		}

		private K getKey() {
			return key;
		}

		private Future<V> getFutureValue() {
			return futureValue;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (object == null || getClass() != object.getClass()) {
				return false;
			}
			KeyAndFutureValue<?, ?> that = (KeyAndFutureValue<?, ?>) object;
			return Objects.equals(getKey(), that.getKey()) && Objects.equals(getFutureValue(), that.getFutureValue());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getKey(), getFutureValue());
		}

	}

}