package com.fragment.juc.queue.demo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentLinkedQueue 演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>CAS无锁机制</li>
 *   <li>非阻塞特性</li>
 *   <li>高并发性能</li>
 *   <li>与LinkedBlockingQueue对比</li>
 *   <li>size()方法的注意事项</li>
 * </ul>
 * 
 * @author fragment
 */
public class ConcurrentLinkedQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== ConcurrentLinkedQueue 演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 非阻塞特性
        demonstrateNonBlocking();

        // 3. 高并发性能测试
        demonstrateHighConcurrency();

        // 4. size()方法注意事项
        demonstrateSizeMethod();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: CAS无锁，非阻塞\n");

        Queue<String> queue = new ConcurrentLinkedQueue<>();

        // 添加元素
        System.out.println("=== 添加元素 ===");
        queue.offer("A");
        queue.offer("B");
        queue.offer("C");
        System.out.println("添加3个元素: A, B, C");
        System.out.println("队列大小: " + queue.size());

        // 查看队头
        System.out.println("\n=== 查看队头 ===");
        String head = queue.peek();
        System.out.println("peek: " + head);
        System.out.println("队列大小: " + queue.size() + " (未改变)");

        // 取出元素
        System.out.println("\n=== 取出元素 ===");
        String item = queue.poll();
        System.out.println("poll: " + item);
        System.out.println("队列大小: " + queue.size());

        // 遍历队列
        System.out.println("\n=== 遍历队列 ===");
        for (String s : queue) {
            System.out.println("元素: " + s);
        }

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 非阻塞特性
     */
    private static void demonstrateNonBlocking() {
        System.out.println("2. 非阻塞特性");
        System.out.println("特点: 所有操作都是非阻塞的\n");

        Queue<String> queue = new ConcurrentLinkedQueue<>();

        // offer永远返回true（无界队列）
        System.out.println("=== offer操作 ===");
        for (int i = 0; i < 1000; i++) {
            boolean success = queue.offer("Item-" + i);
            if (!success) {
                System.out.println("offer失败: " + i);
            }
        }
        System.out.println("成功添加1000个元素");
        System.out.println("队列大小: " + queue.size());

        // poll在队列空时返回null
        System.out.println("\n=== poll操作 ===");
        queue.clear();
        String item = queue.poll();
        System.out.println("空队列poll: " + item);  // null

        System.out.println("\n关键点: offer永远成功，poll返回null，无阻塞");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 高并发性能测试
     */
    private static void demonstrateHighConcurrency() throws InterruptedException {
        System.out.println("3. 高并发性能测试");
        System.out.println("特点: CAS无锁，高并发性能好\n");

        Queue<Integer> queue = new ConcurrentLinkedQueue<>();
        final int OPERATIONS = 100000;
        final int THREAD_COUNT = 10;
        
        AtomicInteger produceCount = new AtomicInteger(0);
        AtomicInteger consumeCount = new AtomicInteger(0);

        long start = System.currentTimeMillis();

        // 创建生产者线程
        Thread[] producers = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            producers[i] = new Thread(() -> {
                for (int j = 0; j < OPERATIONS / THREAD_COUNT; j++) {
                    queue.offer(j);
                    produceCount.incrementAndGet();
                }
            });
            producers[i].start();
        }

        // 创建消费者线程
        Thread[] consumers = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            consumers[i] = new Thread(() -> {
                int count = 0;
                while (count < OPERATIONS / THREAD_COUNT) {
                    Integer item = queue.poll();
                    if (item != null) {
                        consumeCount.incrementAndGet();
                        count++;
                    } else {
                        Thread.yield();  // 队列空，让出CPU
                    }
                }
            });
            consumers[i].start();
        }

        // 等待所有线程完成
        for (Thread t : producers) {
            t.join();
        }
        for (Thread t : consumers) {
            t.join();
        }

        long end = System.currentTimeMillis();

        System.out.println("=== 性能统计 ===");
        System.out.println("总操作数: " + OPERATIONS);
        System.out.println("线程数: " + THREAD_COUNT + " 生产者 + " + THREAD_COUNT + " 消费者");
        System.out.println("生产总数: " + produceCount.get());
        System.out.println("消费总数: " + consumeCount.get());
        System.out.println("总耗时: " + (end - start) + "ms");
        System.out.println("吞吐量: " + (OPERATIONS * 2 * 1000 / (end - start)) + " ops/s");

        System.out.println("\n关键点: CAS无锁，高并发性能优秀");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. size()方法注意事项
     */
    private static void demonstrateSizeMethod() throws InterruptedException {
        System.out.println("4. size()方法注意事项");
        System.out.println("特点: size()是O(n)操作，高并发下不准确\n");

        Queue<Integer> queue = new ConcurrentLinkedQueue<>();

        // 添加元素
        for (int i = 0; i < 1000; i++) {
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

        System.out.println("\n⚠️  注意事项:");
        System.out.println("1. size()是O(n)操作，需要遍历整个队列");
        System.out.println("2. 高并发下，size()返回值可能不准确");
        System.out.println("3. 避免频繁调用size()");
        System.out.println("4. 推荐使用isEmpty()判断队列是否为空");

        System.out.println("\n" + createSeparator(60) + "\n");
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
