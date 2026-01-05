package com.fragment.juc.lock.project;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * 基于读写锁的缓存实现
 * 
 * 实现内容：
 * 1. 基于ReadWriteLock的缓存
 * 2. 基于StampedLock的缓存
 * 3. 支持过期时间
 * 4. 性能对比
 * 
 * @author huabin
 */
public class ReadWriteCache {

    /**
     * 缓存条目
     */
    static class CacheEntry<V> {
        final V value;
        final long createTime;
        final long ttl; // 存活时间（毫秒）

        CacheEntry(V value, long ttl) {
            this.value = value;
            this.createTime = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return ttl > 0 && (System.currentTimeMillis() - createTime) > ttl;
        }

        @Override
        public String toString() {
            return "CacheEntry{value=" + value + 
                   ", age=" + (System.currentTimeMillis() - createTime) + "ms" +
                   ", expired=" + isExpired() + "}";
        }
    }

    /**
     * 基于ReadWriteLock的缓存实现
     */
    static class ReadWriteLockCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Lock readLock = rwLock.readLock();
        private final Lock writeLock = rwLock.writeLock();

        /**
         * 获取缓存值
         */
        public V get(K key) {
            readLock.lock();
            try {
                CacheEntry<V> entry = cache.get(key);
                if (entry != null && !entry.isExpired()) {
                    return entry.value;
                }
                return null;
            } finally {
                readLock.unlock();
            }
        }

        /**
         * 放入缓存
         */
        public void put(K key, V value, long ttl) {
            writeLock.lock();
            try {
                cache.put(key, new CacheEntry<>(value, ttl));
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * 计算并缓存（如果不存在）
         */
        public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction, long ttl) {
            // 先尝试读取
            readLock.lock();
            try {
                CacheEntry<V> entry = cache.get(key);
                if (entry != null && !entry.isExpired()) {
                    return entry.value;
                }
            } finally {
                readLock.unlock();
            }

            // 需要计算，获取写锁
            writeLock.lock();
            try {
                // 双重检查
                CacheEntry<V> entry = cache.get(key);
                if (entry == null || entry.isExpired()) {
                    V value = mappingFunction.apply(key);
                    cache.put(key, new CacheEntry<>(value, ttl));
                    return value;
                }
                return entry.value;
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * 移除缓存
         */
        public void remove(K key) {
            writeLock.lock();
            try {
                cache.remove(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * 清理过期条目
         */
        public int cleanup() {
            writeLock.lock();
            try {
                int removed = 0;
                cache.entrySet().removeIf(entry -> {
                    if (entry.getValue().isExpired()) {
                        removed++;
                        return true;
                    }
                    return false;
                });
                return removed;
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * 获取缓存大小
         */
        public int size() {
            readLock.lock();
            try {
                return cache.size();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * 清空缓存
         */
        public void clear() {
            writeLock.lock();
            try {
                cache.clear();
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * 基于StampedLock的缓存实现（更高性能）
     */
    static class StampedLockCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new HashMap<>();
        private final StampedLock lock = new StampedLock();

        /**
         * 获取缓存值（使用乐观读）
         */
        public V get(K key) {
            // 乐观读
            long stamp = lock.tryOptimisticRead();
            CacheEntry<V> entry = cache.get(key);
            
            if (!lock.validate(stamp)) {
                // 升级为悲观读
                stamp = lock.readLock();
                try {
                    entry = cache.get(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            return (entry != null && !entry.isExpired()) ? entry.value : null;
        }

        /**
         * 放入缓存
         */
        public void put(K key, V value, long ttl) {
            long stamp = lock.writeLock();
            try {
                cache.put(key, new CacheEntry<>(value, ttl));
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        /**
         * 计算并缓存（如果不存在）
         */
        public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction, long ttl) {
            // 先尝试乐观读
            long stamp = lock.tryOptimisticRead();
            CacheEntry<V> entry = cache.get(key);
            
            if (!lock.validate(stamp)) {
                stamp = lock.readLock();
                try {
                    entry = cache.get(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }

            // 需要计算，获取写锁
            stamp = lock.writeLock();
            try {
                // 双重检查
                entry = cache.get(key);
                if (entry == null || entry.isExpired()) {
                    V value = mappingFunction.apply(key);
                    cache.put(key, new CacheEntry<>(value, ttl));
                    return value;
                }
                return entry.value;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        /**
         * 获取缓存大小
         */
        public int size() {
            long stamp = lock.tryOptimisticRead();
            int size = cache.size();
            
            if (!lock.validate(stamp)) {
                stamp = lock.readLock();
                try {
                    size = cache.size();
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            
            return size;
        }
    }

    /**
     * 演示1：基本缓存操作
     */
    public static void demoBasicOperations() throws InterruptedException {
        System.out.println("\n========== 演示1：基本缓存操作 ==========\n");

        ReadWriteLockCache<String, String> cache = new ReadWriteLockCache<>();

        // 放入缓存
        cache.put("user:1", "Alice", 5000);
        cache.put("user:2", "Bob", 5000);
        cache.put("user:3", "Charlie", 2000); // 2秒过期

        System.out.println("初始缓存大小: " + cache.size());

        // 读取缓存
        System.out.println("\n读取缓存:");
        System.out.println("  user:1 = " + cache.get("user:1"));
        System.out.println("  user:2 = " + cache.get("user:2"));
        System.out.println("  user:3 = " + cache.get("user:3"));

        // 等待user:3过期
        System.out.println("\n等待2秒...");
        Thread.sleep(2500);

        System.out.println("\n2秒后读取:");
        System.out.println("  user:1 = " + cache.get("user:1"));
        System.out.println("  user:3 = " + cache.get("user:3") + " (已过期)");

        // 清理过期条目
        int removed = cache.cleanup();
        System.out.println("\n清理了 " + removed + " 个过期条目");
        System.out.println("清理后缓存大小: " + cache.size());

        System.out.println("\n✅ 缓存支持过期时间");
    }

    /**
     * 演示2：computeIfAbsent
     */
    public static void demoComputeIfAbsent() throws InterruptedException {
        System.out.println("\n========== 演示2：computeIfAbsent ==========\n");

        ReadWriteLockCache<Integer, String> cache = new ReadWriteLockCache<>();

        // 模拟数据库查询
        java.util.function.Function<Integer, String> dbQuery = userId -> {
            System.out.println("  [DB] 查询用户: " + userId);
            try {
                Thread.sleep(100); // 模拟数据库延迟
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "User-" + userId;
        };

        // 多线程并发访问
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 1; j <= 3; j++) {
                    String value = cache.computeIfAbsent(j, dbQuery, 5000);
                    System.out.println("[" + Thread.currentThread().getName() + 
                                     "] 获取: user:" + j + " = " + value);
                }
            }, "Thread-" + i);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("\n缓存大小: " + cache.size());
        System.out.println("✅ computeIfAbsent避免了重复计算");
    }

    /**
     * 演示3：性能对比
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 演示3：性能对比 ==========\n");

        final int threadCount = 10;
        final int operations = 10000;

        // 测试ReadWriteLockCache
        System.out.println("测试ReadWriteLockCache...");
        ReadWriteLockCache<Integer, Integer> rwCache = new ReadWriteLockCache<>();
        long time1 = testCache(rwCache, threadCount, operations);

        // 测试StampedLockCache
        System.out.println("测试StampedLockCache...");
        StampedLockCache<Integer, Integer> slCache = new StampedLockCache<>();
        long time2 = testCache(slCache, threadCount, operations);

        // 输出对比
        System.out.println("\n性能对比:");
        System.out.println("  ReadWriteLockCache: " + time1 + "ms");
        System.out.println("  StampedLockCache:   " + time2 + "ms");
        System.out.println("  性能提升: " + 
                         String.format("%.2f%%", (time1 - time2) * 100.0 / time1));

        System.out.println("\n📊 分析:");
        System.out.println("  StampedLock的乐观读在读多写少场景下性能更优");
    }

    private static long testCache(Object cache, int threadCount, int operations) 
            throws InterruptedException {
        Thread[] threads = new Thread[threadCount];
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    int key = j % 100;
                    if (j % 10 == 0) {
                        // 10%写操作
                        if (cache instanceof ReadWriteLockCache) {
                            ((ReadWriteLockCache<Integer, Integer>) cache).put(key, j, 10000);
                        } else {
                            ((StampedLockCache<Integer, Integer>) cache).put(key, j, 10000);
                        }
                    } else {
                        // 90%读操作
                        if (cache instanceof ReadWriteLockCache) {
                            ((ReadWriteLockCache<Integer, Integer>) cache).get(key);
                        } else {
                            ((StampedLockCache<Integer, Integer>) cache).get(key);
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return System.currentTimeMillis() - startTime;
    }

    /**
     * 演示4：实际应用场景
     */
    public static void demoRealWorldUsage() throws InterruptedException {
        System.out.println("\n========== 演示4：实际应用场景 ==========\n");

        // 场景：用户信息缓存
        ReadWriteLockCache<String, UserInfo> userCache = new ReadWriteLockCache<>();

        class UserInfo {
            String userId;
            String name;
            int age;

            UserInfo(String userId, String name, int age) {
                this.userId = userId;
                this.name = name;
                this.age = age;
            }

            @Override
            public String toString() {
                return "UserInfo{userId='" + userId + "', name='" + name + "', age=" + age + "}";
            }
        }

        // 模拟从数据库加载用户
        java.util.function.Function<String, UserInfo> loadUser = userId -> {
            System.out.println("  [DB] 加载用户: " + userId);
            try {
                Thread.sleep(100); // 模拟数据库查询
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return new UserInfo(userId, "User-" + userId, 25);
        };

        System.out.println("模拟多个请求访问用户信息:\n");

        // 模拟多个请求
        Thread[] requests = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int requestId = i;
            requests[i] = new Thread(() -> {
                String userId = "user" + (requestId % 2 + 1); // 只访问user1和user2
                UserInfo user = userCache.computeIfAbsent(userId, loadUser, 5000);
                System.out.println("[Request-" + requestId + "] 获取用户: " + user);
            }, "Request-" + i);
        }

        for (Thread request : requests) {
            request.start();
            Thread.sleep(50);
        }

        for (Thread request : requests) {
            request.join();
        }

        System.out.println("\n缓存统计:");
        System.out.println("  缓存大小: " + userCache.size());
        System.out.println("  ✅ 只查询了2次数据库，其他请求命中缓存");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 缓存实现总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 线程安全：使用读写锁保证并发安全");
        System.out.println("   2. 过期机制：支持TTL过期时间");
        System.out.println("   3. 懒加载：computeIfAbsent支持懒加载");
        System.out.println("   4. 高性能：读写分离，读操作可并发");

        System.out.println("\n📊 两种实现对比:");
        System.out.println("   ReadWriteLockCache:");
        System.out.println("     - 使用ReentrantReadWriteLock");
        System.out.println("     - 可重入");
        System.out.println("     - API简单");
        System.out.println("   StampedLockCache:");
        System.out.println("     - 使用StampedLock乐观读");
        System.out.println("     - 性能更高");
        System.out.println("     - 不可重入");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 用户信息缓存");
        System.out.println("   ✅ 配置信息缓存");
        System.out.println("   ✅ 字典数据缓存");
        System.out.println("   ✅ 读多写少的数据");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 合理设置TTL避免内存泄漏");
        System.out.println("   2. 定期清理过期条目");
        System.out.println("   3. 考虑缓存大小限制");
        System.out.println("   4. 注意缓存穿透、击穿、雪崩问题");

        System.out.println("\n🚀 优化建议:");
        System.out.println("   1. 使用LRU淘汰策略");
        System.out.println("   2. 添加缓存统计（命中率）");
        System.out.println("   3. 支持批量操作");
        System.out.println("   4. 考虑使用Caffeine等成熟缓存库");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            基于读写锁的缓存实现                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本操作
        demoBasicOperations();

        // 演示2：computeIfAbsent
        demoComputeIfAbsent();

        // 演示3：性能对比
        comparePerformance();

        // 演示4：实际应用
        demoRealWorldUsage();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. 读写锁非常适合实现缓存");
        System.out.println("2. StampedLock的乐观读性能更优");
        System.out.println("3. computeIfAbsent避免重复计算");
        System.out.println("4. 需要考虑过期、淘汰等机制");
        System.out.println("5. 实际项目建议使用成熟的缓存库");
        System.out.println("===========================");
    }
}
