package com.fragment.core.collections.hashmap.demo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * HashMap线程安全问题演示
 * 
 * 演示内容：
 * 1. HashMap的线程安全问题
 * 2. 使用Hashtable解决
 * 3. 使用Collections.synchronizedMap解决
 * 4. 使用ConcurrentHashMap解决（推荐）
 * 
 * @author huabin
 */
public class HashMapThreadSafetyDemo {

    private static final int THREAD_COUNT = 10;
    private static final int PUT_COUNT = 1000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== HashMap线程安全问题演示 ==========\n");
        
        // 1. HashMap的线程安全问题
        testHashMap();
        
        // 2. Hashtable
        testHashtable();
        
        // 3. Collections.synchronizedMap
        testSynchronizedMap();
        
        // 4. ConcurrentHashMap（推荐）
        testConcurrentHashMap();
        
        // 5. 性能对比
        performanceComparison();
    }

    /**
     * 1. HashMap的线程安全问题
     */
    private static void testHashMap() throws InterruptedException {
        System.out.println("1. HashMap的线程安全问题");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        // 创建多个线程并发put
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < PUT_COUNT; j++) {
                    map.put("key_" + threadId + "_" + j, j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        int expectedSize = THREAD_COUNT * PUT_COUNT;
        int actualSize = map.size();
        System.out.println("期望大小: " + expectedSize);
        System.out.println("实际大小: " + actualSize);
        System.out.println("数据丢失: " + (expectedSize - actualSize) + "个");
        System.out.println("结论: HashMap在多线程环境下不安全，会导致数据丢失\n");
    }

    /**
     * 2. Hashtable
     */
    private static void testHashtable() throws InterruptedException {
        System.out.println("2. Hashtable");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = new Hashtable<>();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < PUT_COUNT; j++) {
                    map.put("key_" + threadId + "_" + j, j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        int expectedSize = THREAD_COUNT * PUT_COUNT;
        int actualSize = map.size();
        System.out.println("期望大小: " + expectedSize);
        System.out.println("实际大小: " + actualSize);
        System.out.println("结论: Hashtable是线程安全的，但性能较差（所有方法都加synchronized）\n");
    }

    /**
     * 3. Collections.synchronizedMap
     */
    private static void testSynchronizedMap() throws InterruptedException {
        System.out.println("3. Collections.synchronizedMap");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = Collections.synchronizedMap(new HashMap<>());
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < PUT_COUNT; j++) {
                    map.put("key_" + threadId + "_" + j, j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        int expectedSize = THREAD_COUNT * PUT_COUNT;
        int actualSize = map.size();
        System.out.println("期望大小: " + expectedSize);
        System.out.println("实际大小: " + actualSize);
        System.out.println("结论: synchronizedMap是线程安全的，但性能较差（粗粒度锁）\n");
    }

    /**
     * 4. ConcurrentHashMap（推荐）
     */
    private static void testConcurrentHashMap() throws InterruptedException {
        System.out.println("4. ConcurrentHashMap（推荐）");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < PUT_COUNT; j++) {
                    map.put("key_" + threadId + "_" + j, j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        
        int expectedSize = THREAD_COUNT * PUT_COUNT;
        int actualSize = map.size();
        System.out.println("期望大小: " + expectedSize);
        System.out.println("实际大小: " + actualSize);
        System.out.println("结论: ConcurrentHashMap是线程安全的，且性能优异（细粒度锁）\n");
    }

    /**
     * 5. 性能对比
     */
    private static void performanceComparison() throws InterruptedException {
        System.out.println("5. 性能对比");
        System.out.println("----------------------------------------");
        
        int testThreadCount = 20;
        int testPutCount = 10000;
        
        // HashMap（不安全，仅作性能参考）
        long time1 = testPerformance(new HashMap<>(), testThreadCount, testPutCount);
        
        // Hashtable
        long time2 = testPerformance(new Hashtable<>(), testThreadCount, testPutCount);
        
        // Collections.synchronizedMap
        long time3 = testPerformance(Collections.synchronizedMap(new HashMap<>()), testThreadCount, testPutCount);
        
        // ConcurrentHashMap
        long time4 = testPerformance(new ConcurrentHashMap<>(), testThreadCount, testPutCount);
        
        System.out.println("性能对比（" + testThreadCount + "个线程，每个线程put " + testPutCount + "次）:");
        System.out.println("  HashMap: " + time1 + "ms（不安全，仅作参考）");
        System.out.println("  Hashtable: " + time2 + "ms");
        System.out.println("  SynchronizedMap: " + time3 + "ms");
        System.out.println("  ConcurrentHashMap: " + time4 + "ms（推荐）");
        
        System.out.println("\n结论:");
        System.out.println("  1. HashMap性能最好，但不安全");
        System.out.println("  2. Hashtable和SynchronizedMap性能较差");
        System.out.println("  3. ConcurrentHashMap性能接近HashMap，且线程安全");
        System.out.println("  4. 多线程环境下，推荐使用ConcurrentHashMap");
        
        System.out.println();
    }

    /**
     * 测试性能
     */
    private static long testPerformance(Map<String, Integer> map, int threadCount, int putCount) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(threadCount);
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < putCount; j++) {
                    map.put("key_" + threadId + "_" + j, j);
                }
                latch.countDown();
            }).start();
        }
        
        latch.await();
        long end = System.currentTimeMillis();
        
        return end - start;
    }
}
