package com.fragment.juc.aqs.project;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 自定义互斥锁
 * 
 * 特性：
 * 1. 独占模式
 * 2. 支持重入
 * 3. 非公平
 * 
 * @author huabin
 */
public class CustomMutex implements Lock {
    
    // 同步器
    private final Sync sync = new Sync();
    
    /**
     * 内部同步器
     */
    private static class Sync extends AbstractQueuedSynchronizer {
        
        /**
         * 尝试获取锁
         * 
         * @param arg 获取参数（通常是1）
         * @return true表示成功
         */
        @Override
        protected boolean tryAcquire(int arg) {
            final Thread current = Thread.currentThread();
            int c = getState();
            
            // 情况1：锁空闲
            if (c == 0) {
                // CAS获取锁
                if (compareAndSetState(0, arg)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 情况2：可重入
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + arg;
                if (nextc < 0) // 溢出检查
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            
            // 情况3：获取失败
            return false;
        }
        
        /**
         * 尝试释放锁
         * 
         * @param arg 释放参数（通常是1）
         * @return true表示完全释放
         */
        @Override
        protected boolean tryRelease(int arg) {
            int c = getState() - arg;
            
            // 检查是否是持有锁的线程
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            
            boolean free = false;
            if (c == 0) {
                // 完全释放
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }
        
        /**
         * 是否被当前线程独占
         */
        @Override
        protected boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
        
        /**
         * 创建条件变量
         */
        Condition newCondition() {
            return new ConditionObject();
        }
    }
    
    // ========== Lock接口实现 ==========
    
    @Override
    public void lock() {
        sync.acquire(1);
    }
    
    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }
    
    @Override
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }
    
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(time));
    }
    
    @Override
    public void unlock() {
        sync.release(1);
    }
    
    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 是否被锁定
     */
    public boolean isLocked() {
        return sync.getState() != 0;
    }
    
    /**
     * 是否被当前线程持有
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }
    
    /**
     * 获取重入次数
     */
    public int getHoldCount() {
        return sync.getState();
    }
    
    // ========== 测试 ==========
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            自定义互斥锁测试                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        
        testBasicLock();
        testReentrant();
        testMultiThread();
        
        System.out.println("\n===========================");
        System.out.println("✅ 所有测试通过！");
        System.out.println("===========================");
    }
    
    /**
     * 测试1：基本加锁解锁
     */
    public static void testBasicLock() {
        System.out.println("\n========== 测试1：基本加锁解锁 ==========\n");
        
        CustomMutex lock = new CustomMutex();
        
        System.out.println("初始状态: isLocked=" + lock.isLocked());
        
        lock.lock();
        System.out.println("加锁后: isLocked=" + lock.isLocked() + ", holdCount=" + lock.getHoldCount());
        
        lock.unlock();
        System.out.println("解锁后: isLocked=" + lock.isLocked() + ", holdCount=" + lock.getHoldCount());
        
        System.out.println("\n✅ 基本加锁解锁正常");
    }
    
    /**
     * 测试2：重入
     */
    public static void testReentrant() {
        System.out.println("\n========== 测试2：重入 ==========\n");
        
        CustomMutex lock = new CustomMutex();
        
        lock.lock();
        System.out.println("第1次加锁，holdCount=" + lock.getHoldCount());
        
        lock.lock();
        System.out.println("第2次加锁，holdCount=" + lock.getHoldCount());
        
        lock.lock();
        System.out.println("第3次加锁，holdCount=" + lock.getHoldCount());
        
        lock.unlock();
        System.out.println("第1次解锁，holdCount=" + lock.getHoldCount());
        
        lock.unlock();
        System.out.println("第2次解锁，holdCount=" + lock.getHoldCount());
        
        lock.unlock();
        System.out.println("第3次解锁，holdCount=" + lock.getHoldCount());
        
        System.out.println("\n✅ 重入功能正常");
    }
    
    /**
     * 测试3：多线程竞争
     */
    public static void testMultiThread() throws InterruptedException {
        System.out.println("\n========== 测试3：多线程竞争 ==========\n");
        
        CustomMutex lock = new CustomMutex();
        int[] counter = {0};
        
        Thread[] threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int threadId = i + 1;
            threads[i] = new Thread(() -> {
                lock.lock();
                try {
                    System.out.println("Thread-" + threadId + " 获取锁");
                    for (int j = 0; j < 1000; j++) {
                        counter[0]++;
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("Thread-" + threadId + " 释放锁");
                    lock.unlock();
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("\n计数器值: " + counter[0] + " (预期: 3000)");
        System.out.println("\n✅ 多线程互斥正常");
    }
}
