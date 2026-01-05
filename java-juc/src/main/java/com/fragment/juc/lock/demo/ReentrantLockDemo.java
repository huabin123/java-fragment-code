package com.fragment.juc.lock.demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReentrantLock演示
 * 
 * 演示内容：
 * 1. ReentrantLock基本使用
 * 2. tryLock()非阻塞获取锁
 * 3. lockInterruptibly()可中断获取锁
 * 4. 公平锁vs非公平锁
 * 5. 可重入性验证
 * 
 * @author huabin
 */
public class ReentrantLockDemo {

    /**
     * 演示1：基本使用
     */
    public static void demoBasicUsage() {
        System.out.println("\n========== 演示1：ReentrantLock基本使用 ==========\n");

        Lock lock = new ReentrantLock();
        
        // 标准使用模式
        lock.lock();
        try {
            System.out.println("[" + Thread.currentThread().getName() + "] 获取到锁");
            System.out.println("[" + Thread.currentThread().getName() + "] 执行业务逻辑...");
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("[" + Thread.currentThread().getName() + "] 释放锁");
            lock.unlock();
        }

        System.out.println("\n✅ 标准使用模式：lock() + try-finally + unlock()");
    }

    /**
     * 演示2：tryLock()非阻塞获取锁
     */
    public static void demoTryLock() throws InterruptedException {
        System.out.println("\n========== 演示2：tryLock()非阻塞获取锁 ==========\n");

        Lock lock = new ReentrantLock();

        // 线程1：持有锁2秒
        Thread thread1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[线程1] 获取到锁，持有2秒");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("[线程1] 释放锁");
                lock.unlock();
            }
        }, "Thread-1");

        // 线程2：尝试获取锁（立即返回）
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保线程1先获取锁
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("[线程2] 尝试获取锁（不等待）...");
            if (lock.tryLock()) {
                try {
                    System.out.println("[线程2] 获取锁成功");
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("[线程2] 获取锁失败，立即返回");
            }
        }, "Thread-2");

        // 线程3：尝试获取锁（等待1秒）
        Thread thread3 = new Thread(() -> {
            try {
                Thread.sleep(500); // 确保线程1先获取锁
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("[线程3] 尝试获取锁（最多等待1秒）...");
            try {
                if (lock.tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("[线程3] 获取锁成功");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("[线程3] 等待1秒后仍未获取到锁");
                }
            } catch (InterruptedException e) {
                System.out.println("[线程3] 被中断");
            }
        }, "Thread-3");

        thread1.start();
        thread2.start();
        thread3.start();

        thread1.join();
        thread2.join();
        thread3.join();

        System.out.println("\n✅ tryLock()可以避免无限期等待");
    }

    /**
     * 演示3：lockInterruptibly()可中断获取锁
     */
    public static void demoLockInterruptibly() throws InterruptedException {
        System.out.println("\n========== 演示3：lockInterruptibly()可中断 ==========\n");

        Lock lock = new ReentrantLock();

        // 线程1：持有锁5秒
        Thread thread1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("[线程1] 获取到锁，持有5秒");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("[线程1] 释放锁");
                lock.unlock();
            }
        }, "Thread-1");

        // 线程2：可中断地等待锁
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(100); // 确保线程1先获取锁
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("[线程2] 尝试获取锁（可中断）...");
            try {
                lock.lockInterruptibly();
                try {
                    System.out.println("[线程2] 获取锁成功");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("[线程2] 等待锁时被中断");
            }
        }, "Thread-2");

        thread1.start();
        thread2.start();

        // 等待1秒后中断线程2
        Thread.sleep(1000);
        System.out.println("[Main] 中断线程2");
        thread2.interrupt();

        thread1.join();
        thread2.join();

        System.out.println("\n✅ lockInterruptibly()支持中断，避免死等");
    }

    /**
     * 演示4：公平锁vs非公平锁
     */
    public static void demoFairVsNonfair() throws InterruptedException {
        System.out.println("\n========== 演示4：公平锁vs非公平锁 ==========\n");

        // 非公平锁
        System.out.println("非公平锁（默认）:");
        testLockFairness(new ReentrantLock(false));

        Thread.sleep(500);

        // 公平锁
        System.out.println("\n公平锁:");
        testLockFairness(new ReentrantLock(true));

        System.out.println("\n📊 对比:");
        System.out.println("  非公平锁：性能高，但可能导致线程饥饿");
        System.out.println("  公平锁：  保证FIFO，但性能略低");
    }

    private static void testLockFairness(Lock lock) throws InterruptedException {
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 2; j++) {
                    lock.lock();
                    try {
                        System.out.println("  [线程" + threadId + "] 获取到锁");
                    } finally {
                        lock.unlock();
                    }
                }
            }, "Thread-" + i);
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

    /**
     * 演示5：可重入性验证
     */
    public static void demoReentrant() {
        System.out.println("\n========== 演示5：可重入性验证 ==========\n");

        ReentrantLock lock = new ReentrantLock();

        class ReentrantExample {
            public void method1() {
                lock.lock();
                try {
                    System.out.println("[method1] 获取锁，持有数: " + lock.getHoldCount());
                    method2(); // 重入
                } finally {
                    System.out.println("[method1] 释放锁，持有数: " + (lock.getHoldCount() - 1));
                    lock.unlock();
                }
            }

            public void method2() {
                lock.lock();
                try {
                    System.out.println("[method2] 获取锁（重入），持有数: " + lock.getHoldCount());
                    method3(); // 再次重入
                } finally {
                    System.out.println("[method2] 释放锁，持有数: " + (lock.getHoldCount() - 1));
                    lock.unlock();
                }
            }

            public void method3() {
                lock.lock();
                try {
                    System.out.println("[method3] 获取锁（再次重入），持有数: " + lock.getHoldCount());
                } finally {
                    System.out.println("[method3] 释放锁，持有数: " + (lock.getHoldCount() - 1));
                    lock.unlock();
                }
            }
        }

        ReentrantExample example = new ReentrantExample();
        example.method1();

        System.out.println("\n✅ 同一线程可以多次获取同一把锁（可重入）");
    }

    /**
     * 演示6：Lock vs synchronized对比
     */
    public static void compareLockAndSynchronized() throws InterruptedException {
        System.out.println("\n========== 演示6：Lock vs synchronized对比 ==========\n");

        final int threadCount = 10;
        final int operations = 100000;

        // 测试Lock
        System.out.println("测试ReentrantLock...");
        Lock lock = new ReentrantLock();
        long[] lockCounter = {0};
        long lockStartTime = System.currentTimeMillis();

        Thread[] lockThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            lockThreads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    lock.lock();
                    try {
                        lockCounter[0]++;
                    } finally {
                        lock.unlock();
                    }
                }
            });
            lockThreads[i].start();
        }

        for (Thread thread : lockThreads) {
            thread.join();
        }
        long lockTime = System.currentTimeMillis() - lockStartTime;

        // 测试synchronized
        System.out.println("测试synchronized...");
        long[] syncCounter = {0};
        Object syncLock = new Object();
        long syncStartTime = System.currentTimeMillis();

        Thread[] syncThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            syncThreads[i] = new Thread(() -> {
                for (int j = 0; j < operations; j++) {
                    synchronized (syncLock) {
                        syncCounter[0]++;
                    }
                }
            });
            syncThreads[i].start();
        }

        for (Thread thread : syncThreads) {
            thread.join();
        }
        long syncTime = System.currentTimeMillis() - syncStartTime;

        // 输出对比
        System.out.println("\n性能对比:");
        System.out.println("  ReentrantLock: " + lockTime + "ms, 结果: " + lockCounter[0]);
        System.out.println("  synchronized:  " + syncTime + "ms, 结果: " + syncCounter[0]);
        System.out.println("  性能差异: " + String.format("%.2f%%", 
            Math.abs(lockTime - syncTime) * 100.0 / Math.max(lockTime, syncTime)));

        System.out.println("\n📊 特性对比:");
        System.out.println("  ┌─────────────────┬──────────────┬──────────────┐");
        System.out.println("  │     特性        │ ReentrantLock│ synchronized │");
        System.out.println("  ├─────────────────┼──────────────┼──────────────┤");
        System.out.println("  │ 使用方式        │   显式调用   │    关键字    │");
        System.out.println("  │ 锁释放          │   手动释放   │    自动释放  │");
        System.out.println("  │ 可中断          │     支持     │    不支持    │");
        System.out.println("  │ 超时获取        │     支持     │    不支持    │");
        System.out.println("  │ 公平性          │   可选择     │    非公平    │");
        System.out.println("  │ 条件队列        │     多个     │     单个     │");
        System.out.println("  │ 性能            │     略高     │     相近     │");
        System.out.println("  └─────────────────┴──────────────┴──────────────┘");
    }

    /**
     * 演示7：避免死锁
     */
    public static void demoAvoidDeadlock() throws InterruptedException {
        System.out.println("\n========== 演示7：使用tryLock避免死锁 ==========\n");

        Lock lock1 = new ReentrantLock();
        Lock lock2 = new ReentrantLock();

        // 线程1：lock1 -> lock2
        Thread thread1 = new Thread(() -> {
            try {
                while (true) {
                    if (lock1.tryLock(50, TimeUnit.MILLISECONDS)) {
                        try {
                            System.out.println("[线程1] 获取lock1");
                            Thread.sleep(50);
                            
                            if (lock2.tryLock(50, TimeUnit.MILLISECONDS)) {
                                try {
                                    System.out.println("[线程1] 获取lock2，执行业务");
                                    break; // 成功，退出循环
                                } finally {
                                    lock2.unlock();
                                }
                            } else {
                                System.out.println("[线程1] 获取lock2失败，释放lock1重试");
                            }
                        } finally {
                            lock1.unlock();
                        }
                    }
                    Thread.sleep(10); // 短暂休眠后重试
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-1");

        // 线程2：lock2 -> lock1
        Thread thread2 = new Thread(() -> {
            try {
                while (true) {
                    if (lock2.tryLock(50, TimeUnit.MILLISECONDS)) {
                        try {
                            System.out.println("[线程2] 获取lock2");
                            Thread.sleep(50);
                            
                            if (lock1.tryLock(50, TimeUnit.MILLISECONDS)) {
                                try {
                                    System.out.println("[线程2] 获取lock1，执行业务");
                                    break; // 成功，退出循环
                                } finally {
                                    lock1.unlock();
                                }
                            } else {
                                System.out.println("[线程2] 获取lock1失败，释放lock2重试");
                            }
                        } finally {
                            lock2.unlock();
                        }
                    }
                    Thread.sleep(10); // 短暂休眠后重试
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("\n✅ 使用tryLock()可以避免死锁");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== ReentrantLock总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 可重入：同一线程可多次获取同一把锁");
        System.out.println("   2. 可中断：lockInterruptibly()支持中断");
        System.out.println("   3. 可超时：tryLock(timeout)支持超时");
        System.out.println("   4. 公平性：可选择公平锁或非公平锁");
        System.out.println("   5. 多条件：支持多个Condition");

        System.out.println("\n⚠️  使用注意:");
        System.out.println("   1. 必须在finally中unlock()");
        System.out.println("   2. lock()和unlock()必须配对");
        System.out.println("   3. 避免在lock()和unlock()之间return");
        System.out.println("   4. 使用tryLock()避免死锁");

        System.out.println("\n📊 选择建议:");
        System.out.println("   使用synchronized的场景:");
        System.out.println("     - 简单的同步需求");
        System.out.println("     - 不需要高级特性");
        System.out.println("     - 代码简洁性优先");
        
        System.out.println("\n   使用ReentrantLock的场景:");
        System.out.println("     - 需要可中断的锁获取");
        System.out.println("     - 需要超时获取锁");
        System.out.println("     - 需要公平锁");
        System.out.println("     - 需要多个条件队列");
        System.out.println("     - 需要tryLock()避免死锁");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              ReentrantLock演示                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：tryLock
        demoTryLock();

        // 演示3：lockInterruptibly
        demoLockInterruptibly();

        // 演示4：公平锁vs非公平锁
        demoFairVsNonfair();

        // 演示5：可重入性
        demoReentrant();

        // 演示6：性能对比
        compareLockAndSynchronized();

        // 演示7：避免死锁
        demoAvoidDeadlock();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. ReentrantLock提供了比synchronized更灵活的锁机制");
        System.out.println("2. 必须在finally中释放锁");
        System.out.println("3. tryLock()可以避免死锁");
        System.out.println("4. lockInterruptibly()支持中断");
        System.out.println("5. 根据场景选择合适的锁");
        System.out.println("===========================");
    }
}
