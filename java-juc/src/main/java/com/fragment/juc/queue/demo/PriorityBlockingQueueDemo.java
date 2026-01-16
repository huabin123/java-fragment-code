package com.fragment.juc.queue.demo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * PriorityBlockingQueue 演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>优先级排序机制</li>
 *   <li>堆结构维护</li>
 *   <li>自动扩容</li>
 *   <li>Comparable vs Comparator</li>
 *   <li>优先级任务调度</li>
 * </ul>
 * 
 * @author fragment
 */
public class PriorityBlockingQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== PriorityBlockingQueue 演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 优先级排序演示
        demonstratePriorityOrdering();

        // 3. 自动扩容演示
        demonstrateAutoExpansion();

        // 4. Comparator使用
        demonstrateComparator();

        // 5. 优先级任务调度
        demonstratePriorityTaskScheduling();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() throws InterruptedException {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: 无界队列，自动按优先级排序\n");

        BlockingQueue<Integer> queue = new PriorityBlockingQueue<>();

        // 添加元素（乱序）
        System.out.println("=== 添加元素 ===");
        queue.put(5);
        queue.put(1);
        queue.put(3);
        queue.put(2);
        queue.put(4);
        System.out.println("添加顺序: 5, 1, 3, 2, 4");
        System.out.println("队列大小: " + queue.size());

        // 查看队头
        System.out.println("\n=== 查看队头 ===");
        Integer head = queue.peek();
        System.out.println("队头元素: " + head + " (最小值)");

        // 按优先级取出
        System.out.println("\n=== 按优先级取出 ===");
        while (!queue.isEmpty()) {
            Integer item = queue.take();
            System.out.println("取出: " + item);
        }

        System.out.println("\n关键点: 自动按优先级排序，取出时总是最小值");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 优先级排序演示
     */
    private static void demonstratePriorityOrdering() throws InterruptedException {
        System.out.println("2. 优先级排序演示");
        System.out.println("特点: 使用Comparable接口排序\n");

        BlockingQueue<Task> queue = new PriorityBlockingQueue<>();

        // 添加不同优先级的任务
        System.out.println("=== 添加任务 ===");
        queue.put(new Task("低优先级任务", 5));
        System.out.println("添加: 低优先级任务 (优先级5)");
        
        queue.put(new Task("高优先级任务", 1));
        System.out.println("添加: 高优先级任务 (优先级1)");
        
        queue.put(new Task("中优先级任务", 3));
        System.out.println("添加: 中优先级任务 (优先级3)");

        // 按优先级执行
        System.out.println("\n=== 按优先级执行 ===");
        while (!queue.isEmpty()) {
            Task task = queue.take();
            System.out.println("执行: " + task.getName() + " (优先级" + task.getPriority() + ")");
        }

        System.out.println("\n关键点: 优先级数字越小，优先级越高");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 自动扩容演示
     */
    private static void demonstrateAutoExpansion() {
        System.out.println("3. 自动扩容演示");
        System.out.println("特点: 无界队列，自动扩容\n");

        // 初始容量11
        BlockingQueue<Integer> queue = new PriorityBlockingQueue<>(11);
        System.out.println("初始容量: 11");

        // 添加超过初始容量的元素
        System.out.println("\n=== 添加100个元素 ===");
        for (int i = 0; i < 100; i++) {
            queue.offer(i);
        }
        System.out.println("成功添加100个元素");
        System.out.println("队列大小: " + queue.size());

        System.out.println("\n关键点: 自动扩容，无需担心容量限制");
        System.out.println("扩容策略: 小容量(<64)翻倍+2，大容量增加50%");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. Comparator使用
     */
    private static void demonstrateComparator() throws InterruptedException {
        System.out.println("4. Comparator使用");
        System.out.println("特点: 可以使用自定义Comparator\n");

        // 使用Comparator（降序）
        BlockingQueue<Integer> queue = new PriorityBlockingQueue<>(
            11, 
            (a, b) -> b - a  // 降序
        );

        System.out.println("=== 使用降序Comparator ===");
        queue.put(1);
        queue.put(5);
        queue.put(3);
        System.out.println("添加: 1, 5, 3");

        System.out.println("\n按降序取出:");
        while (!queue.isEmpty()) {
            System.out.println("取出: " + queue.take());
        }

        System.out.println("\n关键点: Comparator优先于Comparable");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 优先级任务调度
     */
    private static void demonstratePriorityTaskScheduling() throws InterruptedException {
        System.out.println("5. 优先级任务调度");
        System.out.println("场景: 多个任务按优先级执行\n");

        BlockingQueue<PriorityTask> taskQueue = new PriorityBlockingQueue<>();

        // 创建任务
        System.out.println("=== 创建任务 ===");
        taskQueue.put(new PriorityTask("数据备份", 3, 1000));
        taskQueue.put(new PriorityTask("紧急修复", 1, 500));
        taskQueue.put(new PriorityTask("日志清理", 5, 800));
        taskQueue.put(new PriorityTask("报表生成", 2, 1200));
        taskQueue.put(new PriorityTask("数据同步", 4, 600));

        System.out.println("创建5个任务，优先级分别为: 3, 1, 5, 2, 4");

        // 启动工作线程
        Thread worker = new Thread(() -> {
            while (!taskQueue.isEmpty()) {
                try {
                    PriorityTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        System.out.println("\n[工作线程] 开始执行: " + task.getName() + 
                                         " (优先级" + task.getPriority() + ")");
                        task.execute();
                        System.out.println("[工作线程] 完成: " + task.getName());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        worker.start();
        worker.join();

        System.out.println("\n关键点: 高优先级任务先执行，低优先级任务后执行");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 任务类（实现Comparable）
     */
    static class Task implements Comparable<Task> {
        private final String name;
        private final int priority;  // 数字越小优先级越高

        public Task(String name, int priority) {
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
        public int compareTo(Task o) {
            return Integer.compare(this.priority, o.priority);
        }
    }

    /**
     * 优先级任务
     */
    static class PriorityTask implements Comparable<PriorityTask> {
        private final String name;
        private final int priority;
        private final long duration;

        public PriorityTask(String name, int priority, long duration) {
            this.name = name;
            this.priority = priority;
            this.duration = duration;
        }

        public String getName() {
            return name;
        }

        public int getPriority() {
            return priority;
        }

        public void execute() {
            try {
                Thread.sleep(duration);
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
