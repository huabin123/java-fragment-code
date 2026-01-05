package com.fragment.juc.aqs.demo;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 共享锁演示
 *
 * 演示内容：
 * 1. 共享模式的基本流程
 * 2. 传播机制
 * 3. 与独占模式的区别
 *
 * @author huabin
 */
public class SharedLockDemo {

    /**
     * 演示1：简单的共享锁（信号量）
     */
    public static void demoSimpleSharedLock() throws InterruptedException {
        System.out.println("\n========== 演示1：简单的共享锁 ==========\n");

        class SimpleSharedLock {
            private final Sync sync;

            SimpleSharedLock(int permits) {
                sync = new Sync(permits);
            }

            class Sync extends AbstractQueuedSynchronizer {
                Sync(int permits) {
                    setState(permits);
                }

                @Override
                protected int tryAcquireShared(int arg) {
                    for (;;) {
                        int available = getState();
                        int remaining = available - arg;

                        if (remaining < 0) {
                            System.out.println("  [" + Thread.currentThread().getName() +
                                             "] 许可不足，进入队列");
                            return remaining;
                        }

                        if (compareAndSetState(available, remaining)) {
                            System.out.println("  [" + Thread.currentThread().getName() +
                                             "] 获取许可，剩余: " + remaining);
                            return remaining;
                        }
                    }
                }

                @Override
                protected boolean tryReleaseShared(int arg) {
                    for (;;) {
                        int current = getState();
                        int next = current + arg;

                        if (compareAndSetState(current, next)) {
                            System.out.println("  [" + Thread.currentThread().getName() +
                                             "] 释放许可，剩余: " + next);
                            return true;
                        }
                    }
                }

                int getPermits() {
                    return getState();
                }
            }

            public void acquire() {
                sync.acquireShared(1);
            }

            public void release() {
                sync.releaseShared(1);
            }

            public int availablePermits() {
                return sync.getPermits();
            }
        }

        SimpleSharedLock lock = new SimpleSharedLock(2);

        System.out.println("初始许可数: " + lock.availablePermits());
        System.out.println("\n3个线程竞争2个许可:\n");

        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                lock.acquire();
                try {
                    System.out.println("  [Thread-" + id + "] 执行任务");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.release();
                }
            }, "Thread-" + id).start();
            Thread.sleep(100);
        }

        Thread.sleep(5000);

        System.out.println("\n✅ 共享锁允许多个线程同时访问");
    }

    /**
     * 演示2：传播机制
     */
    public static void demoPropagation() throws InterruptedException {
        System.out.println("\n========== 演示2：传播机制 ==========\n");

        class PropagationDemo {
            private final Sync sync;

            PropagationDemo(int permits) {
                sync = new Sync(permits);
            }

            class Sync extends AbstractQueuedSynchronizer {
                Sync(int permits) {
                    setState(permits);
                }

                @Override
                protected int tryAcquireShared(int arg) {
                    for (;;) {
                        int available = getState();
                        int remaining = available - arg;

                        if (remaining < 0) {
                            return remaining;
                        }

                        if (compareAndSetState(available, remaining)) {
                            if (remaining > 0) {
                                System.out.println("  [" + Thread.currentThread().getName() +
                                                 "] 获取成功，剩余" + remaining + "，需要传播");
                            } else {
                                System.out.println("  [" + Thread.currentThread().getName() +
                                                 "] 获取成功，剩余0，不传播");
                            }
                            return remaining;
                        }
                    }
                }

                @Override
                protected boolean tryReleaseShared(int arg) {
                    for (;;) {
                        int current = getState();
                        int next = current + arg;

                        if (compareAndSetState(current, next)) {
                            System.out.println("  [" + Thread.currentThread().getName() +
                                             "] 释放" + arg + "个许可");
                            return true;
                        }
                    }
                }
            }

            public void acquire() {
                sync.acquireShared(1);
            }

            public void release(int permits) {
                sync.releaseShared(permits);
            }
        }

        PropagationDemo demo = new PropagationDemo(0);

        System.out.println("初始许可数: 0");
        System.out.println("4个线程等待许可:\n");

        // 4个线程等待
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            new Thread(() -> {
                System.out.println("  [Thread-" + id + "] 开始等待");
                demo.acquire();
                System.out.println("  [Thread-" + id + "] 获取成功");
            }, "Thread-" + id).start();
            Thread.sleep(100);
        }

        Thread.sleep(1000);

        System.out.println("\n释放3个许可，观察传播:\n");
        demo.release(3);

        Thread.sleep(2000);

        System.out.println("\n✅ 传播机制唤醒了多个等待线程");
    }

    /**
     * 演示3：CountDownLatch式的共享锁
     */
    public static void demoCountDownLatch() throws InterruptedException {
        System.out.println("\n========== 演示3：CountDownLatch式共享锁 ==========\n");

        class SimpleLatch {
            private final Sync sync;

            SimpleLatch(int count) {
                sync = new Sync(count);
            }

            class Sync extends AbstractQueuedSynchronizer {
                Sync(int count) {
                    setState(count);
                }

                @Override
                protected int tryAcquireShared(int arg) {
                    int state = getState();
                    if (state == 0) {
                        System.out.println("  [" + Thread.currentThread().getName() +
                                         "] 倒计时完成，通过");
                        return 1;
                    } else {
                        System.out.println("  [" + Thread.currentThread().getName() +
                                         "] 倒计时未完成，等待");
                        return -1;
                    }
                }

                @Override
                protected boolean tryReleaseShared(int arg) {
                    for (;;) {
                        int c = getState();
                        if (c == 0)
                            return false;

                        int nextc = c - 1;
                        if (compareAndSetState(c, nextc)) {
                            System.out.println("  [" + Thread.currentThread().getName() +
                                             "] countDown，剩余: " + nextc);
                            return nextc == 0;
                        }
                    }
                }

                int getCount() {
                    return getState();
                }
            }

            public void await() {
                sync.acquireShared(1);
            }

            public void countDown() {
                sync.releaseShared(1);
            }

            public int getCount() {
                return sync.getCount();
            }
        }

        SimpleLatch latch = new SimpleLatch(3);

        System.out.println("初始计数: " + latch.getCount());
        System.out.println("\n主线程等待:\n");

        // 主线程等待
        new Thread(() -> {
            System.out.println("  [Main] 开始等待");
            latch.await();
            System.out.println("  [Main] 等待结束");
        }, "Main").start();

        Thread.sleep(500);

        // 3个工作线程
        System.out.println("\n工作线程执行:\n");
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    Thread.sleep(id * 500);
                    System.out.println("  [Worker-" + id + "] 完成任务");
                    latch.countDown();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "Worker-" + id).start();
        }

        Thread.sleep(3000);

        System.out.println("\n✅ 倒计时到0唤醒所有等待线程");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 共享锁总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 多个线程可以同时获取");
        System.out.println("   2. 支持传播机制");
        System.out.println("   3. tryAcquireShared返回int");

        System.out.println("\n📊 返回值含义:");
        System.out.println("   < 0  - 获取失败，进入队列");
        System.out.println("   = 0  - 获取成功，资源用完，不传播");
        System.out.println("   > 0  - 获取成功，还有剩余，传播");

        System.out.println("\n💡 vs 独占模式:");
        System.out.println("   独占模式:");
        System.out.println("     - 同时只有1个线程");
        System.out.println("     - 返回boolean");
        System.out.println("     - 无传播");
        System.out.println("   共享模式:");
        System.out.println("     - 同时可有多个线程");
        System.out.println("     - 返回int");
        System.out.println("     - 有传播");

        System.out.println("\n🔄 典型应用:");
        System.out.println("   ✅ Semaphore - 信号量");
        System.out.println("   ✅ CountDownLatch - 倒计时门栓");
        System.out.println("   ✅ ReadLock - 读锁");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            共享锁演示                                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：简单共享锁
        demoSimpleSharedLock();

        // 演示2：传播机制
        demoPropagation();

        // 演示3：CountDownLatch
        demoCountDownLatch();

        // 总结
        summarize();

        System.out.println("\n===========================");
        System.out.println("核心要点：");
        System.out.println("1. 共享模式允许多个线程同时获取资源");
        System.out.println("2. 传播机制是共享模式的核心");
        System.out.println("3. 返回值控制是否传播");
        System.out.println("4. 是Semaphore和CountDownLatch的基础");
        System.out.println("===========================");
    }
}
