package com.fragment.juc.jmm.project;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于volatile的简单缓存实现
 * 
 * 演示内容：
 * 1. 使用volatile实现简单的缓存
 * 2. volatile的安全发布
 * 3. 不可变对象的设计
 * 
 * @author huabin
 */
public class VolatileCache {

    /**
     * 方案1：错误的实现 - 没有volatile
     */
    static class WrongCache<K, V> {
        private Map<K, V> cache = new HashMap<>();

        public void put(K key, V value) {
            Map<K, V> newCache = new HashMap<>(cache);
            newCache.put(key, value);
            cache = newCache; // 没有volatile，可能不可见
        }

        public V get(K key) {
            return cache.get(key);
        }
    }

    /**
     * 方案2：使用volatile - 简单但有限制
     */
    static class SimpleVolatileCache<K, V> {
        // volatile保证cache引用的可见性
        // 但Map本身不是线程安全的
        private volatile Map<K, V> cache = new HashMap<>();

        /**
         * 写操作：创建新的不可变Map
         */
        public void put(K key, V value) {
            // 1. 复制当前cache
            Map<K, V> newCache = new HashMap<>(cache);
            // 2. 修改副本
            newCache.put(key, value);
            // 3. volatile写，保证新cache对其他线程可见
            cache = newCache;
        }

        /**
         * 读操作：读取volatile引用
         */
        public V get(K key) {
            // volatile读，保证能看到最新的cache
            return cache.get(key);
        }

        public int size() {
            return cache.size();
        }
    }

    /**
     * 方案3：使用ReadWriteLock - 更高效
     */
    static class ReadWriteLockCache<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        public void put(K key, V value) {
            lock.writeLock().lock();
            try {
                cache.put(key, value);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public V get(K key) {
            lock.readLock().lock();
            try {
                return cache.get(key);
            } finally {
                lock.readLock().unlock();
            }
        }

        public int size() {
            lock.readLock().lock();
            try {
                return cache.size();
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * 不可变的缓存条目
     */
    static class CacheEntry<V> {
        private final V value;
        private final long timestamp;
        private final long ttl; // Time To Live (毫秒)

        public CacheEntry(V value, long ttl) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        public V getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl;
        }

        @Override
        public String toString() {
            return "CacheEntry{value=" + value + 
                   ", age=" + (System.currentTimeMillis() - timestamp) + "ms" +
                   ", ttl=" + ttl + "ms" +
                   ", expired=" + isExpired() + "}";
        }
    }

    /**
     * 方案4：带过期时间的volatile缓存
     */
    static class VolatileCacheWithTTL<K, V> {
        private volatile Map<K, CacheEntry<V>> cache = new HashMap<>();

        public void put(K key, V value, long ttl) {
            Map<K, CacheEntry<V>> newCache = new HashMap<>(cache);
            newCache.put(key, new CacheEntry<>(value, ttl));
            cache = newCache;
        }

        public V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (entry.isExpired()) {
                // 过期了，返回null
                // 注意：这里不删除过期条目，因为删除需要写操作
                return null;
            }
            return entry.getValue();
        }

        public void cleanup() {
            Map<K, CacheEntry<V>> newCache = new HashMap<>();
            for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
                if (!entry.getValue().isExpired()) {
                    newCache.put(entry.getKey(), entry.getValue());
                }
            }
            cache = newCache;
        }

        public int size() {
            return cache.size();
        }
    }

    /**
     * 测试缓存的并发性能
     */
    private static void testCachePerformance(String name, 
                                             Runnable writeTask, 
                                             Runnable readTask) 
            throws InterruptedException {
        System.out.println("\n---------- 测试: " + name + " ----------");

        int writerCount = 2;
        int readerCount = 8;
        int operations = 1000;

        long startTime = System.currentTimeMillis();

        // 创建写线程
        Thread[] writers = new Thread[writerCount];
        for (int i = 0; i < writerCount; i++) {
            writers[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    writeTask.run();
                }
            }, "Writer-" + i);
            writers[i].start();
        }

        // 创建读线程
        Thread[] readers = new Thread[readerCount];
        for (int i = 0; i < readerCount; i++) {
            readers[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    readTask.run();
                }
            }, "Reader-" + i);
            readers[i].start();
        }

        // 等待所有线程完成
        for (Thread writer : writers) {
            writer.join();
        }
        for (Thread reader : readers) {
            reader.join();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("总耗时: " + (endTime - startTime) + "ms");
    }

    /**
     * 演示volatile缓存的使用
     */
    public static void demoVolatileCache() throws InterruptedException {
        System.out.println("\n========== 演示：volatile缓存 ==========\n");

        SimpleVolatileCache<String, String> cache = new SimpleVolatileCache<>();

        // 写线程
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                String key = "key" + i;
                String value = "value" + i;
                cache.put(key, value);
                System.out.println("[Writer] 写入: " + key + " = " + value);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Writer-Thread");

        // 读线程
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                String key = "key" + (i % 5);
                String value = cache.get(key);
                System.out.println("[Reader] 读取: " + key + " = " + value);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Reader-Thread");

        writer.start();
        Thread.sleep(50); // 让writer先启动
        reader.start();

        writer.join();
        reader.join();

        System.out.println("\n最终缓存大小: " + cache.size());
        System.out.println("✅ volatile保证了缓存的可见性");
    }

    /**
     * 演示带TTL的缓存
     */
    public static void demoCacheWithTTL() throws InterruptedException {
        System.out.println("\n========== 演示：带过期时间的缓存 ==========\n");

        VolatileCacheWithTTL<String, String> cache = new VolatileCacheWithTTL<>();

        // 写入数据
        cache.put("short", "过期快", 1000);  // 1秒过期
        cache.put("long", "过期慢", 5000);   // 5秒过期
        System.out.println("写入两个缓存条目");

        // 立即读取
        System.out.println("\n立即读取:");
        System.out.println("short = " + cache.get("short"));
        System.out.println("long = " + cache.get("long"));

        // 等待2秒
        System.out.println("\n等待2秒...");
        Thread.sleep(2000);

        // 再次读取
        System.out.println("\n2秒后读取:");
        System.out.println("short = " + cache.get("short") + " (已过期)");
        System.out.println("long = " + cache.get("long") + " (未过期)");

        // 清理过期条目
        System.out.println("\n清理前缓存大小: " + cache.size());
        cache.cleanup();
        System.out.println("清理后缓存大小: " + cache.size());
    }

    /**
     * 性能对比
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 性能对比 ==========");

        // 测试1：volatile缓存
        SimpleVolatileCache<Integer, Integer> volatileCache = new SimpleVolatileCache<>();
        testCachePerformance("Volatile缓存",
                () -> volatileCache.put((int)(Math.random() * 100), 1),
                () -> volatileCache.get((int)(Math.random() * 100)));

        // 测试2：ReadWriteLock缓存
        ReadWriteLockCache<Integer, Integer> lockCache = new ReadWriteLockCache<>();
        testCachePerformance("ReadWriteLock缓存",
                () -> lockCache.put((int)(Math.random() * 100), 1),
                () -> lockCache.get((int)(Math.random() * 100)));

        System.out.println("\n性能分析:");
        System.out.println("  Volatile缓存:");
        System.out.println("    - 写操作需要复制整个Map，开销大");
        System.out.println("    - 读操作无锁，性能好");
        System.out.println("    - 适合读多写少的场景");
        System.out.println("  ReadWriteLock缓存:");
        System.out.println("    - 写操作有锁，但不需要复制");
        System.out.println("    - 读操作有锁，但多个读可以并发");
        System.out.println("    - 适合读写都较多的场景");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            基于volatile的简单缓存实现                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本的volatile缓存
        demoVolatileCache();

        // 演示2：带TTL的缓存
        demoCacheWithTTL();

        // 演示3：性能对比
        comparePerformance();

        System.out.println("\n" + "===========================");
        System.out.println("学习要点：");
        System.out.println("1. volatile适合发布不可变对象");
        System.out.println("2. 写时复制（Copy-On-Write）适合读多写少");
        System.out.println("3. volatile不能替代锁，只适合特定场景");
        System.out.println("4. 选择合适的并发策略很重要");
        System.out.println("===========================");
    }
}
