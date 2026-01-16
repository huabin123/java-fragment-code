package com.fragment.juc.queue.practice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存过期管理实战 - 基于DelayQueue的缓存过期清理
 * 
 * <p>场景：带过期时间的缓存系统
 * <ul>
 *   <li>自动过期清理</li>
 *   <li>TTL（Time To Live）支持</li>
 *   <li>LRU（Least Recently Used）支持</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>DelayQueue实现过期管理</li>
 *   <li>ConcurrentHashMap存储数据</li>
 *   <li>线程安全</li>
 * </ul>
 * 
 * @author fragment
 */
public class CacheExpiration {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 缓存过期管理实战 ==========\n");

        // 场景1：基本TTL缓存
        demonstrateBasicTTLCache();

        Thread.sleep(1000);

        // 场景2：访问刷新TTL
        demonstrateRefreshOnAccess();

        Thread.sleep(1000);

        // 场景3：缓存统计
        demonstrateCacheStatistics();
    }

    /**
     * 场景1：基本TTL缓存
     */
    private static void demonstrateBasicTTLCache() throws InterruptedException {
        System.out.println("=== 场景1：基本TTL缓存 ===\n");

        ExpiringCache<String, String> cache = new ExpiringCache<>();
        cache.start();

        // 添加缓存项
        cache.put("key1", "value1", 2000);  // 2秒过期
        cache.put("key2", "value2", 4000);  // 4秒过期
        cache.put("key3", "value3", 6000);  // 6秒过期

        System.out.println("添加3个缓存项，过期时间分别为2s、4s、6s");

        // 查询缓存
        for (int i = 0; i < 7; i++) {
            System.out.println("\n第" + i + "秒:");
            System.out.println("  key1: " + cache.get("key1"));
            System.out.println("  key2: " + cache.get("key2"));
            System.out.println("  key3: " + cache.get("key3"));
            System.out.println("  缓存大小: " + cache.size());
            Thread.sleep(1000);
        }

        cache.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：访问刷新TTL
     */
    private static void demonstrateRefreshOnAccess() throws InterruptedException {
        System.out.println("=== 场景2：访问刷新TTL ===\n");

        RefreshingCache<String, String> cache = new RefreshingCache<>();
        cache.start();

        // 添加缓存项（3秒过期）
        cache.put("data", "important", 3000);
        System.out.println("添加缓存项，3秒过期");

        // 每秒访问一次，刷新TTL
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(1000);
            String value = cache.get("data");
            System.out.println("第" + i + "秒访问: " + value + " (TTL已刷新)");
        }

        // 停止访问，等待过期
        System.out.println("\n停止访问，等待过期...");
        for (int i = 1; i <= 4; i++) {
            Thread.sleep(1000);
            String value = cache.get("data");
            System.out.println("第" + i + "秒: " + value);
        }

        cache.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：缓存统计
     */
    private static void demonstrateCacheStatistics() throws InterruptedException {
        System.out.println("=== 场景3：缓存统计 ===\n");

        StatisticsCache<String, String> cache = new StatisticsCache<>();
        cache.start();

        // 添加缓存项
        for (int i = 1; i <= 10; i++) {
            cache.put("key" + i, "value" + i, 5000);
        }

        // 模拟访问
        for (int i = 0; i < 20; i++) {
            String key = "key" + ((i % 10) + 1);
            cache.get(key);
            Thread.sleep(100);
        }

        // 打印统计
        cache.printStatistics();

        // 等待过期
        Thread.sleep(6000);
        cache.printStatistics();

        cache.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 过期缓存
     */
    static class ExpiringCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();
        private final DelayQueue<ExpireEntry<K>> expireQueue = new DelayQueue<>();
        private volatile boolean running = false;
        private Thread cleanupThread;

        /**
         * 启动清理线程
         */
        public void start() {
            running = true;
            cleanupThread = new Thread(() -> {
                while (running) {
                    try {
                        ExpireEntry<K> entry = expireQueue.take();
                        cache.remove(entry.getKey());
                        System.out.println("[清理] 缓存过期删除: " + entry.getKey());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "CacheCleanup");
            cleanupThread.start();
        }

        /**
         * 添加缓存
         */
        public void put(K key, V value, long ttlMs) {
            cache.put(key, value);
            expireQueue.offer(new ExpireEntry<>(key, ttlMs));
        }

        /**
         * 获取缓存
         */
        public V get(K key) {
            return cache.get(key);
        }

        /**
         * 缓存大小
         */
        public int size() {
            return cache.size();
        }

        /**
         * 停止清理线程
         */
        public void shutdown() {
            running = false;
            if (cleanupThread != null) {
                cleanupThread.interrupt();
            }
        }
    }

    /**
     * 访问刷新缓存
     */
    static class RefreshingCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
        private final DelayQueue<ExpireEntry<K>> expireQueue = new DelayQueue<>();
        private volatile boolean running = false;
        private Thread cleanupThread;

        public void start() {
            running = true;
            cleanupThread = new Thread(() -> {
                while (running) {
                    try {
                        ExpireEntry<K> entry = expireQueue.take();
                        CacheEntry<V> cacheEntry = cache.get(entry.getKey());
                        
                        // 检查是否已被刷新
                        if (cacheEntry != null && 
                            cacheEntry.getExpireTime() <= entry.getExpireTime()) {
                            cache.remove(entry.getKey());
                            System.out.println("[清理] 缓存过期删除: " + entry.getKey());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            cleanupThread.start();
        }

        public void put(K key, V value, long ttlMs) {
            long expireTime = System.currentTimeMillis() + ttlMs;
            cache.put(key, new CacheEntry<>(value, expireTime));
            expireQueue.offer(new ExpireEntry<>(key, ttlMs, expireTime));
        }

        public V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null) {
                // 刷新TTL
                long newExpireTime = System.currentTimeMillis() + 3000;
                entry.setExpireTime(newExpireTime);
                expireQueue.offer(new ExpireEntry<>(key, 3000, newExpireTime));
                return entry.getValue();
            }
            return null;
        }

        public void shutdown() {
            running = false;
            if (cleanupThread != null) {
                cleanupThread.interrupt();
            }
        }
    }

    /**
     * 统计缓存
     */
    static class StatisticsCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();
        private final DelayQueue<ExpireEntry<K>> expireQueue = new DelayQueue<>();
        private volatile boolean running = false;
        private Thread cleanupThread;
        
        private final AtomicInteger hitCount = new AtomicInteger(0);
        private final AtomicInteger missCount = new AtomicInteger(0);
        private final AtomicInteger expireCount = new AtomicInteger(0);

        public void start() {
            running = true;
            cleanupThread = new Thread(() -> {
                while (running) {
                    try {
                        ExpireEntry<K> entry = expireQueue.take();
                        cache.remove(entry.getKey());
                        expireCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            cleanupThread.start();
        }

        public void put(K key, V value, long ttlMs) {
            cache.put(key, value);
            expireQueue.offer(new ExpireEntry<>(key, ttlMs));
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
            int total = hitCount.get() + missCount.get();
            double hitRate = total > 0 ? (double) hitCount.get() / total * 100 : 0;
            
            System.out.println("\n=== 缓存统计 ===");
            System.out.println("缓存大小: " + cache.size());
            System.out.println("命中次数: " + hitCount.get());
            System.out.println("未命中次数: " + missCount.get());
            System.out.println("命中率: " + String.format("%.2f%%", hitRate));
            System.out.println("过期清理: " + expireCount.get());
        }

        public void shutdown() {
            running = false;
            if (cleanupThread != null) {
                cleanupThread.interrupt();
            }
        }
    }

    /**
     * 过期条目
     */
    static class ExpireEntry<K> implements Delayed {
        private final K key;
        private final long expireTime;

        public ExpireEntry(K key, long ttlMs) {
            this(key, ttlMs, System.currentTimeMillis() + ttlMs);
        }

        public ExpireEntry(K key, long ttlMs, long expireTime) {
            this.key = key;
            this.expireTime = expireTime;
        }

        public K getKey() {
            return key;
        }

        public long getExpireTime() {
            return expireTime;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expireTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expireTime, ((ExpireEntry<?>) o).expireTime);
        }
    }

    /**
     * 缓存条目
     */
    static class CacheEntry<V> {
        private final V value;
        private long expireTime;

        public CacheEntry(V value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }

        public V getValue() {
            return value;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
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
