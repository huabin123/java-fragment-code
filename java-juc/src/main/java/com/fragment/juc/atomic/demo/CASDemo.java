package com.fragment.juc.atomic.demo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * CAS(Compare And Swap)操作演示
 * 
 * 演示内容：
 * 1. CAS的基本原理
 * 2. ABA问题演示
 * 3. AtomicStampedReference解决ABA问题
 * 4. CAS的性能特点
 * 
 * @author huabin
 */
public class CASDemo {

    /**
     * 演示1：CAS基本操作
     */
    public static void demoBasicCAS() {
        System.out.println("\n========== 演示1：CAS基本操作 ==========\n");

        AtomicInteger atomicInt = new AtomicInteger(100);
        System.out.println("初始值: " + atomicInt.get());

        // CAS操作：期望值是100，更新为200
        boolean success1 = atomicInt.compareAndSet(100, 200);
        System.out.println("\nCAS(100 -> 200): " + success1);
        System.out.println("当前值: " + atomicInt.get());

        // CAS操作：期望值是100，更新为300（会失败，因为当前值是200）
        boolean success2 = atomicInt.compareAndSet(100, 300);
        System.out.println("\nCAS(100 -> 300): " + success2 + " (失败，因为当前值不是100)");
        System.out.println("当前值: " + atomicInt.get());

        // CAS操作：期望值是200，更新为300（会成功）
        boolean success3 = atomicInt.compareAndSet(200, 300);
        System.out.println("\nCAS(200 -> 300): " + success3);
        System.out.println("当前值: " + atomicInt.get());

        System.out.println("\n✅ CAS的核心思想：");
        System.out.println("   只有当前值等于期望值时，才会更新为新值");
        System.out.println("   这是一个原子操作，由CPU指令保证");
    }

    /**
     * 演示2：ABA问题
     */
    public static void demoABAProblem() throws InterruptedException {
        System.out.println("\n========== 演示2：ABA问题 ==========\n");

        AtomicInteger atomicInt = new AtomicInteger(100);

        // 线程1：期望100，想改为200，但会延迟执行
        Thread thread1 = new Thread(() -> {
            int expect = atomicInt.get();
            System.out.println("[线程1] 读取到值: " + expect);
            System.out.println("[线程1] 准备将 " + expect + " 改为 200，但先休眠1秒...");
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            boolean success = atomicInt.compareAndSet(expect, 200);
            System.out.println("[线程1] CAS操作" + (success ? "成功" : "失败") + 
                             "，当前值: " + atomicInt.get());
        }, "Thread-1");

        // 线程2：将100改为50，再改回100
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保线程1先读取
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("[线程2] 将值从 " + atomicInt.get() + " 改为 50");
            atomicInt.compareAndSet(100, 50);
            System.out.println("[线程2] 当前值: " + atomicInt.get());

            System.out.println("[线程2] 将值从 " + atomicInt.get() + " 改回 100");
            atomicInt.compareAndSet(50, 100);
            System.out.println("[线程2] 当前值: " + atomicInt.get());
        }, "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("\n⚠️  ABA问题分析：");
        System.out.println("   线程1读取到100，准备改为200");
        System.out.println("   线程2将100改为50，再改回100");
        System.out.println("   线程1的CAS操作成功了，但中间状态被忽略了");
        System.out.println("   这在某些场景下可能导致问题（如栈操作）");
    }

    /**
     * 演示3：使用AtomicStampedReference解决ABA问题
     */
    public static void demoSolveABA() throws InterruptedException {
        System.out.println("\n========== 演示3：使用版本号解决ABA问题 ==========\n");

        // 初始值100，版本号0
        AtomicStampedReference<Integer> stampedRef = 
            new AtomicStampedReference<>(100, 0);

        // 线程1：期望值100版本0，想改为200版本1，但会延迟执行
        Thread thread1 = new Thread(() -> {
            int expect = stampedRef.getReference();
            int stamp = stampedRef.getStamp();
            System.out.println("[线程1] 读取到值: " + expect + ", 版本号: " + stamp);
            System.out.println("[线程1] 准备将 " + expect + " 改为 200，但先休眠1秒...");
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            boolean success = stampedRef.compareAndSet(expect, 200, stamp, stamp + 1);
            System.out.println("[线程1] CAS操作" + (success ? "成功" : "失败") + 
                             "，当前值: " + stampedRef.getReference() + 
                             ", 版本号: " + stampedRef.getStamp());
            
            if (!success) {
                System.out.println("[线程1] 失败原因：版本号已经改变，检测到了中间状态的变化");
            }
        }, "Thread-1");

        // 线程2：将100改为50，再改回100，但版本号会递增
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保线程1先读取
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int value = stampedRef.getReference();
            int stamp = stampedRef.getStamp();
            System.out.println("[线程2] 将值从 " + value + " 改为 50，版本号从 " + 
                             stamp + " 改为 " + (stamp + 1));
            stampedRef.compareAndSet(value, 50, stamp, stamp + 1);
            System.out.println("[线程2] 当前值: " + stampedRef.getReference() + 
                             ", 版本号: " + stampedRef.getStamp());

            value = stampedRef.getReference();
            stamp = stampedRef.getStamp();
            System.out.println("[线程2] 将值从 " + value + " 改回 100，版本号从 " + 
                             stamp + " 改为 " + (stamp + 1));
            stampedRef.compareAndSet(value, 100, stamp, stamp + 1);
            System.out.println("[线程2] 当前值: " + stampedRef.getReference() + 
                             ", 版本号: " + stampedRef.getStamp());
        }, "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("\n✅ 解决方案：");
        System.out.println("   使用版本号（或时间戳）来标记每次修改");
        System.out.println("   即使值相同，但版本号不同，CAS也会失败");
        System.out.println("   这样就能检测到中间状态的变化");
    }

    /**
     * 演示4：CAS的自旋特性
     */
    public static void demoCASSpinning() throws InterruptedException {
        System.out.println("\n========== 演示4：CAS的自旋特性 ==========\n");

        AtomicInteger counter = new AtomicInteger(0);
        final int threadCount = 5;
        final int incrementPerThread = 1000;

        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementPerThread; j++) {
                    // 使用CAS自旋
                    int oldValue;
                    int newValue;
                    do {
                        oldValue = counter.get();
                        newValue = oldValue + 1;
                        // 如果CAS失败，会继续循环重试（自旋）
                    } while (!counter.compareAndSet(oldValue, newValue));
                }
            }, "Thread-" + i);
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("预期结果: " + (threadCount * incrementPerThread));
        System.out.println("实际结果: " + counter.get());
        System.out.println("✅ CAS通过自旋保证了原子性");

        System.out.println("\n⚠️  CAS的特点：");
        System.out.println("   优点：");
        System.out.println("     - 无锁，避免线程阻塞");
        System.out.println("     - 性能好（低竞争场景）");
        System.out.println("   缺点：");
        System.out.println("     - 自旋消耗CPU（高竞争场景）");
        System.out.println("     - 只能保证单个变量的原子性");
        System.out.println("     - 可能出现ABA问题");
    }

    /**
     * 演示5：CAS vs synchronized性能对比
     */
    public static void comparePerformance() throws InterruptedException {
        System.out.println("\n========== 演示5：CAS vs synchronized性能对比 ==========\n");

        final int threadCount = 10;
        final int operations = 100000;

        // 测试1：使用CAS
        AtomicInteger casCounter = new AtomicInteger(0);
        long casStartTime = System.currentTimeMillis();

        Thread[] casThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            casThreads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    casCounter.incrementAndGet();
                }
            });
            casThreads[i].start();
        }

        for (Thread thread : casThreads) {
            thread.join();
        }

        long casEndTime = System.currentTimeMillis();
        long casTime = casEndTime - casStartTime;

        // 测试2：使用synchronized
        final int[] syncCounter = {0};
        long syncStartTime = System.currentTimeMillis();

        Thread[] syncThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            syncThreads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    synchronized (syncCounter) {
                        syncCounter[0]++;
                    }
                }
            });
            syncThreads[i].start();
        }

        for (Thread thread : syncThreads) {
            thread.join();
        }

        long syncEndTime = System.currentTimeMillis();
        long syncTime = syncEndTime - syncStartTime;

        System.out.println("CAS方式:");
        System.out.println("  结果: " + casCounter.get());
        System.out.println("  耗时: " + casTime + "ms");

        System.out.println("\nsynchronized方式:");
        System.out.println("  结果: " + syncCounter[0]);
        System.out.println("  耗时: " + syncTime + "ms");

        System.out.println("\n性能对比:");
        System.out.println("  CAS比synchronized快 " + 
                         String.format("%.2f", (double)syncTime / casTime) + " 倍");

        System.out.println("\n📊 性能分析：");
        System.out.println("   低竞争场景：CAS性能远超synchronized");
        System.out.println("   高竞争场景：CAS自旋消耗CPU，性能可能下降");
        System.out.println("   建议：根据实际场景选择合适的同步机制");
    }

    /**
     * 解释CAS的底层实现
     */
    public static void explainCASImplementation() {
        System.out.println("\n========== CAS的底层实现 ==========");
        
        System.out.println("\nJava层面（AtomicInteger）：");
        System.out.println("  public final boolean compareAndSet(int expect, int update) {");
        System.out.println("      return unsafe.compareAndSwapInt(this, valueOffset, expect, update);");
        System.out.println("  }");
        
        System.out.println("\nUnsafe层面（native方法）：");
        System.out.println("  public final native boolean compareAndSwapInt(");
        System.out.println("      Object o, long offset, int expected, int x);");
        
        System.out.println("\nCPU层面（x86）：");
        System.out.println("  LOCK CMPXCHG 指令");
        System.out.println("    - LOCK前缀：锁定总线或缓存行");
        System.out.println("    - CMPXCHG：比较并交换");
        
        System.out.println("\n执行过程：");
        System.out.println("  1. 读取内存位置V的值");
        System.out.println("  2. 比较V的值是否等于期望值A");
        System.out.println("  3. 如果相等，将V的值更新为新值B");
        System.out.println("  4. 返回操作是否成功");
        System.out.println("  5. 整个过程是原子的（CPU指令级别）");
        
        System.out.println("\n为什么CAS是原子的？");
        System.out.println("  - CPU的CMPXCHG指令本身是原子的");
        System.out.println("  - LOCK前缀保证了多核环境下的原子性");
        System.out.println("  - 通过缓存一致性协议（MESI）保证可见性");
        
        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              CAS(Compare And Swap)操作演示                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：CAS基本操作
        demoBasicCAS();

        // 演示2：ABA问题
        demoABAProblem();

        // 演示3：解决ABA问题
        demoSolveABA();

        // 演示4：CAS的自旋特性
        demoCASSpinning();

        // 演示5：性能对比
        comparePerformance();

        // 解释底层实现
        explainCASImplementation();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. CAS是一种无锁的原子操作");
        System.out.println("2. CAS通过CPU指令保证原子性");
        System.out.println("3. CAS失败时会自旋重试");
        System.out.println("4. ABA问题可以通过版本号解决");
        System.out.println("5. 低竞争场景下CAS性能优于synchronized");
        System.out.println("===========================");
    }
}
