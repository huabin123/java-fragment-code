package com.fragment.core.collections.concurrenthashmap.demo;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ConcurrentHashMap 内部原理演示
 *
 * 演示内容：
 * 1. JDK 7 分段锁 vs JDK 8 CAS + synchronized
 * 2. 并发写入安全验证
 * 3. 与 Hashtable、synchronizedMap 的性能对比
 * 4. size() 的弱一致性
 */
public class ConcurrentHashMapInternalsDemo {

    private static final int THREAD_COUNT = 16;
    private static final int OPS_PER_THREAD = 10_000;

    public static void main(String[] args) throws Exception {
        demonstrateConcurrentSafety();
        demonstratePerformanceComparison();
        demonstrateSizeWeakConsistency();
    }

    /**
     * 验证 ConcurrentHashMap 在高并发写入下数据不丢失
     */
    private static void demonstrateConcurrentSafety() throws Exception {
        System.out.println("=== 1. 并发安全验证 ===");

        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    int key = threadId * OPS_PER_THREAD + i;
                    map.put(key, key);
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();

        int expected = THREAD_COUNT * OPS_PER_THREAD;
        System.out.println("期望 size=" + expected + ", 实际 size=" + map.size());
        System.out.println(map.size() == expected ? "✅ 数据完整" : "❌ 数据丢失");
        System.out.println();
    }

    /**
     * ConcurrentHashMap vs Hashtable vs synchronizedMap 性能对比
     */
    private static void demonstratePerformanceComparison() throws Exception {
        System.out.println("=== 2. 并发性能对比（" + THREAD_COUNT + " 线程，各 " + OPS_PER_THREAD + " 次读写）===");

        // ConcurrentHashMap：读无锁，写只锁单个桶
        long chm = benchmark(new ConcurrentHashMap<>());
        System.out.println("ConcurrentHashMap:  " + chm + "ms");

        // Hashtable：所有操作锁整个对象（方法级 synchronized）
        long ht = benchmark(new Hashtable<>());
        System.out.println("Hashtable:          " + ht + "ms");

        // synchronizedMap：与 Hashtable 类似，全局锁
        long sm = benchmark(java.util.Collections.synchronizedMap(new HashMap<>()));
        System.out.println("synchronizedMap:    " + sm + "ms");

        System.out.println("ConcurrentHashMap 快约 " + (ht / Math.max(chm, 1)) + "x");
        System.out.println();
    }

    private static long benchmark(Map<Integer, Integer> map) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        long start = System.currentTimeMillis();

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    int key = (threadId * OPS_PER_THREAD + i) % 1000;
                    if (i % 4 == 0) map.put(key, key);  // 25% 写
                    else            map.get(key);        // 75% 读
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();
        return System.currentTimeMillis() - start;
    }

    /**
     * size() 的弱一致性：在并发场景下 size() 不保证精确
     */
    private static void demonstrateSizeWeakConsistency() throws Exception {
        System.out.println("=== 3. size() 弱一致性 ===");

        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // 同时 put 和读取 size
        for (int t = 0; t < 4; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 1000; i++) map.put(i, i);
            });
        }

        // 在并发写入过程中读取 size，可能不等于最终值
        System.out.println("并发写入中 size(): " + map.size() + "（不一定等于最终值）");
        pool.shutdown();
        while (!pool.isTerminated()) Thread.sleep(10);
        System.out.println("写入完成后 size(): " + map.size());
        System.out.println("说明：mappingCount() 比 size() 更准确（返回 long，避免 int 溢出）");
        System.out.println("精确计数推荐 LongAdder，而非依赖 size()");
    }
}
