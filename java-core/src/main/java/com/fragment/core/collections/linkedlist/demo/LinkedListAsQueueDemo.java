package com.fragment.core.collections.linkedlist.demo;

import java.util.LinkedList;
import java.util.Queue;

/**
 * LinkedList作为队列使用演示
 * 
 * 演示内容：
 * 1. Queue接口的基本操作
 * 2. 队列的FIFO特性
 * 3. offer/poll vs add/remove
 * 4. 实际应用场景
 * 
 * @author huabin
 */
public class LinkedListAsQueueDemo {

    public static void main(String[] args) {
        System.out.println("========== LinkedList作为队列使用演示 ==========\n");
        
        // 1. Queue接口的基本操作
        basicQueueOperations();
        
        // 2. offer/poll vs add/remove
        offerPollVsAddRemove();
        
        // 3. 实际应用：任务队列
        taskQueueExample();
    }

    /**
     * 1. Queue接口的基本操作
     */
    private static void basicQueueOperations() {
        System.out.println("1. Queue接口的基本操作");
        System.out.println("----------------------------------------");
        
        Queue<String> queue = new LinkedList<>();
        
        // offer：入队（添加到尾部）
        queue.offer("任务1");
        queue.offer("任务2");
        queue.offer("任务3");
        System.out.println("入队后: " + queue);
        
        // peek：查看队首元素（不删除）
        String first = queue.peek();
        System.out.println("peek: " + first + ", 队列: " + queue);
        
        // poll：出队（删除队首元素）
        String polled = queue.poll();
        System.out.println("poll: " + polled + ", 队列: " + queue);
        
        // size：队列大小
        System.out.println("size: " + queue.size());
        
        // isEmpty：是否为空
        System.out.println("isEmpty: " + queue.isEmpty());
        
        System.out.println();
    }

    /**
     * 2. offer/poll vs add/remove
     */
    private static void offerPollVsAddRemove() {
        System.out.println("2. offer/poll vs add/remove");
        System.out.println("----------------------------------------");
        
        Queue<String> queue = new LinkedList<>();
        
        System.out.println("offer vs add:");
        boolean offered = queue.offer("A");
        System.out.println("  offer(A): " + offered);
        
        boolean added = queue.add("B");
        System.out.println("  add(B): " + added);
        
        System.out.println("  队列: " + queue);
        
        System.out.println("\npoll vs remove:");
        String polled = queue.poll();
        System.out.println("  poll(): " + polled);
        
        String removed = queue.remove();
        System.out.println("  remove(): " + removed);
        
        System.out.println("  队列: " + queue);
        
        System.out.println("\npeek vs element:");
        queue.offer("C");
        String peeked = queue.peek();
        System.out.println("  peek(): " + peeked);
        
        String element = queue.element();
        System.out.println("  element(): " + element);
        
        System.out.println("\n空队列时的区别:");
        queue.clear();
        
        String pollEmpty = queue.poll();
        System.out.println("  poll()返回: " + pollEmpty + "（不抛异常）");
        
        String peekEmpty = queue.peek();
        System.out.println("  peek()返回: " + peekEmpty + "（不抛异常）");
        
        try {
            queue.remove();
        } catch (Exception e) {
            System.out.println("  remove()抛出异常: " + e.getClass().getSimpleName());
        }
        
        try {
            queue.element();
        } catch (Exception e) {
            System.out.println("  element()抛出异常: " + e.getClass().getSimpleName());
        }
        
        System.out.println("\n推荐使用offer/poll/peek（返回特殊值，不抛异常）");
        
        System.out.println();
    }

    /**
     * 3. 实际应用：任务队列
     */
    private static void taskQueueExample() {
        System.out.println("3. 实际应用：任务队列");
        System.out.println("----------------------------------------");
        
        TaskQueue taskQueue = new TaskQueue();
        
        // 添加任务
        taskQueue.addTask(new Task("下载文件", 1));
        taskQueue.addTask(new Task("处理数据", 2));
        taskQueue.addTask(new Task("发送邮件", 3));
        
        // 处理任务
        System.out.println("\n开始处理任务:");
        taskQueue.processTasks();
        
        System.out.println();
    }

    /**
     * 任务类
     */
    static class Task {
        String name;
        int priority;

        Task(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        void execute() {
            System.out.println("  执行任务: " + name + "（优先级: " + priority + "）");
            try {
                Thread.sleep(100);  // 模拟任务执行
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return name + "(优先级:" + priority + ")";
        }
    }

    /**
     * 任务队列
     */
    static class TaskQueue {
        private final Queue<Task> queue = new LinkedList<>();

        void addTask(Task task) {
            queue.offer(task);
            System.out.println("添加任务: " + task);
        }

        void processTasks() {
            while (!queue.isEmpty()) {
                Task task = queue.poll();
                if (task != null) {
                    task.execute();
                }
            }
            System.out.println("所有任务处理完成");
        }

        int size() {
            return queue.size();
        }
    }
}
