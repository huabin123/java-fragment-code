package com.fragment.core.threadpool.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易版线程池实现
 * 
 * <p>核心功能：
 * <ul>
 *   <li>固定数量的工作线程</li>
 *   <li>任务队列（阻塞队列）</li>
 *   <li>任务提交（execute方法）</li>
 *   <li>优雅关闭（shutdown方法）</li>
 *   <li>拒绝策略</li>
 * </ul>
 * 
 * @author fragment
 */
public class SimpleThreadPool {
    
    /** 任务队列 */
    private final BlockingQueue<Runnable> taskQueue;
    
    /** 工作线程列表 */
    private final List<Thread> workers;
    
    /** 线程数量 */
    private final int poolSize;
    
    /** 拒绝策略 */
    private final RejectedExecutionHandler handler;
    
    /** 是否已关闭 */
    private volatile boolean isShutdown = false;
    
    /** 已完成任务数 */
    private final AtomicInteger completedTaskCount = new AtomicInteger(0);
    
    /** 提交的任务总数 */
    private final AtomicInteger submittedTaskCount = new AtomicInteger(0);
    
    /**
     * 构造函数
     * 
     * @param poolSize 线程数量
     * @param queueCapacity 队列容量
     * @param handler 拒绝策略
     */
    public SimpleThreadPool(int poolSize, 
                           int queueCapacity, 
                           RejectedExecutionHandler handler) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("线程数必须大于0");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("队列容量必须大于0");
        }
        
        this.poolSize = poolSize;
        this.taskQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.workers = new ArrayList<>(poolSize);
        this.handler = handler != null ? handler : new AbortPolicy();
        
        // 创建并启动工作线程
        for (int i = 0; i < poolSize; i++) {
            Worker worker = new Worker();
            Thread thread = new Thread(worker, "SimpleThreadPool-Worker-" + i);
            thread.start();
            workers.add(thread);
        }
        
        System.out.println("线程池已创建，线程数: " + poolSize + 
                         ", 队列容量: " + queueCapacity);
    }
    
    /**
     * 提交任务
     * 
     * @param task 任务
     */
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("任务不能为空");
        }
        
        if (isShutdown) {
            throw new RuntimeException("线程池已关闭，无法提交新任务");
        }
        
        submittedTaskCount.incrementAndGet();
        
        // 尝试将任务加入队列
        boolean offered = taskQueue.offer(task);
        
        if (!offered) {
            // 队列已满，执行拒绝策略
            handler.rejectedExecution(task, this);
        }
    }
    
    /**
     * 关闭线程池（不再接受新任务，等待已提交的任务完成）
     */
    public void shutdown() {
        if (isShutdown) {
            return;
        }
        
        isShutdown = true;
        
        // 中断所有工作线程
        for (Thread worker : workers) {
            worker.interrupt();
        }
        
        System.out.println("线程池已关闭");
    }
    
    /**
     * 等待所有任务完成
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否在超时时间内完成
     */
    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        
        for (Thread worker : workers) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = timeoutMillis - elapsed;
            
            if (remaining <= 0) {
                return false;
            }
            
            worker.join(remaining);
        }
        
        return true;
    }
    
    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return taskQueue.size();
    }
    
    /**
     * 获取已完成任务数
     */
    public int getCompletedTaskCount() {
        return completedTaskCount.get();
    }
    
    /**
     * 获取已提交任务数
     */
    public int getSubmittedTaskCount() {
        return submittedTaskCount.get();
    }
    
    /**
     * 获取线程池大小
     */
    public int getPoolSize() {
        return poolSize;
    }
    
    /**
     * 是否已关闭
     */
    public boolean isShutdown() {
        return isShutdown;
    }
    
    /**
     * 获取线程池状态
     */
    public String getStatus() {
        return String.format(
            "线程池状态 [线程数: %d, 队列大小: %d, 已提交: %d, 已完成: %d, 已关闭: %s]",
            poolSize,
            getQueueSize(),
            getSubmittedTaskCount(),
            getCompletedTaskCount(),
            isShutdown
        );
    }
    
    /**
     * 工作线程
     */
    private class Worker implements Runnable {
        
        @Override
        public void run() {
            System.out.println("Worker线程启动: " + Thread.currentThread().getName());
            
            // 循环从队列获取任务并执行
            while (!isShutdown || !taskQueue.isEmpty()) {
                try {
                    // 从队列获取任务（阻塞等待）
                    Runnable task = taskQueue.take();
                    
                    // 执行任务
                    try {
                        task.run();
                        completedTaskCount.incrementAndGet();
                    } catch (Exception e) {
                        // 捕获任务执行异常，防止Worker线程退出
                        System.err.println("任务执行失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                } catch (InterruptedException e) {
                    // 线程被中断，检查是否应该退出
                    if (isShutdown) {
                        break;
                    }
                }
            }
            
            System.out.println("Worker线程退出: " + Thread.currentThread().getName());
        }
    }
    
    /**
     * 拒绝策略接口
     */
    public interface RejectedExecutionHandler {
        /**
         * 处理被拒绝的任务
         * 
         * @param task 被拒绝的任务
         * @param executor 线程池
         */
        void rejectedExecution(Runnable task, SimpleThreadPool executor);
    }
    
    /**
     * 抛出异常策略（默认）
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable task, SimpleThreadPool executor) {
            throw new RuntimeException("任务被拒绝: " + task + ", " + executor.getStatus());
        }
    }
    
    /**
     * 调用者执行策略
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable task, SimpleThreadPool executor) {
            if (!executor.isShutdown()) {
                System.out.println("队列已满，调用者执行任务: " + Thread.currentThread().getName());
                task.run();
            }
        }
    }
    
    /**
     * 丢弃策略
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable task, SimpleThreadPool executor) {
            System.out.println("队列已满，丢弃任务: " + task);
        }
    }
    
    /**
     * 丢弃最老任务策略
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable task, SimpleThreadPool executor) {
            if (!executor.isShutdown()) {
                // 移除队列头部任务
                Runnable oldest = executor.taskQueue.poll();
                System.out.println("队列已满，丢弃最老任务: " + oldest);
                // 重新提交新任务
                executor.taskQueue.offer(task);
            }
        }
    }
}
