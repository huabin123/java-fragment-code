package com.fragment.core.thread.demo;

/**
 * 线程生命周期演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>线程的6种状态</li>
 *   <li>状态之间的转换</li>
 *   <li>如何查看线程状态</li>
 * </ul>
 * 
 * @author fragment
 */
public class ThreadLifecycleDemo {
    
    private static final Object lock = new Object();
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 线程生命周期演示 ==========\n");
        
        // 1. NEW状态
        demonstrateNewState();
        
        // 2. RUNNABLE状态
        demonstrateRunnableState();
        
        // 3. BLOCKED状态
        demonstrateBlockedState();
        
        // 4. WAITING状态
        demonstrateWaitingState();
        
        // 5. TIMED_WAITING状态
        demonstrateTimedWaitingState();
        
        // 6. TERMINATED状态
        demonstrateTerminatedState();
    }
    
    /**
     * 演示NEW状态
     */
    private static void demonstrateNewState() {
        System.out.println("1. NEW状态演示");
        
        Thread t = new Thread(() -> {
            System.out.println("线程执行");
        });
        
        System.out.println("线程状态: " + t.getState()); // NEW
        System.out.println("说明: 线程对象已创建，但还未调用start()\n");
    }
    
    /**
     * 演示RUNNABLE状态
     */
    private static void demonstrateRunnableState() throws InterruptedException {
        System.out.println("2. RUNNABLE状态演示");
        
        Thread t = new Thread(() -> {
            // 执行计算密集型任务
            long sum = 0;
            for (int i = 0; i < 1000000; i++) {
                sum += i;
            }
        });
        
        t.start();
        Thread.sleep(10); // 等待线程启动
        
        System.out.println("线程状态: " + t.getState()); // RUNNABLE
        System.out.println("说明: 线程正在JVM中执行，可能在等待CPU调度\n");
        
        t.join();
    }
    
    /**
     * 演示BLOCKED状态
     */
    private static void demonstrateBlockedState() throws InterruptedException {
        System.out.println("3. BLOCKED状态演示");
        
        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                System.out.println("t1获得锁");
                try {
                    Thread.sleep(2000); // 持有锁2秒
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            synchronized (lock) {
                System.out.println("t2获得锁");
            }
        }, "Thread-2");
        
        t1.start();
        Thread.sleep(100); // 确保t1先获得锁
        t2.start();
        Thread.sleep(100); // 确保t2开始等待锁
        
        System.out.println("t2状态: " + t2.getState()); // BLOCKED
        System.out.println("说明: t2正在等待获取synchronized锁\n");
        
        t1.join();
        t2.join();
    }
    
    /**
     * 演示WAITING状态
     */
    private static void demonstrateWaitingState() throws InterruptedException {
        System.out.println("4. WAITING状态演示");
        
        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                try {
                    System.out.println("t1调用wait()");
                    lock.wait(); // 进入WAITING状态
                    System.out.println("t1被唤醒");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Thread-1");
        
        t1.start();
        Thread.sleep(100); // 确保t1进入wait
        
        System.out.println("t1状态: " + t1.getState()); // WAITING
        System.out.println("说明: t1调用了wait()，等待被notify()唤醒");
        
        // 唤醒t1
        synchronized (lock) {
            lock.notify();
        }
        System.out.println("已唤醒t1\n");
        
        t1.join();
    }
    
    /**
     * 演示TIMED_WAITING状态
     */
    private static void demonstrateTimedWaitingState() throws InterruptedException {
        System.out.println("5. TIMED_WAITING状态演示");
        
        Thread t1 = new Thread(() -> {
            try {
                System.out.println("t1开始sleep");
                Thread.sleep(2000);
                System.out.println("t1 sleep结束");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-1");
        
        t1.start();
        Thread.sleep(100); // 确保t1进入sleep
        
        System.out.println("t1状态: " + t1.getState()); // TIMED_WAITING
        System.out.println("说明: t1调用了sleep()，等待超时自动唤醒\n");
        
        t1.join();
    }
    
    /**
     * 演示TERMINATED状态
     */
    private static void demonstrateTerminatedState() throws InterruptedException {
        System.out.println("6. TERMINATED状态演示");
        
        Thread t1 = new Thread(() -> {
            System.out.println("t1执行完成");
        }, "Thread-1");
        
        t1.start();
        t1.join(); // 等待t1结束
        
        System.out.println("t1状态: " + t1.getState()); // TERMINATED
        System.out.println("说明: t1的run()方法已执行完毕，线程生命周期结束\n");
    }
}
