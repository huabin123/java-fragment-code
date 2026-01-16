package com.fragment.juc.queue.practice;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务调度系统实战
 * 
 * <p>场景：基于DelayQueue实现的轻量级任务调度器
 * <ul>
 *   <li>支持延迟执行</li>
 *   <li>支持周期执行</li>
 *   <li>支持任务优先级</li>
 *   <li>支持任务取消</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>DelayQueue实现延迟调度</li>
 *   <li>周期任务的重新入队</li>
 *   <li>任务状态管理</li>
 *   <li>优雅停止机制</li>
 * </ul>
 * 
 * @author fragment
 */
public class TaskSchedulingSystem {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 任务调度系统实战 ==========\n");

        SimpleScheduler scheduler = new SimpleScheduler(2);
        scheduler.start();

        // 场景1：延迟执行
        System.out.println("=== 场景1：延迟执行 ===");
        scheduler.schedule(() -> {
            System.out.println("[延迟任务] 1秒后执行");
        }, 1000);

        scheduler.schedule(() -> {
            System.out.println("[延迟任务] 2秒后执行");
        }, 2000);

        Thread.sleep(3000);

        // 场景2：周期执行
        System.out.println("\n=== 场景2：周期执行 ===");
        ScheduledTask periodicTask = scheduler.scheduleAtFixedRate(() -> {
            System.out.println("[周期任务] 每1秒执行一次，时间: " + System.currentTimeMillis());
        }, 0, 1000);

        Thread.sleep(5000);

        // 取消周期任务
        System.out.println("\n取消周期任务");
        periodicTask.cancel();

        Thread.sleep(2000);

        // 场景3：批量任务
        System.out.println("\n=== 场景3：批量任务 ===");
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            scheduler.schedule(() -> {
                System.out.println("[批量任务-" + taskId + "] 执行");
            }, i * 500);
        }

        Thread.sleep(3000);

        // 优雅停止
        System.out.println("\n=== 优雅停止 ===");
        scheduler.shutdown();

        // 打印统计
        scheduler.printStatistics();
    }

    /**
     * 简单调度器
     */
    static class SimpleScheduler {
        // 任务队列
        private final DelayQueue<ScheduledTask> taskQueue = new DelayQueue<>();
        
        // 工作线程
        private final SchedulerThread[] workers;
        
        // 运行状态
        private volatile boolean running = false;
        
        // 任务ID生成器
        private final AtomicLong taskIdGenerator = new AtomicLong(0);
        
        // 统计信息
        private final AtomicInteger scheduledCount = new AtomicInteger(0);
        private final AtomicInteger executedCount = new AtomicInteger(0);
        private final AtomicInteger cancelledCount = new AtomicInteger(0);

        public SimpleScheduler(int workerCount) {
            this.workers = new SchedulerThread[workerCount];
            for (int i = 0; i < workerCount; i++) {
                workers[i] = new SchedulerThread(i);
            }
        }

        /**
         * 启动调度器
         */
        public void start() {
            if (running) {
                return;
            }
            
            running = true;
            System.out.println("启动调度器，工作线程数: " + workers.length + "\n");
            
            for (SchedulerThread worker : workers) {
                worker.start();
            }
        }

        /**
         * 延迟执行
         */
        public ScheduledTask schedule(Runnable task, long delay) {
            if (!running) {
                throw new IllegalStateException("调度器未启动");
            }
            
            long taskId = taskIdGenerator.incrementAndGet();
            ScheduledTask scheduledTask = new ScheduledTask(
                taskId, task, delay, 0, TaskType.ONE_TIME
            );
            
            taskQueue.put(scheduledTask);
            scheduledCount.incrementAndGet();
            
            return scheduledTask;
        }

        /**
         * 周期执行（固定速率）
         */
        public ScheduledTask scheduleAtFixedRate(Runnable task, long initialDelay, long period) {
            if (!running) {
                throw new IllegalStateException("调度器未启动");
            }
            
            if (period <= 0) {
                throw new IllegalArgumentException("周期必须大于0");
            }
            
            long taskId = taskIdGenerator.incrementAndGet();
            ScheduledTask scheduledTask = new ScheduledTask(
                taskId, task, initialDelay, period, TaskType.PERIODIC
            );
            
            taskQueue.put(scheduledTask);
            scheduledCount.incrementAndGet();
            
            return scheduledTask;
        }

        /**
         * 优雅停止
         */
        public void shutdown() throws InterruptedException {
            System.out.println("停止接收新任务...");
            running = false;

            System.out.println("等待队列中的任务执行完成...");
            System.out.println("当前队列大小: " + taskQueue.size());

            // 等待队列清空（最多10秒）
            long start = System.currentTimeMillis();
            while (!taskQueue.isEmpty() && System.currentTimeMillis() - start < 10000) {
                Thread.sleep(100);
            }

            // 停止工作线程
            System.out.println("停止工作线程...");
            for (SchedulerThread worker : workers) {
                worker.shutdown();
            }

            // 等待工作线程结束
            for (SchedulerThread worker : workers) {
                worker.join(1000);
            }

            System.out.println("调度器已停止");
        }

        /**
         * 打印统计信息
         */
        public void printStatistics() {
            System.out.println("\n========== 统计信息 ==========");
            System.out.println("调度任务数: " + scheduledCount.get());
            System.out.println("执行任务数: " + executedCount.get());
            System.out.println("取消任务数: " + cancelledCount.get());
            System.out.println("队列剩余: " + taskQueue.size());
            
            for (SchedulerThread worker : workers) {
                System.out.println("工作线程-" + worker.workerId + " 执行: " + 
                                 worker.executedCount.get());
            }
        }

        /**
         * 工作线程
         */
        class SchedulerThread extends Thread {
            private final int workerId;
            private volatile boolean running = true;
            private final AtomicInteger executedCount = new AtomicInteger(0);

            public SchedulerThread(int workerId) {
                super("Scheduler-Worker-" + workerId);
                this.workerId = workerId;
            }

            @Override
            public void run() {
                System.out.println("[工作线程-" + workerId + "] 启动");

                while (running || !taskQueue.isEmpty()) {
                    try {
                        // 获取到期任务
                        ScheduledTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                        
                        if (task != null) {
                            // 检查任务状态
                            if (task.isCancelled()) {
                                System.out.println("[工作线程-" + workerId + "] 任务已取消: " + task.getTaskId());
                                cancelledCount.incrementAndGet();
                                continue;
                            }
                            
                            // 执行任务
                            executeTask(task);
                            
                            // 如果是周期任务，重新入队
                            if (task.isPeriodic() && !task.isCancelled()) {
                                task.updateNextRunTime();
                                taskQueue.put(task);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[工作线程-" + workerId + "] 异常: " + e.getMessage());
                    }
                }

                System.out.println("[工作线程-" + workerId + "] 退出，共执行 " + 
                                 executedCount.get() + " 个任务");
            }

            /**
             * 执行任务
             */
            private void executeTask(ScheduledTask task) {
                try {
                    task.run();
                    executedCount.incrementAndGet();
                    SimpleScheduler.this.executedCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("[工作线程-" + workerId + "] 任务执行异常: " + e.getMessage());
                }
            }

            /**
             * 停止工作线程
             */
            public void shutdown() {
                running = false;
                this.interrupt();
            }
        }
    }

    /**
     * 调度任务
     */
    static class ScheduledTask implements Delayed, Runnable {
        private final long taskId;
        private final Runnable task;
        private long nextRunTime;
        private final long period;
        private final TaskType type;
        private volatile boolean cancelled = false;

        public ScheduledTask(long taskId, Runnable task, long delay, long period, TaskType type) {
            this.taskId = taskId;
            this.task = task;
            this.nextRunTime = System.currentTimeMillis() + delay;
            this.period = period;
            this.type = type;
        }

        public long getTaskId() {
            return taskId;
        }

        public boolean isPeriodic() {
            return type == TaskType.PERIODIC;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            this.cancelled = true;
        }

        public void updateNextRunTime() {
            this.nextRunTime = System.currentTimeMillis() + period;
        }

        @Override
        public void run() {
            if (!cancelled) {
                task.run();
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = nextRunTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.nextRunTime, ((ScheduledTask) o).nextRunTime);
        }
    }

    /**
     * 任务类型
     */
    enum TaskType {
        ONE_TIME,   // 一次性任务
        PERIODIC    // 周期任务
    }
}
