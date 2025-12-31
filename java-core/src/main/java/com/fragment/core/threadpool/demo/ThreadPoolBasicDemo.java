package com.fragment.core.threadpool.demo;

import java.util.concurrent.*;

/**
 * 线程池基础演示
 * 
 * @author fragment
 */
public class ThreadPoolBasicDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 线程池基础演示 ==========\n");
        
        // 1. 创建线程池的错误方式
        demonstrateWrongWay();
        
        // 2. 创建线程池的正确方式
        demonstrateCorrectWay();
        
        // 3. 不同拒绝策略的演示
        demonstrateRejectedPolicies();
        
        // 4. 优雅关闭线程池
        demonstrateGracefulShutdown();
    }
    
    /**
     * 错误方式：使用Executors工具类
     */
    private static void demonstrateWrongWay() {
        System.out.println("1. ❌ 错误方式：使用Executors工具类");
        
        // 问题：无界队列，可能导致OOM
        ExecutorService executor = Executors.newFixedThreadPool(10);
        System.out.println("创建了newFixedThreadPool，使用无界队列LinkedBlockingQueue");
        System.out.println("风险：队列可能无限增长，导致OOM\n");
        
        executor.shutdown();
    }
    
    /**
     * 正确方式：手动创建ThreadPoolExecutor
     */
    private static void demonstrateCorrectWay() throws InterruptedException {
        System.out.println("2. ✅ 正确方式：手动创建ThreadPoolExecutor");
        
        int cpuCount = Runtime.getRuntime().availableProcessors();
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            cpuCount,                              // 核心线程数
            cpuCount * 2,                          // 最大线程数
            60L,                                   // 空闲线程存活时间
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),         // 有界队列
            new ThreadFactory() {                  // 自定义线程工厂
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "demo-pool-" + count++);
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 调用者执行策略
        );
        
        System.out.println("核心线程数: " + executor.getCorePoolSize());
        System.out.println("最大线程数: " + executor.getMaximumPoolSize());
        System.out.println("队列容量: 100");
        System.out.println("拒绝策略: CallerRunsPolicy\n");
        
        // 提交任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.execute(() -> {
                System.out.println("执行任务 " + taskId + 
                                 ", 线程: " + Thread.currentThread().getName());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        
        Thread.sleep(2000);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 演示不同的拒绝策略
     */
    private static void demonstrateRejectedPolicies() throws InterruptedException {
        System.out.println("3. 不同拒绝策略的演示\n");
        
        // 策略1: AbortPolicy - 抛出异常
        System.out.println("策略1: AbortPolicy - 抛出异常");
        demonstrateAbortPolicy();
        
        // 策略2: CallerRunsPolicy - 调用者执行
        System.out.println("\n策略2: CallerRunsPolicy - 调用者执行");
        demonstrateCallerRunsPolicy();
        
        // 策略3: DiscardPolicy - 静默丢弃
        System.out.println("\n策略3: DiscardPolicy - 静默丢弃");
        demonstrateDiscardPolicy();
        
        System.out.println();
    }
    
    private static void demonstrateAbortPolicy() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.AbortPolicy()
        );
        
        try {
            // 提交3个任务：1个执行，1个排队，1个被拒绝
            executor.execute(() -> sleep(1000));
            executor.execute(() -> sleep(1000));
            executor.execute(() -> sleep(1000));  // 这个会被拒绝
        } catch (RejectedExecutionException e) {
            System.out.println("任务被拒绝，抛出异常: " + e.getMessage());
        }
        
        executor.shutdown();
    }
    
    private static void demonstrateCallerRunsPolicy() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 提交3个任务：1个执行，1个排队，1个由调用者执行
        executor.execute(() -> {
            System.out.println("任务1执行，线程: " + Thread.currentThread().getName());
            sleep(100);
        });
        executor.execute(() -> {
            System.out.println("任务2执行，线程: " + Thread.currentThread().getName());
            sleep(100);
        });
        executor.execute(() -> {
            System.out.println("任务3执行，线程: " + Thread.currentThread().getName() + " (调用者)");
            sleep(100);
        });
        
        Thread.sleep(500);
        executor.shutdown();
    }
    
    private static void demonstrateDiscardPolicy() throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.DiscardPolicy()
        );
        
        // 提交3个任务：1个执行，1个排队，1个被丢弃
        executor.execute(() -> {
            System.out.println("任务1执行");
            sleep(100);
        });
        executor.execute(() -> {
            System.out.println("任务2执行");
            sleep(100);
        });
        executor.execute(() -> {
            System.out.println("任务3执行（不会打印，因为被丢弃）");
            sleep(100);
        });
        
        Thread.sleep(500);
        executor.shutdown();
    }
    
    /**
     * 演示优雅关闭线程池
     */
    private static void demonstrateGracefulShutdown() throws InterruptedException {
        System.out.println("4. 优雅关闭线程池\n");
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10)
        );
        
        // 提交任务
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            executor.execute(() -> {
                System.out.println("执行任务 " + taskId);
                sleep(500);
                System.out.println("任务 " + taskId + " 完成");
            });
        }
        
        // 优雅关闭
        System.out.println("调用shutdown()，不再接受新任务");
        executor.shutdown();
        
        // 尝试提交新任务（会被拒绝）
        try {
            executor.execute(() -> System.out.println("新任务"));
        } catch (RejectedExecutionException e) {
            System.out.println("新任务被拒绝: " + e.getMessage());
        }
        
        // 等待任务完成
        System.out.println("等待已提交的任务完成...");
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        
        if (terminated) {
            System.out.println("所有任务已完成，线程池已关闭");
        } else {
            System.out.println("超时，强制关闭");
            executor.shutdownNow();
        }
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
