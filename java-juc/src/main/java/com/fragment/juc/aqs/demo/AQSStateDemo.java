package com.fragment.juc.aqs.demo;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * AQS状态演示
 * 
 * 演示内容：
 * 1. state的三种操作
 * 2. state在不同同步器中的含义
 * 3. CAS操作的原子性
 * 
 * @author huabin
 */
public class AQSStateDemo {

    /**
     * 演示1：state的三种操作
     */
    public static void demoStateOperations() {
        System.out.println("\n========== 演示1：state的三种操作 ==========\n");

        class SimpleSync extends AbstractQueuedSynchronizer {
            // 1. getState() - 读取状态
            public int getValue() {
                return getState();
            }

            // 2. setState() - 设置状态
            public void setValue(int value) {
                setState(value);
            }

            // 3. compareAndSetState() - CAS更新
            public boolean casValue(int expect, int update) {
                return compareAndSetState(expect, update);
            }
        }

        SimpleSync sync = new SimpleSync();

        System.out.println("1. getState():");
        System.out.println("  初始值: " + sync.getValue());

        System.out.println("\n2. setState():");
        sync.setValue(10);
        System.out.println("  设置为10: " + sync.getValue());

        System.out.println("\n3. compareAndSetState():");
        boolean success1 = sync.casValue(10, 20);
        System.out.println("  CAS(10->20): " + success1 + ", 当前值: " + sync.getValue());

        boolean success2 = sync.casValue(10, 30);
        System.out.println("  CAS(10->30): " + success2 + ", 当前值: " + sync.getValue());

        System.out.println("\n✅ state提供了三种操作方式");
    }

    /**
     * 演示2：模拟ReentrantLock的state
     */
    public static void demoReentrantLockState() {
        System.out.println("\n========== 演示2：ReentrantLock的state ==========\n");

        class LockSync extends AbstractQueuedSynchronizer {
            public boolean tryLock() {
                int c = getState();
                if (c == 0) {
                    if (compareAndSetState(0, 1)) {
                        setExclusiveOwnerThread(Thread.currentThread());
                        System.out.println("  获取锁成功，state: 0 -> 1");
                        return true;
                    }
                } else if (Thread.currentThread() == getExclusiveOwnerThread()) {
                    setState(c + 1);
                    System.out.println("  重入锁，state: " + c + " -> " + (c + 1));
                    return true;
                }
                return false;
            }

            public void unlock() {
                int c = getState() - 1;
                if (c == 0) {
                    setExclusiveOwnerThread(null);
                    System.out.println("  完全释放，state: 1 -> 0");
                } else {
                    System.out.println("  部分释放，state: " + (c + 1) + " -> " + c);
                }
                setState(c);
            }

            public int getHoldCount() {
                return getState();
            }
        }

        LockSync lock = new LockSync();

        System.out.println("state含义：重入次数");
        System.out.println("初始state: " + lock.getHoldCount());

        System.out.println("\n第1次加锁:");
        lock.tryLock();

        System.out.println("\n第2次加锁（重入）:");
        lock.tryLock();

        System.out.println("\n第3次加锁（重入）:");
        lock.tryLock();

        System.out.println("\n第1次解锁:");
        lock.unlock();

        System.out.println("\n第2次解锁:");
        lock.unlock();

        System.out.println("\n第3次解锁:");
        lock.unlock();

        System.out.println("\n✅ state表示重入次数");
    }

    /**
     * 演示3：模拟Semaphore的state
     */
    public static void demoSemaphoreState() {
        System.out.println("\n========== 演示3：Semaphore的state ==========\n");

        class SemaphoreSync extends AbstractQueuedSynchronizer {
            SemaphoreSync(int permits) {
                setState(permits);
            }

            public boolean tryAcquire() {
                for (;;) {
                    int available = getState();
                    int remaining = available - 1;

                    if (remaining < 0) {
                        System.out.println("  获取失败，许可不足，state: " + available);
                        return false;
                    }

                    if (compareAndSetState(available, remaining)) {
                        System.out.println("  获取成功，state: " + available + " -> " + remaining);
                        return true;
                    }
                }
            }

            public void release() {
                for (;;) {
                    int current = getState();
                    int next = current + 1;

                    if (compareAndSetState(current, next)) {
                        System.out.println("  释放成功，state: " + current + " -> " + next);
                        return;
                    }
                }
            }

            public int getPermits() {
                return getState();
            }
        }

        SemaphoreSync semaphore = new SemaphoreSync(3);

        System.out.println("state含义：可用许可数");
        System.out.println("初始state: " + semaphore.getPermits());

        System.out.println("\n获取许可1:");
        semaphore.tryAcquire();

        System.out.println("\n获取许可2:");
        semaphore.tryAcquire();

        System.out.println("\n获取许可3:");
        semaphore.tryAcquire();

        System.out.println("\n获取许可4（失败）:");
        semaphore.tryAcquire();

        System.out.println("\n释放许可:");
        semaphore.release();

        System.out.println("\n再次获取许可:");
        semaphore.tryAcquire();

        System.out.println("\n✅ state表示可用许可数");
    }

    /**
     * 演示4：模拟CountDownLatch的state
     */
    public static void demoCountDownLatchState() {
        System.out.println("\n========== 演示4：CountDownLatch的state ==========\n");

        class LatchSync extends AbstractQueuedSynchronizer {
            LatchSync(int count) {
                setState(count);
            }

            public boolean isReady() {
                return getState() == 0;
            }

            public void countDown() {
                for (;;) {
                    int c = getState();
                    if (c == 0) {
                        System.out.println("  已经是0，无需倒计时");
                        return;
                    }

                    int nextc = c - 1;
                    if (compareAndSetState(c, nextc)) {
                        System.out.println("  倒计时，state: " + c + " -> " + nextc);
                        if (nextc == 0) {
                            System.out.println("  ✅ 倒计时完成！");
                        }
                        return;
                    }
                }
            }

            public int getCount() {
                return getState();
            }
        }

        LatchSync latch = new LatchSync(3);

        System.out.println("state含义：倒计时数量");
        System.out.println("初始state: " + latch.getCount());

        System.out.println("\n第1次countDown:");
        latch.countDown();

        System.out.println("\n第2次countDown:");
        latch.countDown();

        System.out.println("\n第3次countDown:");
        latch.countDown();

        System.out.println("\n第4次countDown:");
        latch.countDown();

        System.out.println("\n✅ state表示倒计时数量");
    }

    /**
     * 演示5：CAS的原子性
     */
    public static void demoCASAtomicity() throws InterruptedException {
        System.out.println("\n========== 演示5：CAS的原子性 ==========\n");

        class Counter extends AbstractQueuedSynchronizer {
            public void increment() {
                for (;;) {
                    int current = getState();
                    int next = current + 1;
                    if (compareAndSetState(current, next)) {
                        return;
                    }
                }
            }

            public int getValue() {
                return getState();
            }
        }

        Counter counter = new Counter();
        int threadCount = 10;
        int incrementsPerThread = 1000;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    counter.increment();
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("线程数: " + threadCount);
        System.out.println("每线程递增: " + incrementsPerThread);
        System.out.println("预期值: " + (threadCount * incrementsPerThread));
        System.out.println("实际值: " + counter.getValue());

        System.out.println("\n✅ CAS保证了原子性");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== state总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. volatile int：保证可见性");
        System.out.println("   2. 三种操作：get、set、CAS");
        System.out.println("   3. 含义由子类定义");
        System.out.println("   4. 可拆分使用（如ReadWriteLock）");

        System.out.println("\n📊 不同同步器的state含义:");
        System.out.println("   ReentrantLock:       重入次数");
        System.out.println("   Semaphore:           可用许可数");
        System.out.println("   CountDownLatch:      倒计时数量");
        System.out.println("   ReadWriteLock:       高16位读锁，低16位写锁");

        System.out.println("\n💡 设计优势:");
        System.out.println("   ✅ 灵活：一个int表示多种含义");
        System.out.println("   ✅ 高效：CAS性能好");
        System.out.println("   ✅ 简洁：避免复杂的状态对象");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            AQS状态演示                                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：state操作
        demoStateOperations();

        // 演示2：ReentrantLock
        demoReentrantLockState();

        // 演示3：Semaphore
        demoSemaphoreState();

        // 演示4：CountDownLatch
        demoCountDownLatchState();

        // 演示5：CAS原子性
        demoCASAtomicity();

        // 总结
        summarize();

        System.out.println("\n===========================");
        System.out.println("核心要点：");
        System.out.println("1. state是AQS的核心，表示同步状态");
        System.out.println("2. 不同同步器赋予state不同的含义");
        System.out.println("3. CAS保证了state更新的原子性");
        System.out.println("4. volatile保证了state的可见性");
        System.out.println("===========================");
    }
}
