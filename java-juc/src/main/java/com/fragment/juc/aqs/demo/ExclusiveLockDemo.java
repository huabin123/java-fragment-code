package com.fragment.juc.aqs.demo;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 独占锁演示
 * 
 * 演示内容：
 * 1. 独占模式的基本流程
 * 2. 队列的形成过程
 * 3. 阻塞和唤醒机制
 * 
 * @author huabin
 */
public class ExclusiveLockDemo {

    /**
     * 简单的独占锁
     */
    static class SimpleLock {
        private final Sync sync = new Sync();

        static class Sync extends AbstractQueuedSynchronizer {
            @Override
            protected boolean tryAcquire(int arg) {
                if (compareAndSetState(0, 1)) {
                    setExclusiveOwnerThread(Thread.currentThread());
                    System.out.println("  [" + Thread.currentThread().getName() + "] 获取锁成功");
                    return true;
                }
                System.out.println("  [" + Thread.currentThread().getName() + "] 获取锁失败，进入队列");
                return false;
            }

            @Override
            protected boolean tryRelease(int arg) {
                if (getState() == 0)
                    throw new IllegalMonitorStateException();
                setExclusiveOwnerThread(null);
                setState(0);
                System.out.println("  [" + Thread.currentThread().getName() + "] 释放锁");
                return true;
            }

            @Override
            protected boolean isHeldExclusively() {
                return getState() == 1;
            }
        }

        public void lock() {
            sync.acquire(1);
        }

        public void unlock() {
            sync.release(1);
        }

        public boolean isLocked() {
            return sync.getState() == 1;
        }
    }

    /**
     * 演示1：基本的加锁解锁
     */
    public static void demoBasicLock() throws InterruptedException {
        System.out.println("\n========== 演示1：基本加锁解锁 ==========\n");

        SimpleLock lock = new SimpleLock();

        System.out.println("初始状态: isLocked=" + lock.isLocked());

        System.out.println("\n加锁:");
        lock.lock();
        System.out.println("加锁后: isLocked=" + lock.isLocked());

        System.out.println("\n解锁:");
        lock.unlock();
        System.out.println("解锁后: isLocked=" + lock.isLocked());

        System.out.println("\n✅ 基本的加锁解锁流程");
    }

    /**
     * 演示2：多线程竞争
     */
    public static void demoMultiThreadCompetition() throws InterruptedException {
        System.out.println("\n========== 演示2：多线程竞争 ==========\n");

        SimpleLock lock = new SimpleLock();

        System.out.println("3个线程竞争锁:\n");

        Thread t1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  [Thread-1] 持有锁，工作2秒");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  [Thread-2] 持有锁，工作2秒");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Thread-2");

        Thread t3 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("  [Thread-3] 持有锁，工作2秒");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Thread-3");

        t1.start();
        Thread.sleep(100);
        t2.start();
        Thread.sleep(100);
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        System.out.println("\n✅ 独占锁保证了互斥访问");
    }

    /**
     * 演示3：可重入锁
     */
    public static void demoReentrantLock() {
        System.out.println("\n========== 演示3：可重入锁 ==========\n");

        class ReentrantLock {
            private final Sync sync = new Sync();

            static class Sync extends AbstractQueuedSynchronizer {
                @Override
                protected boolean tryAcquire(int arg) {
                    Thread current = Thread.currentThread();
                    int c = getState();

                    if (c == 0) {
                        if (compareAndSetState(0, arg)) {
                            setExclusiveOwnerThread(current);
                            System.out.println("  [" + current.getName() + "] 首次获取锁，state: 0 -> " + arg);
                            return true;
                        }
                    } else if (current == getExclusiveOwnerThread()) {
                        int nextc = c + arg;
                        setState(nextc);
                        System.out.println("  [" + current.getName() + "] 重入锁，state: " + c + " -> " + nextc);
                        return true;
                    }

                    return false;
                }

                @Override
                protected boolean tryRelease(int arg) {
                    int c = getState() - arg;
                    if (Thread.currentThread() != getExclusiveOwnerThread())
                        throw new IllegalMonitorStateException();

                    boolean free = false;
                    if (c == 0) {
                        free = true;
                        setExclusiveOwnerThread(null);
                        System.out.println("  [" + Thread.currentThread().getName() + "] 完全释放，state: " + getState() + " -> 0");
                    } else {
                        System.out.println("  [" + Thread.currentThread().getName() + "] 部分释放，state: " + getState() + " -> " + c);
                    }
                    setState(c);
                    return free;
                }
            }

            public void lock() {
                sync.acquire(1);
            }

            public void unlock() {
                sync.release(1);
            }
        }

        ReentrantLock lock = new ReentrantLock();

        System.out.println("递归调用测试:\n");

        lock.lock();
        try {
            lock.lock();
            try {
                lock.lock();
                try {
                    System.out.println("  执行业务逻辑");
                } finally {
                    lock.unlock();
                }
            } finally {
                lock.unlock();
            }
        } finally {
            lock.unlock();
        }

        System.out.println("\n✅ 可重入锁支持递归调用");
    }

    /**
     * 演示4：公平锁 vs 非公平锁
     */
    public static void demoFairVsNonfair() throws InterruptedException {
        System.out.println("\n========== 演示4：公平锁 vs 非公平锁 ==========\n");

        class FairLock {
            private final Sync sync = new Sync();

            static class Sync extends AbstractQueuedSynchronizer {
                @Override
                protected boolean tryAcquire(int arg) {
                    Thread current = Thread.currentThread();
                    int c = getState();

                    if (c == 0) {
                        // 公平：检查队列
                        if (!hasQueuedPredecessors() &&
                            compareAndSetState(0, arg)) {
                            setExclusiveOwnerThread(current);
                            System.out.println("  [" + current.getName() + "] 获取锁（公平）");
                            return true;
                        }
                    } else if (current == getExclusiveOwnerThread()) {
                        setState(c + arg);
                        return true;
                    }

                    System.out.println("  [" + current.getName() + "] 获取失败，进入队列");
                    return false;
                }

                @Override
                protected boolean tryRelease(int arg) {
                    int c = getState() - arg;
                    if (Thread.currentThread() != getExclusiveOwnerThread())
                        throw new IllegalMonitorStateException();

                    if (c == 0) {
                        setExclusiveOwnerThread(null);
                        System.out.println("  [" + Thread.currentThread().getName() + "] 释放锁");
                    }
                    setState(c);
                    return c == 0;
                }
            }

            public void lock() {
                sync.acquire(1);
            }

            public void unlock() {
                sync.release(1);
            }
        }

        FairLock lock = new FairLock();

        System.out.println("公平锁测试（严格FIFO）:\n");

        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                lock.lock();
                try {
                    System.out.println("  [Thread-" + id + "] 执行任务");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }, "Thread-" + id).start();
            Thread.sleep(50);
        }

        Thread.sleep(3000);

        System.out.println("\n✅ 公平锁保证FIFO顺序");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 独占锁总结 ==========");

        System.out.println("\n✅ 核心流程:");
        System.out.println("   1. tryAcquire() - 尝试获取");
        System.out.println("   2. 失败则加入队列");
        System.out.println("   3. 在队列中阻塞等待");
        System.out.println("   4. 被唤醒后重试");

        System.out.println("\n📊 关键方法:");
        System.out.println("   tryAcquire()   - 子类实现获取逻辑");
        System.out.println("   tryRelease()   - 子类实现释放逻辑");
        System.out.println("   acquire()      - AQS提供的模板方法");
        System.out.println("   release()      - AQS提供的模板方法");

        System.out.println("\n💡 设计要点:");
        System.out.println("   ✅ 使用state表示锁状态");
        System.out.println("   ✅ 使用CAS保证原子性");
        System.out.println("   ✅ 使用队列管理等待线程");
        System.out.println("   ✅ 支持重入、公平性等特性");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            独占锁演示                                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本锁
        demoBasicLock();

        // 演示2：多线程竞争
        demoMultiThreadCompetition();

        // 演示3：可重入
        demoReentrantLock();

        // 演示4：公平性
        demoFairVsNonfair();

        // 总结
        summarize();

        System.out.println("\n===========================");
        System.out.println("核心要点：");
        System.out.println("1. 独占模式同一时刻只有一个线程可以获取");
        System.out.println("2. tryAcquire返回boolean表示成功或失败");
        System.out.println("3. 支持重入、公平性等高级特性");
        System.out.println("4. 是ReentrantLock的实现基础");
        System.out.println("===========================");
    }
}
