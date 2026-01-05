package com.fragment.juc.aqs.project;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 自定义信号量
 * 
 * 特性：
 * 1. 共享模式
 * 2. 限制并发数
 * 3. 支持公平/非公平
 * 
 * @author huabin
 */
public class CustomSemaphore {
    
    private final Sync sync;
    
    /**
     * 构造函数
     * 
     * @param permits 许可数
     * @param fair 是否公平
     */
    public CustomSemaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
    
    public CustomSemaphore(int permits) {
        this(permits, false);
    }
    
    /**
     * 同步器基类
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        Sync(int permits) {
            setState(permits);
        }
        
        final int getPermits() {
            return getState();
        }
        
        /**
         * 非公平获取
         */
        final int nonfairTryAcquireShared(int acquires) {
            for (;;) {
                int available = getState();
                int remaining = available - acquires;
                
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
        
        /**
         * 释放
         */
        @Override
        protected final boolean tryReleaseShared(int releases) {
            for (;;) {
                int current = getState();
                int next = current + releases;
                
                if (next < current) // 溢出检查
                    throw new Error("Maximum permit count exceeded");
                
                if (compareAndSetState(current, next))
                    return true;
            }
        }
    }
    
    /**
     * 非公平同步器
     */
    static final class NonfairSync extends Sync {
        NonfairSync(int permits) {
            super(permits);
        }
        
        @Override
        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }
    
    /**
     * 公平同步器
     */
    static final class FairSync extends Sync {
        FairSync(int permits) {
            super(permits);
        }
        
        @Override
        protected int tryAcquireShared(int acquires) {
            for (;;) {
                // 公平：先检查队列
                if (hasQueuedPredecessors())
                    return -1;
                
                int available = getState();
                int remaining = available - acquires;
                
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
    }
    
    // ========== 公共API ==========
    
    /**
     * 获取许可
     */
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    
    /**
     * 获取多个许可
     */
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }
    
    /**
     * 尝试获取许可
     */
    public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }
    
    /**
     * 超时获取许可
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }
    
    /**
     * 释放许可
     */
    public void release() {
        sync.releaseShared(1);
    }
    
    /**
     * 释放多个许可
     */
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }
    
    /**
     * 获取可用许可数
     */
    public int availablePermits() {
        return sync.getPermits();
    }
    
    // ========== 测试 ==========
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            自定义信号量测试                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        
        testBasicSemaphore();
        testMultiPermits();
        testConcurrency();
        
        System.out.println("\n===========================");
        System.out.println("✅ 所有测试通过！");
        System.out.println("===========================");
    }
    
    /**
     * 测试1：基本使用
     */
    public static void testBasicSemaphore() throws InterruptedException {
        System.out.println("\n========== 测试1：基本使用 ==========\n");
        
        CustomSemaphore semaphore = new CustomSemaphore(3);
        
        System.out.println("初始许可数: " + semaphore.availablePermits());
        
        semaphore.acquire();
        System.out.println("获取1个许可，剩余: " + semaphore.availablePermits());
        
        semaphore.acquire();
        System.out.println("获取1个许可，剩余: " + semaphore.availablePermits());
        
        semaphore.release();
        System.out.println("释放1个许可，剩余: " + semaphore.availablePermits());
        
        System.out.println("\n✅ 基本使用正常");
    }
    
    /**
     * 测试2：多个许可
     */
    public static void testMultiPermits() throws InterruptedException {
        System.out.println("\n========== 测试2：多个许可 ==========\n");
        
        CustomSemaphore semaphore = new CustomSemaphore(5);
        
        System.out.println("初始许可数: " + semaphore.availablePermits());
        
        semaphore.acquire(3);
        System.out.println("获取3个许可，剩余: " + semaphore.availablePermits());
        
        semaphore.release(2);
        System.out.println("释放2个许可，剩余: " + semaphore.availablePermits());
        
        System.out.println("\n✅ 多个许可操作正常");
    }
    
    /**
     * 测试3：并发控制
     */
    public static void testConcurrency() throws InterruptedException {
        System.out.println("\n========== 测试3：并发控制 ==========\n");
        
        CustomSemaphore semaphore = new CustomSemaphore(2);
        
        System.out.println("信号量许可数: 2");
        System.out.println("启动5个线程竞争:\n");
        
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int threadId = i + 1;
            threads[i] = new Thread(() -> {
                try {
                    System.out.println("Thread-" + threadId + " 等待许可，当前可用: " + 
                                     semaphore.availablePermits());
                    semaphore.acquire();
                    System.out.println("Thread-" + threadId + " 获取许可，开始执行");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("Thread-" + threadId + " 释放许可");
                    semaphore.release();
                }
            });
            threads[i].start();
            Thread.sleep(100);
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("\n最终许可数: " + semaphore.availablePermits());
        System.out.println("\n✅ 并发控制正常");
    }
}
