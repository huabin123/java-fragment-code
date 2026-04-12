package com.fragment.core.collections.queue.demo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * BlockingQueue 系列基础使用演示
 *
 * 演示内容：
 * 1. ArrayBlockingQueue  - 有界阻塞队列（数组实现）
 * 2. LinkedBlockingQueue - 有界/无界阻塞队列（链表实现）
 * 3. PriorityBlockingQueue - 无界优先级阻塞队列
 * 4. SynchronousQueue    - 零容量同步传递队列
 *
 * BlockingQueue 四组操作方法：
 * ┌────────────┬──────────┬──────────┬──────────────────┬──────────────────────┐
 * │  操作      │ 抛异常   │ 返特殊值 │ 一直阻塞         │ 超时阻塞             │
 * ├────────────┼──────────┼──────────┼──────────────────┼──────────────────────┤
 * │  入队      │ add(e)   │ offer(e) │ put(e)           │ offer(e, time, unit) │
 * │  出队      │ remove() │ poll()   │ take()           │ poll(time, unit)     │
 * │  检查      │ element()│ peek()   │ -                │ -                    │
 * └────────────┴──────────┴──────────┴──────────────────┴──────────────────────┘
 *
 * @author huabin
 */
public class BlockingQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== BlockingQueue 系列演示 ==========\n");

        // 1. ArrayBlockingQueue
        arrayBlockingQueueDemo();

        // 2. LinkedBlockingQueue
        linkedBlockingQueueDemo();

        // 3. PriorityBlockingQueue
        priorityBlockingQueueDemo();

        // 4. SynchronousQueue
        synchronousQueueDemo();
    }

    /**
     * 1. ArrayBlockingQueue 演示
     *
     * 特点：
     * - 基于数组的有界阻塞队列，容量固定，创建后不可更改
     * - 只有一把锁（ReentrantLock），生产和消费共用，吞吐量低于 LinkedBlockingQueue
     * - 可选公平锁模式（按等待时间排序，默认非公平）
     * - 适合：生产速率和消费速率接近、需要严格限流的场景
     */
    private static void arrayBlockingQueueDemo() throws InterruptedException {
        System.out.println("1. ArrayBlockingQueue（有界阻塞队列）");
        System.out.println("----------------------------------------");

        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(3); // 容量为 3

        // offer：非阻塞入队，满则返回 false
        System.out.println("offer 结果: " + queue.offer("A")); // true
        System.out.println("offer 结果: " + queue.offer("B")); // true
        System.out.println("offer 结果: " + queue.offer("C")); // true
        System.out.println("offer 结果（队满）: " + queue.offer("D")); // false，队满不阻塞
        System.out.println("当前队列: " + queue);

        // offer 带超时：队满时最多等待指定时间
        boolean result = queue.offer("D", 100, TimeUnit.MILLISECONDS);
        System.out.println("offer 带超时（队满等100ms）: " + result); // false，超时放弃

        // poll：非阻塞出队
        System.out.println("poll: " + queue.poll());
        System.out.println("poll: " + queue.poll());

        // put/take：阻塞版本，用于生产者-消费者模式
        Thread producer = new Thread(() -> {
            try {
                System.out.println("[生产者] put: X");
                queue.put("X"); // 队列有空间，立即入队
                System.out.println("[生产者] put: Y");
                queue.put("Y");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer");

        Thread consumer = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("[消费者] take: " + queue.take());
                System.out.println("[消费者] take: " + queue.take());
                System.out.println("[消费者] take: " + queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println();
    }

    /**
     * 2. LinkedBlockingQueue 演示
     *
     * 特点：
     * - 基于链表的可选有界阻塞队列（默认 Integer.MAX_VALUE，近似无界）
     * - 读写分离两把锁（takeLock / putLock），并发吞吐量高于 ArrayBlockingQueue
     * - 适合：生产速率远大于消费速率、需要高吞吐的场景
     * - 线程池 Executors.newFixedThreadPool() 内部就是用它
     *
     * 注意：默认无界容量容易造成内存堆积，生产环境建议指定容量上限。
     */
    private static void linkedBlockingQueueDemo() throws InterruptedException {
        System.out.println("2. LinkedBlockingQueue（有界/无界阻塞队列）");
        System.out.println("----------------------------------------");

        // 指定容量（推荐）
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(5);

        // 批量入队
        for (int i = 1; i <= 5; i++) {
            queue.offer(i);
        }
        System.out.println("入队 1~5，当前: " + queue + "，size: " + queue.size());
        System.out.println("remainingCapacity（剩余容量）: " + queue.remainingCapacity());

        // drainTo：批量出队到集合（减少锁竞争，适合批量消费场景）
        java.util.List<Integer> batch = new java.util.ArrayList<>();
        int drained = queue.drainTo(batch, 3); // 最多取 3 个
        System.out.println("drainTo 取出 " + drained + " 个: " + batch);
        System.out.println("队列剩余: " + queue);
        System.out.println();
    }

    /**
     * 3. PriorityBlockingQueue 演示
     *
     * 特点：
     * - 无界优先级阻塞队列（只有 take 会阻塞，offer 永远成功）
     * - 基于最小堆，poll() 始终返回最小元素
     * - 线程安全版本的 PriorityQueue
     * - 适合：多线程场景下需要按优先级处理任务
     */
    private static void priorityBlockingQueueDemo() throws InterruptedException {
        System.out.println("3. PriorityBlockingQueue（无界优先级阻塞队列）");
        System.out.println("----------------------------------------");

        PriorityBlockingQueue<Job> queue = new PriorityBlockingQueue<>();

        queue.put(new Job("发邮件", 3));
        queue.put(new Job("处理告警", 1));
        queue.put(new Job("数据同步", 2));
        queue.put(new Job("紧急修复", 1));

        System.out.println("按优先级出队（数字越小优先级越高）:");
        while (!queue.isEmpty()) {
            Job job = queue.take();
            System.out.println("  [P" + job.priority + "] " + job.name);
        }
        System.out.println();
    }

    /**
     * 4. SynchronousQueue 演示
     *
     * 特点：
     * - 零容量队列，不存储任何元素
     * - put() 阻塞直到有消费者 take()，take() 阻塞直到有生产者 put()
     * - 本质是线程间的直接握手传递（Handoff）
     * - Executors.newCachedThreadPool() 内部就是用它
     * - 适合：线程间一对一的直接数据传递，不需要缓冲
     */
    private static void synchronousQueueDemo() throws InterruptedException {
        System.out.println("4. SynchronousQueue（零容量同步传递队列）");
        System.out.println("----------------------------------------");

        SynchronousQueue<String> queue = new SynchronousQueue<>();

        // 消费者先启动，等待数据
        Thread consumer = new Thread(() -> {
            try {
                System.out.println("[消费者] 等待数据...");
                String data = queue.take(); // 阻塞，直到生产者 put
                System.out.println("[消费者] 收到: " + data);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer");

        // 生产者后启动，传递数据
        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("[生产者] 发送: Hello");
                queue.put("Hello"); // 阻塞，直到消费者 take
                System.out.println("[生产者] 发送完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer");

        consumer.start();
        producer.start();
        consumer.join();
        producer.join();

        // offer/poll 非阻塞版本（没有对应的线程时立即返回）
        System.out.println("\nnon-blocking offer（无消费者）: " + queue.offer("data")); // false
        System.out.println("non-blocking poll（无生产者）: " + queue.poll());           // null
        System.out.println();
    }

    /**
     * Job 类（用于 PriorityBlockingQueue 演示）
     */
    static class Job implements Comparable<Job> {
        String name;
        int priority;

        Job(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public int compareTo(Job other) {
            return Integer.compare(this.priority, other.priority);
        }
    }
}
