package com.fragment.core.thread.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * 线程协作机制演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>sleep()的使用和特点</li>
 *   <li>join()的使用和特点</li>
 *   <li>yield()的使用和特点</li>
 *   <li>wait/notify的使用和特点</li>
 * </ul>
 * 
 * @author fragment
 */
public class ThreadCooperationDemo {
    
    private static final Object lock = new Object();
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 线程协作机制演示 ==========\n");
        
        // 1. sleep演示
        demonstrateSleep();
        
        // 2. join演示
        demonstrateJoin();
        
        // 3. yield演示
        demonstrateYield();
        
        // 4. wait/notify演示
        demonstrateWaitNotify();
    }
    
    /**
     * 演示sleep()
     */
    private static void demonstrateSleep() throws InterruptedException {
        System.out.println("1. sleep()演示");
        System.out.println("特点: 不释放锁，进入TIMED_WAITING状态\n");
        
        Thread t = new Thread(() -> {
            synchronized (lock) {
                System.out.println("线程获得锁");
                try {
                    System.out.println("线程开始sleep 2秒（持有锁）");
                    Thread.sleep(2000);
                    System.out.println("线程sleep结束");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("线程释放锁");
            }
        });
        
        t.start();
        Thread.sleep(100);
        
        // 尝试获取锁（会被阻塞）
        new Thread(() -> {
            System.out.println("另一个线程尝试获取锁...");
            synchronized (lock) {
                System.out.println("另一个线程获得锁（等待了2秒）");
            }
        }).start();
        
        t.join();
        Thread.sleep(100);
        System.out.println();
    }
    
    /**
     * 演示join()
     */
    private static void demonstrateJoin() throws InterruptedException {
        System.out.println("2. join()演示");
        System.out.println("特点: 等待目标线程结束\n");
        
        List<Thread> threads = new ArrayList<>();
        
        // 创建3个工作线程
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            Thread t = new Thread(() -> {
                System.out.println("任务 " + taskId + " 开始执行");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("任务 " + taskId + " 执行完成");
            });
            threads.add(t);
            t.start();
        }
        
        System.out.println("主线程等待所有任务完成...");
        
        // 等待所有线程完成
        for (Thread t : threads) {
            t.join();
        }
        
        System.out.println("所有任务完成，主线程继续执行\n");
    }
    
    /**
     * 演示yield()
     */
    private static void demonstrateYield() throws InterruptedException {
        System.out.println("3. yield()演示");
        System.out.println("特点: 提示调度器让出CPU，但不保证\n");
        
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("线程1: " + i);
                Thread.yield(); // 让步
            }
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println("线程2: " + i);
                Thread.yield(); // 让步
            }
        }, "Thread-2");
        
        t1.start();
        t2.start();
        
        t1.join();
        t2.join();
        
        System.out.println("注意: yield()不保证一定让步，输出顺序可能不规律\n");
    }
    
    /**
     * 演示wait/notify
     */
    private static void demonstrateWaitNotify() throws InterruptedException {
        System.out.println("4. wait/notify演示");
        System.out.println("特点: wait()释放锁，notify()唤醒等待线程\n");
        
        final boolean[] ready = {false};
        
        // 等待线程
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                System.out.println("等待线程: 检查条件");
                while (!ready[0]) {
                    try {
                        System.out.println("等待线程: 条件不满足，调用wait()释放锁");
                        lock.wait();
                        System.out.println("等待线程: 被唤醒，重新检查条件");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("等待线程: 条件满足，继续执行");
            }
        }, "Waiter");
        
        // 通知线程
        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            synchronized (lock) {
                System.out.println("通知线程: 修改条件");
                ready[0] = true;
                System.out.println("通知线程: 调用notify()唤醒等待线程");
                lock.notify();
                System.out.println("通知线程: notify()调用完成，但锁还未释放");
            }
            System.out.println("通知线程: 退出synchronized块，释放锁");
        }, "Notifier");
        
        waiter.start();
        Thread.sleep(100); // 确保waiter先执行
        notifier.start();
        
        waiter.join();
        notifier.join();
        
        System.out.println("\n关键点:");
        System.out.println("1. wait()必须在synchronized块中调用");
        System.out.println("2. wait()会释放锁");
        System.out.println("3. notify()不会立即释放锁");
        System.out.println("4. 被唤醒的线程需要重新竞争锁");
    }
}
