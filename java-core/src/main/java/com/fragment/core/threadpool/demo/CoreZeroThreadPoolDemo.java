package com.fragment.core.threadpool.demo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 核心线程数为0的线程池演示
 * 
 * 演示场景：
 * 1. 核心线程数0 + 最大线程数1 + 无界队列
 * 2. newCachedThreadPool 的行为
 * 3. 两者的对比分析
 * 
 * @author fragment
 */
public class CoreZeroThreadPoolDemo {
    
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 核心线程数为0的线程池演示 ==========");
        System.out.println();
        
        // 场景1：核心线程数0 + 最大线程数1 + 无界队列
        demonstrateScenario1();
        
        Thread.sleep(2000);
        System.out.println();
        System.out.println("============================================================");
        System.out.println();
        
        // 场景2：newCachedThreadPool
        demonstrateScenario2();
        
        Thread.sleep(2000);
        System.out.println();
        System.out.println("============================================================");
        System.out.println();
        
        // 场景3：对比分析
        compareScenarios();
    }
    
    /**
     * 场景1：核心线程数0 + 最大线程数1 + 无界队列
     * 预期行为：
     * - 第一个任务入队，创建1个线程执行
     * - 后续任务入队，由唯一线程串行执行
     * - 任务会堆积在队列中
     */
    private static void demonstrateScenario1() throws InterruptedException {
        System.out.println("【场景1：核心0 + 最大1 + 无界队列】");
        System.out.println("配置：corePoolSize=0, maximumPoolSize=1, 无界队列");
        System.out.println();
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0,                          // corePoolSize = 0
            1,                          // maximumPoolSize = 1
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), // 无界队列
            new NamedThreadFactory("Scenario1")
        );
        
        // 提交5个任务
        System.out.println("提交5个任务...");
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println(String.format("[%s] 执行任务%d - 开始 (队列大小: %d, 活跃线程: %d)",
                        Thread.currentThread().getName(),
                        taskId,
                        executor.getQueue().size(),
                        executor.getActiveCount()
                    ));
                    
                    try {
                        Thread.sleep(500); // 模拟任务执行
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    System.out.println(String.format("[%s] 执行任务%d - 完成",
                        Thread.currentThread().getName(),
                        taskId
                    ));
                }
            });
            
            // 打印线程池状态
            Thread.sleep(100);
            printPoolStatus("提交任务" + i + "后", executor);
        }
        
        // 等待任务执行完成
        Thread.sleep(3000);
        
        System.out.println();
        System.out.println("结论：");
        System.out.println("✓ 只创建了1个线程");
        System.out.println("✓ 所有任务串行执行");
        System.out.println("✓ 任务会堆积在队列中");
        System.out.println("⚠️  风险：队列可能无限增长，导致OOM");
        
        executor.shutdown();
    }
    
    /**
     * 场景2：newCachedThreadPool
     * 预期行为：
     * - 每个任务都会创建新线程（如果没有空闲线程）
     * - 任务并发执行
     * - 线程数可能快速增长
     */
    private static void demonstrateScenario2() throws InterruptedException {
        System.out.println("【场景2：newCachedThreadPool】");
        System.out.println("配置：corePoolSize=0, maximumPoolSize=Integer.MAX_VALUE, SynchronousQueue");
        System.out.println();
        
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool(
            new NamedThreadFactory("Scenario2")
        );
        
        // 提交5个任务
        System.out.println("提交5个任务...");
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println(String.format("[%s] 执行任务%d - 开始 (队列大小: %d, 活跃线程: %d)",
                        Thread.currentThread().getName(),
                        taskId,
                        executor.getQueue().size(),
                        executor.getActiveCount()
                    ));
                    
                    try {
                        Thread.sleep(500); // 模拟任务执行
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    System.out.println(String.format("[%s] 执行任务%d - 完成",
                        Thread.currentThread().getName(),
                        taskId
                    ));
                }
            });
            
            // 打印线程池状态
            Thread.sleep(100);
            printPoolStatus("提交任务" + i + "后", executor);
        }
        
        // 等待任务执行完成
        Thread.sleep(1000);
        
        System.out.println();
        System.out.println("结论：");
        System.out.println("✓ 创建了多个线程");
        System.out.println("✓ 任务并发执行");
        System.out.println("✓ SynchronousQueue不存储任务");
        System.out.println("⚠️  风险：线程数可能快速增长，导致OOM");
        
        executor.shutdown();
    }
    
    /**
     * 场景3：对比分析
     */
    private static void compareScenarios() throws InterruptedException {
        System.out.println("【场景3：对比分析】");
        System.out.println();
        
        System.out.println("提交10个快速任务，观察线程创建情况：");
        System.out.println();
        
        // 场景1：核心0 + 最大1 + 无界队列
        ThreadPoolExecutor executor1 = new ThreadPoolExecutor(
            0, 1, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new NamedThreadFactory("Compare1")
        );
        
        // 场景2：newCachedThreadPool
        ThreadPoolExecutor executor2 = (ThreadPoolExecutor) Executors.newCachedThreadPool(
            new NamedThreadFactory("Compare2")
        );
        
        System.out.println("场景1（核心0+最大1+无界队列）：");
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            executor1.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        executor1.shutdown();
        executor1.awaitTermination(10, TimeUnit.SECONDS);
        long time1 = System.currentTimeMillis() - start1;
        System.out.println(String.format("执行时间: %dms (串行执行)", time1));
        System.out.println();
        
        System.out.println("场景2（newCachedThreadPool）：");
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            executor2.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        executor2.shutdown();
        executor2.awaitTermination(10, TimeUnit.SECONDS);
        long time2 = System.currentTimeMillis() - start2;
        System.out.println(String.format("执行时间: %dms (并发执行)", time2));
        System.out.println();
        
        System.out.println("对比结果：");
        System.out.println(String.format("✓ 场景1耗时: %dms (串行)", time1));
        System.out.println(String.format("✓ 场景2耗时: %dms (并发)", time2));
        System.out.println(String.format("✓ 性能差异: %.1fx", (double) time1 / time2));
    }
    
    /**
     * 打印线程池状态
     */
    private static void printPoolStatus(String prefix, ThreadPoolExecutor executor) {
        System.out.printf("  [状态] %s: 线程数=%d, 活跃=%d, 队列=%d, 完成=%d%n",
            prefix,
            executor.getPoolSize(),
            executor.getActiveCount(),
            executor.getQueue().size(),
            executor.getCompletedTaskCount()
        );
    }
    
    /**
     * 自定义线程工厂
     */
    static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-Thread-" + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
