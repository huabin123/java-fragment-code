package com.fragment.juc.container.project;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 并发缓存实战 - 基于ConcurrentHashMap的高性能缓存
 * 
 * <p>功能特性：
 * <ul>
 *   <li>线程安全的缓存操作</li>
 *   <li>支持TTL（过期时间）</li>
 *   <li>支持LRU淘汰策略</li>
 *   <li>缓存统计（命中率、QPS等）</li>
 *   <li>自动过期清理</li>
 * </ul>
 * 
 * <p>应用场景：
 * <ul>
 *   <li>数据库查询结果缓存</li>
 *   <li>API响应缓存</li>
 *   <li>配置信息缓存</li>
 * </ul>
 * 
 * @author fragment
 */
public class ConcurrentCache {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 并发缓存实战 ==========\n");

        // 场景1：基本缓存功能
        demonstrateBasicCache();

        Thread.sleep(1000);

        // 场景2：带TTL的缓存
        demonstrateTTLCache();

        Thread.sleep(1000);

        // 场景3：LRU缓存
        demonstrateLRUCache();

        Thread.sleep(1000);

        // 场景4：缓存统计
        demonstrateCacheStatistics();
    }

    /**
     * 场景1：基本缓存功能
     */
    private static void demonstrateBasicCache() {
        System.out.println("=== 场景1：基本缓存功能 ===\n");

        SimpleCache<String, User> cache = new SimpleCache<>();

        // 添加缓存
        System.out.println("添加缓存:");
        cache.put("user1", new User(1, "Alice"));
        cache.put("user2", new User(2, "Bob"));
        cache.put("user3", new User(3, "Charlie"));
        System.out.println("缓存大小: " + cache.size());

        // 获取缓存
        System.out.println("\n获取缓存:");
        User user = cache.get("user1");
        System.out.println("user1: " + user);

        // 删除缓存
        System.out.println("\n删除缓存:");
        cache.remove("user2");
        System.out.println("删除user2后，缓存大小: " + cache.size());

        // 清空缓存
        System.out.println("\n清空缓存:");
        cache.clear();
        System.out.println("清空后，缓存大小: " + cache.size());

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：带TTL的缓存
     */
    private static void demonstrateTTLCache() throws InterruptedException {
        System.out.println("=== 场景2：带TTL的缓存 ===\n");

        TTLCache<String, String> cache = new TTLCache<>();
        cache.start();

        // 添加缓存（2秒过期）
        System.out.println("添加缓存（2秒过期）:");
        cache.put("key1", "value1", 2000);
        cache.put("key2", "value2", 4000);
        System.out.println("key1: " + cache.get("key1"));
        System.out.println("key2: " + cache.get("key2"));

        // 等待过期
        System.out.println("\n等待3秒...");
        Thread.sleep(3000);

        System.out.println("3秒后:");
        System.out.println("key1: " + cache.get("key1") + " (已过期)");
        System.out.println("key2: " + cache.get("key2") + " (未过期)");

        cache.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：LRU缓存
     */
    private static void demonstrateLRUCache() {
        System.out.println("=== 场景3：LRU缓存 ===\n");

        LRUCache<String, String> cache = new LRUCache<>(3);  // 容量3

        // 添加缓存
        System.out.println("添加缓存（容量3）:");
        cache.put("A", "ValueA");
        System.out.println("添加A，缓存: " + cache.keySet());
        
        cache.put("B", "ValueB");
        System.out.println("添加B，缓存: " + cache.keySet());
        
        cache.put("C", "ValueC");
        System.out.println("添加C，缓存: " + cache.keySet());

        // 访问A（更新访问时间）
        System.out.println("\n访问A:");
        cache.get("A");
        System.out.println("访问A后，缓存: " + cache.keySet());

        // 添加D（触发淘汰）
        System.out.println("\n添加D（触发淘汰）:");
        cache.put("D", "ValueD");
        System.out.println("添加D后，缓存: " + cache.keySet() + " (B被淘汰)");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景4：缓存统计
     */
    private static void demonstrateCacheStatistics() throws InterruptedException {
        System.out.println("=== 场景4：缓存统计 ===\n");

        StatisticsCache<String, String> cache = new StatisticsCache<>();

        // 模拟缓存访问
        System.out.println("模拟缓存访问:");
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // 模拟命中和未命中
        for (int i = 0; i < 100; i++) {
            if (i % 3 == 0) {
                cache.get("key" + (i % 3 + 1));  // 命中
            } else {
                cache.get("key" + (i + 10));  // 未命中
            }
            Thread.sleep(10);
        }

        // 打印统计
        cache.printStatistics();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 简单缓存
     */
    static class SimpleCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();

        public void put(K key, V value) {
            cache.put(key, value);
        }

        public V get(K key) {
            return cache.get(key);
        }

        public V remove(K key) {
            return cache.remove(key);
        }

        public void clear() {
            cache.clear();
        }

        public int size() {
            return cache.size();
        }

        public java.util.Set<K> keySet() {
            return cache.keySet();
        }
    }

    /**
     * 带TTL的缓存
     */
    static class TTLCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
        private final ScheduledExecutorService cleanupExecutor = 
            Executors.newSingleThreadScheduledExecutor();

        public void start() {
            // 定期清理过期缓存
            cleanupExecutor.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            }, 1, 1, TimeUnit.SECONDS);
        }

        public void put(K key, V value, long ttlMs) {
            long expireTime = System.currentTimeMillis() + ttlMs;
            cache.put(key, new CacheEntry<>(value, expireTime));
        }

        public V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            
            if (entry.isExpired(System.currentTimeMillis())) {
                cache.remove(key);
                return null;
            }
            
            return entry.getValue();
        }

        public void shutdown() {
            cleanupExecutor.shutdown();
        }

        static class CacheEntry<V> {
            private final V value;
            private final long expireTime;

            public CacheEntry(V value, long expireTime) {
                this.value = value;
                this.expireTime = expireTime;
            }

            public V getValue() {
                return value;
            }

            public boolean isExpired(long now) {
                return now >= expireTime;
            }
        }
    }

    /**
     * LRU缓存
     */
    static class LRUCache<K, V> {
        private final int capacity;
        private final Map<K, LRUEntry<V>> cache = new ConcurrentHashMap<>();

        public LRUCache(int capacity) {
            this.capacity = capacity;
        }

        public void put(K key, V value) {
            // 如果已存在，更新访问时间
            if (cache.containsKey(key)) {
                cache.get(key).updateAccessTime();
                return;
            }

            // 如果已满，淘汰最久未使用的
            if (cache.size() >= capacity) {
                evictLRU();
            }

            cache.put(key, new LRUEntry<>(value));
        }

        public V get(K key) {
            LRUEntry<V> entry = cache.get(key);
            if (entry != null) {
                entry.updateAccessTime();
                return entry.getValue();
            }
            return null;
        }

        public java.util.Set<K> keySet() {
            return cache.keySet();
        }

        private void evictLRU() {
            K lruKey = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<K, LRUEntry<V>> entry : cache.entrySet()) {
                long accessTime = entry.getValue().getAccessTime();
                if (accessTime < oldestTime) {
                    oldestTime = accessTime;
                    lruKey = entry.getKey();
                }
            }

            if (lruKey != null) {
                cache.remove(lruKey);
            }
        }

        static class LRUEntry<V> {
            private final V value;
            private long accessTime;

            public LRUEntry(V value) {
                this.value = value;
                this.accessTime = System.currentTimeMillis();
            }

            public V getValue() {
                return value;
            }

            public long getAccessTime() {
                return accessTime;
            }

            public void updateAccessTime() {
                this.accessTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * 统计缓存
     */
    static class StatisticsCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong missCount = new AtomicLong(0);
        private final AtomicLong putCount = new AtomicLong(0);
        private final long startTime = System.currentTimeMillis();

        public void put(K key, V value) {
            cache.put(key, value);
            putCount.incrementAndGet();
        }

        public V get(K key) {
            V value = cache.get(key);
            if (value != null) {
                hitCount.incrementAndGet();
            } else {
                missCount.incrementAndGet();
            }
            return value;
        }

        public void printStatistics() {
            long total = hitCount.get() + missCount.get();
            double hitRate = total > 0 ? (double) hitCount.get() / total * 100 : 0;
            long runningTime = System.currentTimeMillis() - startTime;
            double qps = total > 0 ? (double) total / runningTime * 1000 : 0;

            System.out.println("\n=== 缓存统计 ===");
            System.out.println("缓存大小: " + cache.size());
            System.out.println("写入次数: " + putCount.get());
            System.out.println("命中次数: " + hitCount.get());
            System.out.println("未命中次数: " + missCount.get());
            System.out.println("命中率: " + String.format("%.2f%%", hitRate));
            System.out.println("QPS: " + String.format("%.2f", qps));
        }
    }

    /**
     * 用户类
     */
    static class User {
        private final int id;
        private final String name;

        public User(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "User{id=" + id + ", name='" + name + "'}";
        }
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
