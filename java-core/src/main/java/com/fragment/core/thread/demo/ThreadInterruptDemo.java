package com.fragment.core.thread.demo;

/**
 * 线程中断机制演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>interrupt()的使用</li>
 *   <li>isInterrupted()的使用</li>
 *   <li>interrupted()的使用</li>
 *   <li>正确处理InterruptedException</li>
 * </ul>
 * 
 * @author fragment
 */
public class ThreadInterruptDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 线程中断机制演示 ==========\n");
        
        // 1. 中断运行中的线程
        demonstrateInterruptRunning();
        
        // 2. 中断阻塞的线程
        demonstrateInterruptBlocked();
        
        // 3. isInterrupted() vs interrupted()
        demonstrateInterruptMethods();
        
        // 4. 正确处理中断
        demonstrateCorrectHandling();
    }
    
    /**
     * 演示中断运行中的线程
     */
    private static void demonstrateInterruptRunning() throws InterruptedException {
        System.out.println("1. 中断运行中的线程");
        
        Thread t = new Thread(() -> {
            System.out.println("线程开始运行");
            
            while (!Thread.currentThread().isInterrupted()) {
                // 执行任务
                System.out.println("正在执行任务...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    System.out.println("线程被中断");
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            System.out.println("线程结束");
        });
        
        t.start();
        Thread.sleep(1500);
        
        System.out.println("主线程发送中断信号");
        t.interrupt();
        
        t.join();
        System.out.println();
    }
    
    /**
     * 演示中断阻塞的线程
     */
    private static void demonstrateInterruptBlocked() throws InterruptedException {
        System.out.println("2. 中断阻塞的线程");
        
        Thread t = new Thread(() -> {
            try {
                System.out.println("线程开始sleep 10秒");
                Thread.sleep(10000);
                System.out.println("sleep完成"); // 不会执行
            } catch (InterruptedException e) {
                System.out.println("sleep被中断");
                System.out.println("中断标志: " + Thread.currentThread().isInterrupted()); // false
            }
        });
        
        t.start();
        Thread.sleep(1000);
        
        System.out.println("主线程中断sleep中的线程");
        t.interrupt();
        
        t.join();
        System.out.println("说明: 抛出InterruptedException后，中断标志被清除\n");
    }
    
    /**
     * 演示isInterrupted() vs interrupted()
     */
    private static void demonstrateInterruptMethods() throws InterruptedException {
        System.out.println("3. isInterrupted() vs interrupted()");
        
        Thread t = new Thread(() -> {
            // 第1次检查
            System.out.println("第1次 interrupted(): " + Thread.interrupted()); // true，清除标志
            System.out.println("第2次 interrupted(): " + Thread.interrupted()); // false
            System.out.println("第3次 isInterrupted(): " + Thread.currentThread().isInterrupted()); // false
        });
        
        t.start();
        Thread.sleep(100);
        t.interrupt();
        Thread.sleep(100);
        
        System.out.println("说明:");
        System.out.println("- interrupted()是静态方法，检查并清除中断标志");
        System.out.println("- isInterrupted()是实例方法，只检查不清除\n");
    }
    
    /**
     * 演示正确处理中断
     */
    private static void demonstrateCorrectHandling() throws InterruptedException {
        System.out.println("4. 正确处理中断");
        
        // 方式1：重新抛出异常
        Thread t1 = new Thread(() -> {
            try {
                doWorkWithException();
            } catch (InterruptedException e) {
                System.out.println("方式1: 捕获到中断异常，线程退出");
            }
        });
        
        // 方式2：恢复中断状态
        Thread t2 = new Thread(() -> {
            doWorkWithRestore();
            System.out.println("方式2: 恢复中断状态后退出");
        });
        
        // 方式3：退出循环
        Thread t3 = new Thread(() -> {
            doWorkWithBreak();
            System.out.println("方式3: 检测到中断后退出循环");
        });
        
        t1.start();
        Thread.sleep(100);
        t1.interrupt();
        t1.join();
        
        System.out.println();
        
        t2.start();
        Thread.sleep(100);
        t2.interrupt();
        t2.join();
        
        System.out.println();
        
        t3.start();
        Thread.sleep(100);
        t3.interrupt();
        t3.join();
        
        System.out.println("\n关键点:");
        System.out.println("1. 不要吞掉InterruptedException");
        System.out.println("2. 要么重新抛出，要么恢复中断状态");
        System.out.println("3. 在退出前清理资源");
    }
    
    /**
     * 方式1：重新抛出异常
     */
    private static void doWorkWithException() throws InterruptedException {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println("  捕获中断，清理资源");
            // 清理资源
            throw e; // 重新抛出
        }
    }
    
    /**
     * 方式2：恢复中断状态
     */
    private static void doWorkWithRestore() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println("  捕获中断，恢复中断状态");
            // 恢复中断标志
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 方式3：退出循环
     */
    private static void doWorkWithBreak() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.out.println("  捕获中断，退出循环");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
