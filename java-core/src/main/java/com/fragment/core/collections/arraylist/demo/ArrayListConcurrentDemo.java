package com.fragment.core.collections.arraylist.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ArrayList 并发安全演示
 *
 * 演示内容：
 * 1. ArrayList 并发写入导致数据丢失/异常
 * 2. Collections.synchronizedList() 方案
 * 3. CopyOnWriteArrayList 方案及适用场景
 * 4. 三种方案的性能对比
 */
public class ArrayListConcurrentDemo {

    private static final int THREAD_COUNT = 10;
    private static final int OPS_PER_THREAD = 1000;

    public static void main(String[] args) throws Exception {
        demonstrateUnsafe();
        demonstrateSynchronizedList();
        demonstrateCopyOnWrite();
    }

    /**
     * 演示 ArrayList 线程不安全：并发写入导致 size 不足预期，甚至 ArrayIndexOutOfBoundsException
     */
    private static void demonstrateUnsafe() throws Exception {
        System.out.println("=== 1. ArrayList 线程不安全 ===");

        List<Integer> list = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            pool.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    try {
                        list.add(i);
                    } catch (Exception e) {
                        System.out.println("异常: " + e.getClass().getSimpleName());
                    }
                }
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();

        int expected = THREAD_COUNT * OPS_PER_THREAD;
        System.out.println("期望 size=" + expected + ", 实际 size=" + list.size());
        System.out.println("丢失元素: " + (expected - list.size()));
        System.out.println();
    }

    /**
     * Collections.synchronizedList：每个操作加 synchronized，复合操作仍需手动同步
     */
    private static void demonstrateSynchronizedList() throws Exception {
        System.out.println("=== 2. synchronizedList ===");

        List<Integer> list = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            pool.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) list.add(i);
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();

        System.out.println("期望 size=" + (THREAD_COUNT * OPS_PER_THREAD) + ", 实际 size=" + list.size());
        System.out.println("注意：遍历时仍需手动 synchronized(list){ for(...){} }");
        System.out.println();
    }

    /**
     * CopyOnWriteArrayList：写时复制，读无锁，适合读多写少场景
     */
    private static void demonstrateCopyOnWrite() throws Exception {
        System.out.println("=== 3. CopyOnWriteArrayList ===");

        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            pool.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) list.add(i);
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();

        System.out.println("期望 size=" + (THREAD_COUNT * OPS_PER_THREAD) + ", 实际 size=" + list.size());
        System.out.println("适合场景：监听器列表、配置列表（读多写极少）");
        System.out.println("不适合：高频写入（每次写都完整复制数组，内存开销大）");
    }
}
