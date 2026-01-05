package com.fragment.juc.jmm.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 原子性问题演示
 * 
 * 演示内容：
 * 1. i++的非原子性问题
 * 2. 使用synchronized保证原子性
 * 3. 使用AtomicInteger保证原子性
 * 
 * @author huabin
 */
public class AtomicityDemo {

    /**
     * 场景1：没有同步 - 原子性问题
     */
    static class NoSyncCounter {
        private int count = 0;

        public void increment() {
            count++; // 非原子操作：读-改-写
        }

        public int getCount() {
            return count;
        }
    }

    /**
     * 场景2：使用synchronized - 保证原子性
     */
    static class SyncCounter {
        private int count = 0;

        public synchronized void increment() {
            count++; // synchronized保证原子性
        }

        public synchronized int getCount() {
            return count;
        }
    }

    /**
     * 场景3：使用AtomicInteger - 保证原子性
     */
    static class AtomicCounter {
        private AtomicInteger count = new AtomicInteger(0);

        public void increment() {
            count.incrementAndGet(); // 原子操作
        }

        public int getCount() {
            return count.get();
        }
    }

    /**
     * 场景4：volatile不能保证原子性
     */
    static class VolatileCounter {
        private volatile int count = 0;

        public void increment() {
            count++; // volatile不能保证原子性
        }

        public int getCount() {
            return count;
        }
    }

    /**
     * 测试计数器
     */
    private static void testCounter(String name, Runnable incrementTask, 
                                     java.util.function.Supplier<Integer> getCount) 
            throws InterruptedException {
        System.out.println("\n---------- " + name + " ----------");

        int threadCount = 10;
        int incrementPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        // 创建多个线程并发执行
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    incrementTask.run();
                }
                latch.countDown();
            }, "Thread-" + i).start();
        }

        // 等待所有线程完成
        latch.await();
        long endTime = System.currentTimeMillis();

        int finalCount = getCount.get();
        int expectedCount = threadCount * incrementPerThread;

        System.out.println("预期结果: " + expectedCount);
        System.out.println("实际结果: " + finalCount);
        System.out.println("数据丢失: " + (expectedCount - finalCount));
        System.out.println("耗时: " + (endTime - startTime) + "ms");

        if (finalCount == expectedCount) {
            System.out.println("✅ 结果正确");
        } else {
            System.out.println("❌ 结果错误 - 出现了原子性问题");
        }
    }

    /**
     * 演示i++的字节码
     */
    public static void explainIncrementBytecode() {
        System.out.println("\n========== i++的字节码分析 ==========");
        System.out.println("Java代码: count++");
        System.out.println("\n对应的字节码指令:");
        System.out.println("  1. getfield     // 读取count的值");
        System.out.println("  2. iconst_1     // 将常量1压入栈");
        System.out.println("  3. iadd         // 执行加法");
        System.out.println("  4. putfield     // 将结果写回count");
        System.out.println("\n这是一个 读-改-写 的复合操作，不是原子的！");
        System.out.println("\n多线程执行时可能的交错情况:");
        System.out.println("  线程A: 读取count=0");
        System.out.println("  线程B: 读取count=0");
        System.out.println("  线程A: 计算0+1=1");
        System.out.println("  线程B: 计算0+1=1");
        System.out.println("  线程A: 写入count=1");
        System.out.println("  线程B: 写入count=1");
        System.out.println("  结果: count=1 (期望是2，丢失了一次更新)");
        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           Java内存模型 - 原子性问题演示                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 先解释i++的字节码
        explainIncrementBytecode();

        // 测试1：没有同步 - 会出现原子性问题
        NoSyncCounter noSyncCounter = new NoSyncCounter();
        testCounter("1. 没有同步 (原子性问题)", 
                    noSyncCounter::increment, 
                    noSyncCounter::getCount);

        // 测试2：volatile不能保证原子性
        VolatileCounter volatileCounter = new VolatileCounter();
        testCounter("2. 使用volatile (仍有原子性问题)", 
                    volatileCounter::increment, 
                    volatileCounter::getCount);

        // 测试3：synchronized保证原子性
        SyncCounter syncCounter = new SyncCounter();
        testCounter("3. 使用synchronized (保证原子性)", 
                    syncCounter::increment, 
                    syncCounter::getCount);

        // 测试4：AtomicInteger保证原子性
        AtomicCounter atomicCounter = new AtomicCounter();
        testCounter("4. 使用AtomicInteger (保证原子性)", 
                    atomicCounter::increment, 
                    atomicCounter::getCount);

        System.out.println("\n" + "===========================");
        System.out.println("总结：");
        System.out.println("1. i++不是原子操作，多线程下会出现数据丢失");
        System.out.println("2. volatile只保证可见性，不保证原子性");
        System.out.println("3. synchronized保证原子性，但性能较低");
        System.out.println("4. AtomicInteger使用CAS保证原子性，性能较高");
        System.out.println("===========================");
    }
}
