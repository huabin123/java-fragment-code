package com.fragment.juc.aqs.project;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 自定义倒计时门栓
 * 
 * 特性：
 * 1. 共享模式
 * 2. 倒计时到0唤醒所有等待线程
 * 3. 一次性使用
 * 
 * @author huabin
 */
public class CustomCountDownLatch {
    
    private final Sync sync;
    
    /**
     * 构造函数
     * 
     * @param count 倒计时数量
     */
    public CustomCountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }
    
    /**
     * 同步器
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync(int count) {
            setState(count);
        }
        
        int getCount() {
            return getState();
        }
        
        /**
         * 尝试获取（await）
         * 
         * @return 1表示成功（倒计时完成），-1表示失败（需要等待）
         */
        @Override
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
        
        /**
         * 尝试释放（countDown）
         * 
         * @return true表示减到0（唤醒所有等待线程）
         */
        @Override
        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int c = getState();
                
                // 已经是0，无需释放
                if (c == 0)
                    return false;
                
                int nextc = c - 1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0; // 减到0时返回true
            }
        }
    }
    
    // ========== 公共API ==========
    
    /**
     * 等待倒计时完成
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    
    /**
     * 超时等待
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }
    
    /**
     * 倒计时
     */
    public void countDown() {
        sync.releaseShared(1);
    }
    
    /**
     * 获取当前计数
     */
    public long getCount() {
        return sync.getCount();
    }
    
    @Override
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
    
    // ========== 测试 ==========
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            自定义倒计时门栓测试                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        
        testBasicCountDown();
        testMultipleWaiters();
        testTimeout();
        
        System.out.println("\n===========================");
        System.out.println("✅ 所有测试通过！");
        System.out.println("===========================");
    }
    
    /**
     * 测试1：基本倒计时
     */
    public static void testBasicCountDown() throws InterruptedException {
        System.out.println("\n========== 测试1：基本倒计时 ==========\n");
        
        CustomCountDownLatch latch = new CustomCountDownLatch(3);
        
        System.out.println("初始计数: " + latch.getCount());
        
        // 主线程等待
        Thread mainThread = new Thread(() -> {
            try {
                System.out.println("[主线程] 开始等待");
                latch.await();
                System.out.println("[主线程] 等待结束");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        mainThread.start();
        
        Thread.sleep(500);
        
        // 倒计时
        System.out.println("\n开始倒计时:");
        latch.countDown();
        System.out.println("countDown，剩余: " + latch.getCount());
        
        Thread.sleep(500);
        
        latch.countDown();
        System.out.println("countDown，剩余: " + latch.getCount());
        
        Thread.sleep(500);
        
        latch.countDown();
        System.out.println("countDown，剩余: " + latch.getCount());
        
        mainThread.join();
        
        System.out.println("\n✅ 基本倒计时正常");
    }
    
    /**
     * 测试2：多个等待线程
     */
    public static void testMultipleWaiters() throws InterruptedException {
        System.out.println("\n========== 测试2：多个等待线程 ==========\n");
        
        CustomCountDownLatch latch = new CustomCountDownLatch(3);
        
        System.out.println("初始计数: " + latch.getCount());
        System.out.println("启动3个等待线程:\n");
        
        // 3个等待线程
        Thread[] waiters = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final int threadId = i + 1;
            waiters[i] = new Thread(() -> {
                try {
                    System.out.println("[等待线程" + threadId + "] 开始等待");
                    latch.await();
                    System.out.println("[等待线程" + threadId + "] 等待结束");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            waiters[i].start();
        }
        
        Thread.sleep(1000);
        
        System.out.println("\n启动3个工作线程:\n");
        
        // 3个工作线程
        for (int i = 0; i < 3; i++) {
            final int threadId = i + 1;
            new Thread(() -> {
                try {
                    Thread.sleep(threadId * 500);
                    System.out.println("[工作线程" + threadId + "] 完成任务");
                    latch.countDown();
                    System.out.println("剩余计数: " + latch.getCount());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        
        for (Thread waiter : waiters) {
            waiter.join();
        }
        
        System.out.println("\n✅ 所有等待线程都被唤醒");
    }
    
    /**
     * 测试3：超时等待
     */
    public static void testTimeout() throws InterruptedException {
        System.out.println("\n========== 测试3：超时等待 ==========\n");
        
        CustomCountDownLatch latch = new CustomCountDownLatch(3);
        
        System.out.println("初始计数: " + latch.getCount());
        System.out.println("尝试等待1秒（倒计时未完成）:\n");
        
        long startTime = System.currentTimeMillis();
        boolean result = latch.await(1, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        System.out.println("等待结果: " + result);
        System.out.println("等待时间: " + (endTime - startTime) + "ms");
        System.out.println("剩余计数: " + latch.getCount());
        
        System.out.println("\n完成倒计时:");
        latch.countDown();
        latch.countDown();
        latch.countDown();
        
        System.out.println("\n再次等待（倒计时已完成）:");
        result = latch.await(1, TimeUnit.SECONDS);
        System.out.println("等待结果: " + result + " (立即返回)");
        
        System.out.println("\n✅ 超时等待正常");
    }
}
