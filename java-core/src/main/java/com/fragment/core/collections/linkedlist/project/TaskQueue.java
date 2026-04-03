package com.fragment.core.collections.linkedlist.project;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 任务队列实现
 * 
 * 使用LinkedList实现一个线程安全的任务队列
 * 
 * 特性：
 * 1. 支持优先级（高优先级任务插入到队首）
 * 2. 线程安全
 * 3. 支持阻塞等待
 * 4. 支持任务统计
 * 
 * @author huabin
 */
public class TaskQueue {
    
    private final LinkedList<Task> queue = new LinkedList<>();
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    
    private int totalAdded = 0;
    private int totalProcessed = 0;

    /**
     * 添加任务
     * 
     * @param task 任务
     */
    public void addTask(Task task) {
        lock.lock();
        try {
            if (task.priority == Priority.HIGH) {
                // 高优先级任务插入到队首
                queue.addFirst(task);
                System.out.println("[TaskQueue] 添加高优先级任务: " + task.name);
            } else {
                // 普通任务插入到队尾
                queue.addLast(task);
                System.out.println("[TaskQueue] 添加任务: " + task.name);
            }
            totalAdded++;
            notEmpty.signal();  // 通知等待的线程
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取下一个任务（阻塞）
     * 
     * @return 任务
     * @throws InterruptedException 中断异常
     */
    public Task takeTask() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();  // 等待任务
            }
            Task task = queue.removeFirst();
            totalProcessed++;
            System.out.println("[TaskQueue] 获取任务: " + task.name);
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取下一个任务（非阻塞）
     * 
     * @return 任务，如果队列为空返回null
     */
    public Task pollTask() {
        lock.lock();
        try {
            if (queue.isEmpty()) {
                return null;
            }
            Task task = queue.removeFirst();
            totalProcessed++;
            System.out.println("[TaskQueue] 获取任务: " + task.name);
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消最后一个任务
     * 
     * @return 被取消的任务，如果队列为空返回null
     */
    public Task cancelLastTask() {
        lock.lock();
        try {
            if (queue.isEmpty()) {
                return null;
            }
            Task task = queue.removeLast();
            System.out.println("[TaskQueue] 取消任务: " + task.name);
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取队列大小
     */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断队列是否为空
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 打印统计信息
     */
    public void printStats() {
        lock.lock();
        try {
            System.out.println("\n========== 任务队列统计 ==========");
            System.out.println("总添加任务数: " + totalAdded);
            System.out.println("总处理任务数: " + totalProcessed);
            System.out.println("待处理任务数: " + queue.size());
            System.out.println("================================\n");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 任务类
     */
    public static class Task {
        private final String name;
        private final Priority priority;
        private final Runnable runnable;

        public Task(String name, Priority priority, Runnable runnable) {
            this.name = name;
            this.priority = priority;
            this.runnable = runnable;
        }

        public void execute() {
            System.out.println("[Task] 开始执行: " + name);
            try {
                runnable.run();
                System.out.println("[Task] 执行完成: " + name);
            } catch (Exception e) {
                System.out.println("[Task] 执行失败: " + name + ", 错误: " + e.getMessage());
            }
        }

        public String getName() {
            return name;
        }

        public Priority getPriority() {
            return priority;
        }
    }

    /**
     * 优先级枚举
     */
    public enum Priority {
        HIGH,    // 高优先级
        NORMAL   // 普通优先级
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 任务队列测试 ==========\n");
        
        TaskQueue taskQueue = new TaskQueue();
        
        // 创建消费者线程
        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    Task task = taskQueue.takeTask();
                    task.execute();
                    Thread.sleep(500);  // 模拟任务执行时间
                }
            } catch (InterruptedException e) {
                System.out.println("[Consumer] 线程中断");
            }
        });
        consumer.setName("Consumer");
        consumer.start();
        
        // 添加普通任务
        taskQueue.addTask(new Task("任务1", Priority.NORMAL, () -> {
            System.out.println("  处理任务1");
        }));
        
        taskQueue.addTask(new Task("任务2", Priority.NORMAL, () -> {
            System.out.println("  处理任务2");
        }));
        
        taskQueue.addTask(new Task("任务3", Priority.NORMAL, () -> {
            System.out.println("  处理任务3");
        }));
        
        Thread.sleep(1000);
        
        // 添加高优先级任务
        taskQueue.addTask(new Task("紧急任务", Priority.HIGH, () -> {
            System.out.println("  处理紧急任务");
        }));
        
        Thread.sleep(2000);
        
        // 取消最后一个任务
        taskQueue.cancelLastTask();
        
        Thread.sleep(2000);
        
        // 打印统计信息
        taskQueue.printStats();
        
        // 停止消费者线程
        consumer.interrupt();
        
        System.out.println("测试完成");
    }
}
