package com.fragment.juc.container.demo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发队列演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>CAS无锁机制</li>
 *   <li>非阻塞操作</li>
 *   <li>松弛阈值优化</li>
 *   <li>高并发性能</li>
 *   <li>size()方法的注意事项</li>
 * </ul>
 * 
 * @author fragment
 */
public class ConcurrentQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 并发队列演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 非阻塞特性
        demonstrateNonBlocking();

        // 3. 高并发场景
        demonstrateHighConcurrency();

        // 4. size()方法陷阱
        demonstrateSizePitfall();

        // 5. 生产者-消费者模式
        demonstrateProducerConsumer();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: CAS无锁，非阻塞，无界队列\n");

        Queue<String> queue = new ConcurrentLinkedQueue<>();

        // offer - 添加元素
        System.out.println("=== offer操作 ===");
        queue.offer("A");
        queue.offer("B");
        queue.offer("C");
        System.out.println("添加3个元素: A, B, C");
        System.out.println("队列内容: " + queue);

        // peek - 查看队头
        System.out.println("\n=== peek操作 ===");
        String head = queue.peek();
        System.out.println("peek: " + head);
        System.out.println("队列大小: " + queue.size() + " (未改变)");

        // poll - 取出元素
        System.out.println("\n=== poll操作 ===");
        String item = queue.poll();
        System.out.println("poll: " + item);
        System.out.println("队列大小: " + queue.size());

        // isEmpty - 判空
        System.out.println("\n=== isEmpty操作 ===");
        System.out.println("isEmpty: " + queue.isEmpty());

        System.out.println("\n关键点: 所有操作都是非阻塞的，基于CAS实现");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 非阻塞特性演示
     */
    private static void demonstrateNonBlocking() {
        System.out.println("2. 非阻塞特性演示");
        System.out.println("特点: offer永远成功，poll返回null\n");

        Queue<Integer> queue = new ConcurrentLinkedQueue<>();

        // offer永远成功（无界队列）
        System.out.println("=== offer操作（无界） ===");
        for (int i = 0; i < 10000; i++) {
            boolean success = queue.offer(i);
            if (!success) {
                System.out.println("offer失败: " + i);
            }
        }
        System.out.println("成功添加10000个元素");
        System.out.println("队列大小: " + queue.size());

        // poll在队列空时返回null
        System.out.println("\n=== poll操作（队列空） ===");
        queue.clear();
        Integer item = queue.poll();
        System.out.println("空队列poll: " + item);
        System.out.println("不会阻塞，立即返回null");

        System.out.println("\n关键点: 非阻塞，适合不需要等待的场景");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 高并发场景演示
     */
    private static void demonstrateHighConcurrency() throws InterruptedException {
        System.out.println("3. 高并发场景演示");
        System.out.println("场景: 10个生产者 + 10个消费者，100万次操作\n");

        Queue<Integer> queue = new ConcurrentLinkedQueue<>();
        final int OPERATIONS = 100000;
        final int PRODUCER_COUNT = 10;
        final int CONSUMER_COUNT = 10;

        AtomicInteger produceCount = new AtomicInteger(0);
        AtomicInteger consumeCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(PRODUCER_COUNT + CONSUMER_COUNT);

        long start = System.currentTimeMillis();

        // 创建生产者
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            new Thread(() -> {
                for (int j = 0; j < OPERATIONS / PRODUCER_COUNT; j++) {
                    queue.offer(j);
                    produceCount.incrementAndGet();
                }
                latch.countDown();
            }, "Producer-" + i).start();
        }

        // 创建消费者
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            new Thread(() -> {
                int count = 0;
                while (count < OPERATIONS / CONSUMER_COUNT) {
                    Integer item = queue.poll();
                    if (item != null) {
                        consumeCount.incrementAndGet();
                        count++;
                    } else {
                        Thread.yield();  // 队列空，让出CPU
                    }
                }
                latch.countDown();
            }, "Consumer-" + i).start();
        }

        latch.await();
        long end = System.currentTimeMillis();

        System.out.println("=== 性能统计 ===");
        System.out.println("生产总数: " + produceCount.get());
        System.out.println("消费总数: " + consumeCount.get());
        System.out.println("剩余元素: " + queue.size());
        System.out.println("总耗时: " + (end - start) + "ms");
        System.out.println("吞吐量: " + (OPERATIONS * 2 * 1000 / (end - start)) + " ops/s");

        System.out.println("\n关键点: CAS无锁，高并发性能优秀");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. size()方法陷阱演示
     */
    private static void demonstrateSizePitfall() throws InterruptedException {
        System.out.println("4. size()方法陷阱演示");
        System.out.println("陷阱: size()是O(n)操作，高并发下不准确\n");

        Queue<Integer> queue = new ConcurrentLinkedQueue<>();

        // 添加元素
        for (int i = 0; i < 10000; i++) {
            queue.offer(i);
        }

        // 测试size()性能
        System.out.println("=== size()性能测试 ===");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            int size = queue.size();
        }
        long end = System.currentTimeMillis();
        System.out.println("调用size() 10000次，耗时: " + (end - start) + "ms");
        System.out.println("⚠️  size()需要遍历整个队列，O(n)复杂度");

        // 高并发下size()不准确
        System.out.println("\n=== 高并发下size()不准确 ===");
        queue.clear();

        Thread producer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                queue.offer(i);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread sizeChecker = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                System.out.println("当前size: " + queue.size());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        producer.start();
        sizeChecker.start();
        producer.join();
        sizeChecker.join();

        System.out.println("\n⚠️  最佳实践:");
        System.out.println("1. 避免频繁调用size()");
        System.out.println("2. 使用isEmpty()判断队列是否为空");
        System.out.println("3. 不要依赖size()的精确值");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 生产者-消费者模式演示
     */
    private static void demonstrateProducerConsumer() throws InterruptedException {
        System.out.println("5. 生产者-消费者模式演示");
        System.out.println("场景: 任务队列，多生产者多消费者\n");

        Queue<Task> taskQueue = new ConcurrentLinkedQueue<>();
        AtomicInteger taskIdGenerator = new AtomicInteger(0);
        AtomicInteger completedTasks = new AtomicInteger(0);

        final int PRODUCER_COUNT = 3;
        final int CONSUMER_COUNT = 5;
        final int TASKS_PER_PRODUCER = 10;

        CountDownLatch producerLatch = new CountDownLatch(PRODUCER_COUNT);
        CountDownLatch consumerLatch = new CountDownLatch(CONSUMER_COUNT);

        // 启动生产者
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            final int producerId = i;
            new Thread(() -> {
                for (int j = 0; j < TASKS_PER_PRODUCER; j++) {
                    int taskId = taskIdGenerator.incrementAndGet();
                    Task task = new Task(taskId, "Producer-" + producerId);
                    taskQueue.offer(task);
                    System.out.println("[Producer-" + producerId + "] 生产任务: " + taskId);
                    
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                producerLatch.countDown();
            }, "Producer-" + i).start();
        }

        // 启动消费者
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            final int consumerId = i;
            new Thread(() -> {
                while (true) {
                    Task task = taskQueue.poll();
                    if (task != null) {
                        System.out.println("[Consumer-" + consumerId + "] 执行任务: " + 
                                         task.getId() + " (来自" + task.getProducer() + ")");
                        task.execute();
                        completedTasks.incrementAndGet();
                    } else {
                        // 检查是否所有生产者都完成
                        if (producerLatch.getCount() == 0) {
                            break;
                        }
                        Thread.yield();
                    }
                }
                consumerLatch.countDown();
            }, "Consumer-" + i).start();
        }

        // 等待完成
        producerLatch.await();
        consumerLatch.await();

        System.out.println("\n=== 统计 ===");
        System.out.println("总任务数: " + taskIdGenerator.get());
        System.out.println("完成任务数: " + completedTasks.get());
        System.out.println("剩余任务: " + taskQueue.size());

        System.out.println("\n关键点: 适合非阻塞的生产者-消费者场景");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 任务类
     */
    static class Task {
        private final int id;
        private final String producer;

        public Task(int id, String producer) {
            this.id = id;
            this.producer = producer;
        }

        public int getId() {
            return id;
        }

        public String getProducer() {
            return producer;
        }

        public void execute() {
            try {
                Thread.sleep(20);
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
