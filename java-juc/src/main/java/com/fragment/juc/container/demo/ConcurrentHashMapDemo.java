package com.fragment.juc.container.demo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * ConcurrentHashMap演示
 * 
 * 演示内容：
 * 1. 基本使用
 * 2. 线程安全性验证
 * 3. 原子操作方法
 * 4. 性能对比
 * 5. 实际应用场景
 * 
 * @author huabin
 */
public class ConcurrentHashMapDemo {

    /**
     * 演示1：线程安全性对比
     */
    public static void demoThreadSafety() throws InterruptedException {
        System.out.println("\n========== 演示1：线程安全性对比 ==========\n");

        final int threadCount = 10;
        final int operationsPerThread = 1000;

        // 测试HashMap（非线程安全）
        System.out.println("测试HashMap（非线程安全）:");
        Map<Integer, Integer> hashMap = new HashMap<>();
        testMap(hashMap, threadCount, operationsPerThread);
        System.out.println("  HashMap大小: " + hashMap.size() + 
                         " (预期: " + (threadCount * operationsPerThread) + ")");

        // 测试ConcurrentHashMap（线程安全）
        System.out.println("\n测试ConcurrentHashMap（线程安全）:");
        Map<Integer, Integer> concurrentMap = new ConcurrentHashMap<>();
        testMap(concurrentMap, threadCount, operationsPerThread);
        System.out.println("  ConcurrentHashMap大小: " + concurrentMap.size() + 
                         " (预期: " + (threadCount * operationsPerThread) + ")");

        System.out.println("\n✅ ConcurrentHashMap保证了线程安全");
    }

    private static void testMap(Map<Integer, Integer> map, int threadCount, int operations) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    int key = threadId * operations + j;
                    map.put(key, key);
                }
                latch.countDown();
            }).start();
        }

        latch.await();
    }

    /**
     * 演示2：原子操作方法
     */
    public static void demoAtomicOperations() throws InterruptedException {
        System.out.println("\n========== 演示2：原子操作方法 ==========\n");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // putIfAbsent - 不存在时才放入
        System.out.println("1. putIfAbsent():");
        Integer old1 = map.putIfAbsent("key1", 100);
        System.out.println("  首次放入: " + old1 + ", 当前值: " + map.get("key1"));
        Integer old2 = map.putIfAbsent("key1", 200);
        System.out.println("  再次放入: " + old2 + ", 当前值: " + map.get("key1"));

        // computeIfAbsent - 不存在时计算并放入
        System.out.println("\n2. computeIfAbsent():");
        Integer value1 = map.computeIfAbsent("key2", k -> {
            System.out.println("  计算key2的值");
            return 300;
        });
        System.out.println("  首次计算: " + value1);
        Integer value2 = map.computeIfAbsent("key2", k -> {
            System.out.println("  这行不会执行");
            return 400;
        });
        System.out.println("  再次计算: " + value2);

        // computeIfPresent - 存在时重新计算
        System.out.println("\n3. computeIfPresent():");
        map.put("key3", 100);
        System.out.println("  初始值: " + map.get("key3"));
        map.computeIfPresent("key3", (k, v) -> v + 50);
        System.out.println("  计算后: " + map.get("key3"));

        // compute - 无论是否存在都计算
        System.out.println("\n4. compute():");
        map.compute("key4", (k, v) -> v == null ? 1 : v + 1);
        System.out.println("  首次计算: " + map.get("key4"));
        map.compute("key4", (k, v) -> v == null ? 1 : v + 1);
        System.out.println("  再次计算: " + map.get("key4"));

        // merge - 合并值
        System.out.println("\n5. merge():");
        map.put("key5", 100);
        map.merge("key5", 50, Integer::sum);
        System.out.println("  合并后: " + map.get("key5"));

        System.out.println("\n✅ 原子操作方法避免了竞态条件");
    }

    /**
     * 演示3：并发计数器
     */
    public static void demoConcurrentCounter() throws InterruptedException {
        System.out.println("\n========== 演示3：并发计数器 ==========\n");

        ConcurrentHashMap<String, Integer> counter = new ConcurrentHashMap<>();

        int threadCount = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        System.out.println("启动" + threadCount + "个线程，每个线程递增" + incrementsPerThread + "次\n");

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    // 使用compute实现原子递增
                    counter.compute("count", (k, v) -> v == null ? 1 : v + 1);
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        System.out.println("计数结果: " + counter.get("count"));
        System.out.println("预期值: " + (threadCount * incrementsPerThread));
        System.out.println("\n✅ compute()方法保证了原子性");
    }

    /**
     * 演示4：性能对比
     */
    public static void demoPerformanceComparison() throws InterruptedException {
        System.out.println("\n========== 演示4：性能对比 ==========\n");

        final int threadCount = 10;
        final int operations = 100000;

        // 测试ConcurrentHashMap
        System.out.println("测试ConcurrentHashMap...");
        ConcurrentHashMap<Integer, Integer> concurrentMap = new ConcurrentHashMap<>();
        long time1 = testPerformance(concurrentMap, threadCount, operations);

        // 测试Hashtable
        System.out.println("测试Hashtable...");
        Map<Integer, Integer> hashtable = new java.util.Hashtable<>();
        long time2 = testPerformance(hashtable, threadCount, operations);

        // 测试Collections.synchronizedMap
        System.out.println("测试Collections.synchronizedMap...");
        Map<Integer, Integer> syncMap = java.util.Collections.synchronizedMap(new HashMap<>());
        long time3 = testPerformance(syncMap, threadCount, operations);

        System.out.println("\n性能对比:");
        System.out.println("  ConcurrentHashMap:        " + time1 + "ms");
        System.out.println("  Hashtable:                " + time2 + "ms");
        System.out.println("  SynchronizedMap:          " + time3 + "ms");

        System.out.println("\n📊 分析:");
        System.out.println("  ConcurrentHashMap采用分段锁，并发性能最优");
        System.out.println("  Hashtable和SynchronizedMap使用全局锁，性能较差");
    }

    private static long testPerformance(Map<Integer, Integer> map, int threadCount, int operations) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    if (j % 2 == 0) {
                        map.put(j, j);
                    } else {
                        map.get(j);
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 演示5：实际应用 - 缓存
     */
    public static void demoCache() throws InterruptedException {
        System.out.println("\n========== 演示5：实际应用 - 缓存 ==========\n");

        class Cache<K, V> {
            private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();

            public V get(K key, java.util.function.Function<K, V> loader) {
                return cache.computeIfAbsent(key, k -> {
                    System.out.println("  [Cache] 加载: " + key);
                    return loader.apply(k);
                });
            }

            public void put(K key, V value) {
                cache.put(key, value);
            }

            public int size() {
                return cache.size();
            }
        }

        Cache<String, String> cache = new Cache<>();

        // 模拟并发访问
        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            new Thread(() -> {
                // 多个线程访问相同的key
                String value = cache.get("user:1", key -> {
                    try {
                        Thread.sleep(100); // 模拟加载
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return "User-1-Data";
                });
                System.out.println("[线程" + threadId + "] 获取: " + value);
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("\n缓存大小: " + cache.size());
        System.out.println("✅ computeIfAbsent保证了只加载一次");
    }

    /**
     * 演示6：实际应用 - 统计
     */
    public static void demoStatistics() throws InterruptedException {
        System.out.println("\n========== 演示6：实际应用 - 统计 ==========\n");

        ConcurrentHashMap<String, Integer> stats = new ConcurrentHashMap<>();

        // 模拟访问统计
        String[] urls = {"/home", "/about", "/contact", "/products", "/home", "/about"};
        
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                for (String url : urls) {
                    // 原子递增访问次数
                    stats.merge(url, 1, Integer::sum);
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        System.out.println("访问统计:");
        stats.forEach((url, count) -> {
            System.out.println("  " + url + ": " + count + " 次");
        });

        System.out.println("\n✅ merge()方法实现了原子累加");
    }

    /**
     * 演示7：批量操作
     */
    public static void demoBulkOperations() {
        System.out.println("\n========== 演示7：批量操作 ==========\n");

        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 1; i <= 100; i++) {
            map.put(i, i);
        }

        // forEach - 遍历
        System.out.println("1. forEach():");
        map.forEach(10, (k, v) -> {
            if (k <= 5) {
                System.out.println("  " + k + " -> " + v);
            }
        });

        // search - 搜索
        System.out.println("\n2. search():");
        Integer result = map.search(10, (k, v) -> v > 50 ? v : null);
        System.out.println("  第一个大于50的值: " + result);

        // reduce - 归约
        System.out.println("\n3. reduce():");
        Integer sum = map.reduce(10, (k, v) -> v, Integer::sum);
        System.out.println("  所有值的和: " + sum);

        System.out.println("\n✅ 批量操作支持并行处理");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== ConcurrentHashMap总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 线程安全：无需外部同步");
        System.out.println("   2. 高并发：采用分段锁（JDK8后改为CAS+synchronized）");
        System.out.println("   3. 原子操作：提供丰富的原子操作方法");
        System.out.println("   4. 批量操作：支持并行批量处理");
        System.out.println("   5. 不允许null：key和value都不能为null");

        System.out.println("\n📊 核心方法:");
        System.out.println("   putIfAbsent(k, v)      - 不存在时放入");
        System.out.println("   computeIfAbsent(k, f)  - 不存在时计算");
        System.out.println("   computeIfPresent(k, f) - 存在时重新计算");
        System.out.println("   compute(k, f)          - 无条件计算");
        System.out.println("   merge(k, v, f)         - 合并值");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 高并发缓存");
        System.out.println("   ✅ 并发计数器");
        System.out.println("   ✅ 统计信息收集");
        System.out.println("   ✅ 需要线程安全的Map");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. key和value不能为null");
        System.out.println("   2. size()是近似值");
        System.out.println("   3. 迭代器是弱一致性的");
        System.out.println("   4. 批量操作的parallelismThreshold要合理设置");

        System.out.println("\n🔄 vs Hashtable:");
        System.out.println("   ConcurrentHashMap:");
        System.out.println("     - 分段锁，高并发");
        System.out.println("     - 不允许null");
        System.out.println("     - 迭代器弱一致性");
        System.out.println("   Hashtable:");
        System.out.println("     - 全局锁，低并发");
        System.out.println("     - 不允许null");
        System.out.println("     - 迭代器强一致性");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            ConcurrentHashMap演示                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：线程安全性
        demoThreadSafety();

        // 演示2：原子操作
        demoAtomicOperations();

        // 演示3：并发计数器
        demoConcurrentCounter();

        // 演示4：性能对比
        demoPerformanceComparison();

        // 演示5：缓存
        demoCache();

        // 演示6：统计
        demoStatistics();

        // 演示7：批量操作
        demoBulkOperations();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. ConcurrentHashMap是线程安全的高性能Map");
        System.out.println("2. 提供了丰富的原子操作方法");
        System.out.println("3. 适合高并发场景");
        System.out.println("4. key和value都不能为null");
        System.out.println("===========================");
    }
}
