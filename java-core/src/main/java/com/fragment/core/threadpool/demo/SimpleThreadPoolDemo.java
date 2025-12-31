package com.fragment.core.threadpool.demo;

import com.fragment.core.threadpool.simple.SimpleThreadPool;

/**
 * 简易线程池演示
 * 
 * @author fragment
 */
public class SimpleThreadPoolDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 简易线程池演示 ==========\n");
        
        // 1. 基本使用
        demonstrateBasicUsage();
        
        // 2. 拒绝策略演示
        demonstrateRejectedPolicies();
        
        // 3. 监控演示
        demonstrateMonitoring();
    }
    
    /**
     * 基本使用演示
     */
    private static void demonstrateBasicUsage() throws InterruptedException {
        System.out.println("1. 基本使用演示\n");
        
        // 创建线程池：3个线程，队列容量5
        SimpleThreadPool pool = new SimpleThreadPool(
            3, 
            5, 
            new SimpleThreadPool.CallerRunsPolicy()
        );
        
        // 提交10个任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("执行任务 " + taskId + 
                                 ", 线程: " + Thread.currentThread().getName());
                sleep(500);
                System.out.println("任务 " + taskId + " 完成");
            });
            
            System.out.println(pool.getStatus());
        }
        
        // 关闭线程池
        pool.shutdown();
        pool.awaitTermination(10000);
        
        System.out.println("\n最终状态: " + pool.getStatus());
        System.out.println();
    }
    
    /**
     * 拒绝策略演示
     */
    private static void demonstrateRejectedPolicies() throws InterruptedException {
        System.out.println("2. 拒绝策略演示\n");
        
        // 策略1: AbortPolicy
        System.out.println("策略1: AbortPolicy - 抛出异常");
        demonstrateAbortPolicy();
        
        // 策略2: CallerRunsPolicy
        System.out.println("\n策略2: CallerRunsPolicy - 调用者执行");
        demonstrateCallerRunsPolicy();
        
        // 策略3: DiscardPolicy
        System.out.println("\n策略3: DiscardPolicy - 静默丢弃");
        demonstrateDiscardPolicy();
        
        System.out.println();
    }
    
    private static void demonstrateAbortPolicy() throws InterruptedException {
        SimpleThreadPool pool = new SimpleThreadPool(
            1, 1, new SimpleThreadPool.AbortPolicy()
        );
        
        try {
            // 提交3个任务：1个执行，1个排队，1个被拒绝
            pool.execute(() -> sleep(1000));
            pool.execute(() -> sleep(1000));
            pool.execute(() -> sleep(1000));  // 这个会被拒绝
        } catch (RuntimeException e) {
            System.out.println("捕获异常: " + e.getMessage());
        }
        
        pool.shutdown();
        pool.awaitTermination(5000);
    }
    
    private static void demonstrateCallerRunsPolicy() throws InterruptedException {
        SimpleThreadPool pool = new SimpleThreadPool(
            1, 1, new SimpleThreadPool.CallerRunsPolicy()
        );
        
        // 提交3个任务
        pool.execute(() -> {
            System.out.println("任务1，线程: " + Thread.currentThread().getName());
            sleep(100);
        });
        pool.execute(() -> {
            System.out.println("任务2，线程: " + Thread.currentThread().getName());
            sleep(100);
        });
        pool.execute(() -> {
            System.out.println("任务3，线程: " + Thread.currentThread().getName() + " (调用者)");
            sleep(100);
        });
        
        pool.shutdown();
        pool.awaitTermination(5000);
    }
    
    private static void demonstrateDiscardPolicy() throws InterruptedException {
        SimpleThreadPool pool = new SimpleThreadPool(
            1, 1, new SimpleThreadPool.DiscardPolicy()
        );
        
        // 提交3个任务
        pool.execute(() -> {
            System.out.println("任务1执行");
            sleep(100);
        });
        pool.execute(() -> {
            System.out.println("任务2执行");
            sleep(100);
        });
        pool.execute(() -> {
            System.out.println("任务3执行（不会打印）");
            sleep(100);
        });
        
        pool.shutdown();
        pool.awaitTermination(5000);
    }
    
    /**
     * 监控演示
     */
    private static void demonstrateMonitoring() throws InterruptedException {
        System.out.println("3. 监控演示\n");
        
        SimpleThreadPool pool = new SimpleThreadPool(
            2, 3, new SimpleThreadPool.CallerRunsPolicy()
        );
        
        // 启动监控线程
        Thread monitor = new Thread(() -> {
            while (!pool.isShutdown()) {
                System.out.println("[监控] " + pool.getStatus());
                sleep(500);
            }
        });
        monitor.setDaemon(true);
        monitor.start();
        
        // 提交任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("执行任务 " + taskId);
                sleep(1000);
            });
            Thread.sleep(200);
        }
        
        pool.shutdown();
        pool.awaitTermination(20000);
        
        System.out.println("\n最终状态: " + pool.getStatus());
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
