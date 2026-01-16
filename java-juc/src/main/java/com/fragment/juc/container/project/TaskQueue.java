package com.fragment.juc.container.project;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务队列系统实战 - 基于阻塞队列的任务调度系统
 * 
 * <p>功能特性：
 * <ul>
 *   <li>支持优先级任务</li>
 *   <li>支持任务重试</li>
 *   <li>支持任务超时</li>
 *   <li>任务执行统计</li>
 *   <li>工作线程池</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>PriorityBlockingQueue实现优先级队列</li>
 *   <li>LinkedBlockingQueue实现普通队列</li>
 *   <li>ConcurrentHashMap存储任务状态</li>
 * </ul>
 * 
 * <p>应用场景：
 * <ul>
 *   <li>异步任务处理</li>
 *   <li>批量任务调度</li>
 *   <li>后台任务系统</li>
 * </ul>
 * 
 * @author fragment
 */
public class TaskQueue {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 任务队列系统实战 ==========\n");

        // 场景1：基本任务队列
        demonstrateBasicTaskQueue();

        Thread.sleep(1000);

        // 场景2：优先级任务队列
        demonstratePriorityTaskQueue();

        Thread.sleep(1000);

        // 场景3：任务重试机制
        demonstrateRetryTaskQueue();

        Thread.sleep(1000);

        // 场景4：任务统计监控
        demonstrateTaskStatistics();
    }

    /**
     * 场景1：基本任务队列
     */
    private static void demonstrateBasicTaskQueue() throws InterruptedException {
        System.out.println("=== 场景1：基本任务队列 ===\n");

        SimpleTaskQueue taskQueue = new SimpleTaskQueue(3);
        taskQueue.start();

        // 提交任务
        System.out.println("提交10个任务:");
        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            taskQueue.submit(new Task() {
                @Override
                public void execute() {
                    System.out.println("[Worker-" + Thread.currentThread().getName() + 
                                     "] 执行任务" + taskId);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        Thread.sleep(6000);
        taskQueue.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：优先级任务队列
     */
    private static void demonstratePriorityTaskQueue() throws InterruptedException {
        System.out.println("=== 场景2：优先级任务队列 ===\n");

        PriorityTaskQueue taskQueue = new PriorityTaskQueue(2);
        taskQueue.start();

        // 提交不同优先级的任务
        System.out.println("提交任务（优先级: 1=高, 5=低）:");
        taskQueue.submit(new PriorityTask("任务A", 3));
        taskQueue.submit(new PriorityTask("任务B", 1));  // 高优先级
        taskQueue.submit(new PriorityTask("任务C", 5));  // 低优先级
        taskQueue.submit(new PriorityTask("任务D", 2));
        taskQueue.submit(new PriorityTask("任务E", 4));

        Thread.sleep(4000);
        taskQueue.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：任务重试机制
     */
    private static void demonstrateRetryTaskQueue() throws InterruptedException {
        System.out.println("=== 场景3：任务重试机制 ===\n");

        RetryTaskQueue taskQueue = new RetryTaskQueue(2, 3);
        taskQueue.start();

        // 提交会失败的任务
        System.out.println("提交任务（模拟失败）:");
        taskQueue.submit(new RetryableTask("任务1", 2));  // 失败2次后成功
        taskQueue.submit(new RetryableTask("任务2", 1));  // 失败1次后成功
        taskQueue.submit(new RetryableTask("任务3", 5));  // 超过重试次数

        Thread.sleep(5000);
        taskQueue.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景4：任务统计监控
     */
    private static void demonstrateTaskStatistics() throws InterruptedException {
        System.out.println("=== 场景4：任务统计监控 ===\n");

        StatisticsTaskQueue taskQueue = new StatisticsTaskQueue(3);
        taskQueue.start();

        // 提交任务
        System.out.println("提交20个任务:");
        for (int i = 1; i <= 20; i++) {
            final int taskId = i;
            taskQueue.submit(new Task() {
                @Override
                public void execute() {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // 定期打印统计
        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);
            taskQueue.printStatistics();
        }

        taskQueue.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 任务接口
     */
    interface Task {
        void execute();
    }

    /**
     * 简单任务队列
     */
    static class SimpleTaskQueue {
        private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
        private final Thread[] workers;
        private volatile boolean running = false;

        public SimpleTaskQueue(int workerCount) {
            this.workers = new Thread[workerCount];
            for (int i = 0; i < workerCount; i++) {
                workers[i] = new WorkerThread(i);
            }
        }

        public void start() {
            running = true;
            for (Thread worker : workers) {
                worker.start();
            }
        }

        public void submit(Task task) {
            try {
                taskQueue.put(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void shutdown() throws InterruptedException {
            running = false;
            for (Thread worker : workers) {
                worker.interrupt();
                worker.join(1000);
            }
            System.out.println("任务队列已停止");
        }

        class WorkerThread extends Thread {
            private final int workerId;

            public WorkerThread(int workerId) {
                super("Worker-" + workerId);
                this.workerId = workerId;
            }

            @Override
            public void run() {
                while (running || !taskQueue.isEmpty()) {
                    try {
                        Task task = taskQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (task != null) {
                            task.execute();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[Worker-" + workerId + "] 任务执行异常: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 优先级任务队列
     */
    static class PriorityTaskQueue {
        private final BlockingQueue<PriorityTask> taskQueue = new PriorityBlockingQueue<>();
        private final Thread[] workers;
        private volatile boolean running = false;

        public PriorityTaskQueue(int workerCount) {
            this.workers = new Thread[workerCount];
            for (int i = 0; i < workerCount; i++) {
                workers[i] = new WorkerThread(i);
            }
        }

        public void start() {
            running = true;
            for (Thread worker : workers) {
                worker.start();
            }
        }

        public void submit(PriorityTask task) {
            taskQueue.offer(task);
        }

        public void shutdown() throws InterruptedException {
            running = false;
            for (Thread worker : workers) {
                worker.interrupt();
                worker.join(1000);
            }
            System.out.println("优先级任务队列已停止");
        }

        class WorkerThread extends Thread {
            private final int workerId;

            public WorkerThread(int workerId) {
                super("Worker-" + workerId);
                this.workerId = workerId;
            }

            @Override
            public void run() {
                while (running || !taskQueue.isEmpty()) {
                    try {
                        PriorityTask task = taskQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (task != null) {
                            System.out.println("[Worker-" + workerId + "] 执行: " + 
                                             task.getName() + " (优先级" + task.getPriority() + ")");
                            task.execute();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 重试任务队列
     */
    static class RetryTaskQueue {
        private final BlockingQueue<RetryableTask> taskQueue = new LinkedBlockingQueue<>();
        private final Thread[] workers;
        private final int maxRetries;
        private volatile boolean running = false;

        public RetryTaskQueue(int workerCount, int maxRetries) {
            this.workers = new Thread[workerCount];
            this.maxRetries = maxRetries;
            for (int i = 0; i < workerCount; i++) {
                workers[i] = new WorkerThread(i);
            }
        }

        public void start() {
            running = true;
            for (Thread worker : workers) {
                worker.start();
            }
        }

        public void submit(RetryableTask task) {
            try {
                taskQueue.put(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void shutdown() throws InterruptedException {
            running = false;
            for (Thread worker : workers) {
                worker.interrupt();
                worker.join(1000);
            }
            System.out.println("重试任务队列已停止");
        }

        class WorkerThread extends Thread {
            private final int workerId;

            public WorkerThread(int workerId) {
                super("Worker-" + workerId);
                this.workerId = workerId;
            }

            @Override
            public void run() {
                while (running || !taskQueue.isEmpty()) {
                    try {
                        RetryableTask task = taskQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        if (task != null) {
                            try {
                                System.out.println("[Worker-" + workerId + "] 执行: " + 
                                                 task.getName() + " (第" + (task.getRetryCount() + 1) + "次)");
                                task.execute();
                                System.out.println("[Worker-" + workerId + "] 成功: " + task.getName());
                            } catch (Exception e) {
                                task.incrementRetryCount();
                                if (task.getRetryCount() < maxRetries) {
                                    System.out.println("[Worker-" + workerId + "] 失败，重试: " + 
                                                     task.getName());
                                    taskQueue.put(task);  // 重新入队
                                } else {
                                    System.out.println("[Worker-" + workerId + "] 失败，放弃: " + 
                                                     task.getName());
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 统计任务队列
     */
    static class StatisticsTaskQueue extends SimpleTaskQueue {
        private final AtomicInteger submittedCount = new AtomicInteger(0);
        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final AtomicInteger failedCount = new AtomicInteger(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);

        public StatisticsTaskQueue(int workerCount) {
            super(workerCount);
        }

        @Override
        public void submit(Task task) {
            submittedCount.incrementAndGet();
            super.submit(new Task() {
                @Override
                public void execute() {
                    long start = System.currentTimeMillis();
                    try {
                        task.execute();
                        completedCount.incrementAndGet();
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                    } finally {
                        long duration = System.currentTimeMillis() - start;
                        totalExecutionTime.addAndGet(duration);
                    }
                }
            });
        }

        public void printStatistics() {
            int completed = completedCount.get();
            long avgTime = completed > 0 ? totalExecutionTime.get() / completed : 0;
            
            System.out.println("\n[统计] 提交:" + submittedCount.get() + 
                             ", 完成:" + completed + 
                             ", 失败:" + failedCount.get() + 
                             ", 平均耗时:" + avgTime + "ms");
        }
    }

    /**
     * 优先级任务
     */
    static class PriorityTask implements Comparable<PriorityTask>, Task {
        private final String name;
        private final int priority;  // 数字越小优先级越高

        public PriorityTask(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public int getPriority() {
            return priority;
        }

        @Override
        public void execute() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public int compareTo(PriorityTask o) {
            return Integer.compare(this.priority, o.priority);
        }
    }

    /**
     * 可重试任务
     */
    static class RetryableTask implements Task {
        private final String name;
        private final int failTimes;  // 模拟失败次数
        private int retryCount = 0;

        public RetryableTask(String name, int failTimes) {
            this.name = name;
            this.failTimes = failTimes;
        }

        public String getName() {
            return name;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void incrementRetryCount() {
            retryCount++;
        }

        @Override
        public void execute() {
            if (retryCount < failTimes) {
                throw new RuntimeException("模拟失败");
            }
            // 成功执行
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
