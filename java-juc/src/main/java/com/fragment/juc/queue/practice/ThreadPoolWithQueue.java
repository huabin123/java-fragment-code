package com.fragment.juc.queue.practice;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义线程池实战 - 基于阻塞队列的线程池实现
 * 
 * <p>场景：自定义线程池，深入理解ThreadPoolExecutor原理
 * <ul>
 *   <li>核心线程池</li>
 *   <li>任务队列</li>
 *   <li>拒绝策略</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>LinkedBlockingQueue作为任务队列</li>
 *   <li>线程复用</li>
 *   <li>优雅停止</li>
 * </ul>
 * 
 * @author fragment
 */
public class ThreadPoolWithQueue {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 自定义线程池实战 ==========\n");

        // 场景1：固定线程池
        demonstrateFixedThreadPool();

        Thread.sleep(1000);

        // 场景2：有界队列 + 拒绝策略
        demonstrateBoundedQueueWithRejection();

        Thread.sleep(1000);

        // 场景3：监控线程池状态
        demonstrateMonitoring();
    }

    /**
     * 场景1：固定线程池
     */
    private static void demonstrateFixedThreadPool() throws InterruptedException {
        System.out.println("=== 场景1：固定线程池 ===");
        System.out.println("配置：3个核心线程，队列容量100\n");

        SimpleThreadPool pool = new SimpleThreadPool(3, 100);
        pool.start();

        // 提交10个任务
        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            pool.submit(() -> {
                System.out.println("[任务" + taskId + "] 开始执行，线程: " + 
                                 Thread.currentThread().getName());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("[任务" + taskId + "] 执行完成");
            });
        }

        Thread.sleep(3000);
        pool.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：有界队列 + 拒绝策略
     */
    private static void demonstrateBoundedQueueWithRejection() throws InterruptedException {
        System.out.println("=== 场景2：有界队列 + 拒绝策略 ===");
        System.out.println("配置：2个核心线程，队列容量5\n");

        SimpleThreadPool pool = new SimpleThreadPool(2, 5);
        pool.start();

        // 提交10个任务（超过容量）
        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            boolean success = pool.trySubmit(() -> {
                System.out.println("[任务" + taskId + "] 执行");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 100);
            
            if (!success) {
                System.out.println("[任务" + taskId + "] 被拒绝（队列已满）");
            }
        }

        Thread.sleep(3000);
        pool.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：监控线程池状态
     */
    private static void demonstrateMonitoring() throws InterruptedException {
        System.out.println("=== 场景3：监控线程池状态 ===\n");

        SimpleThreadPool pool = new SimpleThreadPool(3, 10);
        pool.start();

        // 启动监控线程
        Thread monitor = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                pool.printStatistics();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitor.start();

        // 提交任务
        for (int i = 1; i <= 20; i++) {
            final int taskId = i;
            pool.submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(100);
        }

        monitor.join();
        pool.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 简单线程池
     */
    static class SimpleThreadPool {
        private final int corePoolSize;
        private final BlockingQueue<Runnable> taskQueue;
        private final WorkerThread[] workers;
        private volatile boolean running = false;
        
        private final AtomicInteger submittedCount = new AtomicInteger(0);
        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final AtomicInteger rejectedCount = new AtomicInteger(0);

        public SimpleThreadPool(int corePoolSize, int queueCapacity) {
            this.corePoolSize = corePoolSize;
            this.taskQueue = new LinkedBlockingQueue<>(queueCapacity);
            this.workers = new WorkerThread[corePoolSize];
            
            for (int i = 0; i < corePoolSize; i++) {
                workers[i] = new WorkerThread(i);
            }
        }

        /**
         * 启动线程池
         */
        public void start() {
            running = true;
            for (WorkerThread worker : workers) {
                worker.start();
            }
        }

        /**
         * 提交任务（阻塞）
         */
        public void submit(Runnable task) throws InterruptedException {
            if (!running) {
                throw new IllegalStateException("线程池已停止");
            }
            taskQueue.put(task);
            submittedCount.incrementAndGet();
        }

        /**
         * 尝试提交任务（非阻塞）
         */
        public boolean trySubmit(Runnable task, long timeoutMs) throws InterruptedException {
            if (!running) {
                return false;
            }
            
            boolean success = taskQueue.offer(task, timeoutMs, TimeUnit.MILLISECONDS);
            if (success) {
                submittedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
            }
            return success;
        }

        /**
         * 停止线程池
         */
        public void shutdown() throws InterruptedException {
            System.out.println("\n停止线程池...");
            running = false;
            
            // 等待队列清空
            while (!taskQueue.isEmpty()) {
                Thread.sleep(100);
            }
            
            // 停止工作线程
            for (WorkerThread worker : workers) {
                worker.interrupt();
                worker.join(1000);
            }
            
            System.out.println("线程池已停止");
            printStatistics();
        }

        /**
         * 打印统计信息
         */
        public void printStatistics() {
            System.out.println(String.format(
                "[监控] 提交:%d, 完成:%d, 拒绝:%d, 队列:%d",
                submittedCount.get(),
                completedCount.get(),
                rejectedCount.get(),
                taskQueue.size()
            ));
        }

        /**
         * 工作线程
         */
        class WorkerThread extends Thread {
            private final int workerId;

            public WorkerThread(int workerId) {
                super("Worker-" + workerId);
                this.workerId = workerId;
            }

            @Override
            public void run() {
                System.out.println("[Worker-" + workerId + "] 启动");
                
                while (running || !taskQueue.isEmpty()) {
                    try {
                        Runnable task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (task != null) {
                            task.run();
                            completedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[Worker-" + workerId + "] 任务执行异常: " + 
                                         e.getMessage());
                    }
                }
                
                System.out.println("[Worker-" + workerId + "] 退出");
            }
        }
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
