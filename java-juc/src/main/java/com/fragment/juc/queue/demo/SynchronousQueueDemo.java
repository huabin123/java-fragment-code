package com.fragment.juc.queue.demo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * SynchronousQueue 演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>零容量特性</li>
 *   <li>直接传递机制</li>
 *   <li>必须配对操作</li>
 *   <li>公平 vs 非公平模式</li>
 *   <li>在线程池中的应用</li>
 * </ul>
 * 
 * @author fragment
 */
public class SynchronousQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== SynchronousQueue 演示 ==========\n");

        // 1. 基本特性演示
        demonstrateBasicFeatures();

        // 2. 直接传递演示
        demonstrateDirectHandoff();

        // 3. 必须配对演示
        demonstratePairing();

        // 4. 公平 vs 非公平
        demonstrateFairness();

        // 5. 超时操作
        demonstrateTimeout();
    }

    /**
     * 1. 基本特性演示
     */
    private static void demonstrateBasicFeatures() {
        System.out.println("1. 基本特性演示");
        System.out.println("特点: 容量为0，无法存储元素\n");

        BlockingQueue<String> queue = new SynchronousQueue<>();

        System.out.println("=== 队列特性 ===");
        System.out.println("size: " + queue.size());                    // 0
        System.out.println("isEmpty: " + queue.isEmpty());              // true
        System.out.println("remainingCapacity: " + queue.remainingCapacity()); // 0

        // 尝试非阻塞操作
        System.out.println("\n=== 非阻塞操作 ===");
        boolean offered = queue.offer("item");
        System.out.println("offer结果: " + offered);  // false（没有消费者等待）

        String polled = queue.poll();
        System.out.println("poll结果: " + polled);    // null（队列空）

        System.out.println("\n关键点: 容量为0，无法存储元素，offer/poll立即返回");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 直接传递演示
     */
    private static void demonstrateDirectHandoff() throws InterruptedException {
        System.out.println("2. 直接传递演示");
        System.out.println("特点: 生产者直接传递给消费者，无中间存储\n");

        BlockingQueue<String> queue = new SynchronousQueue<>();

        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                System.out.println("[消费者] 准备接收...");
                long start = System.currentTimeMillis();
                String item = queue.take();
                long end = System.currentTimeMillis();
                System.out.println("[消费者] 接收到: " + item + "，等待了" + (end - start) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        Thread.sleep(100);  // 确保消费者先启动

        // 生产者线程
        System.out.println("[生产者] 开始传递...");
        long start = System.currentTimeMillis();
        queue.put("Hello");
        long end = System.currentTimeMillis();
        System.out.println("[生产者] 传递完成，耗时" + (end - start) + "ms");

        consumer.join();

        System.out.println("\n关键点: 生产者阻塞直到消费者接收，实现直接传递");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 必须配对演示
     */
    private static void demonstratePairing() throws InterruptedException {
        System.out.println("3. 必须配对演示");
        System.out.println("特点: put必须等待take，take必须等待put\n");

        BlockingQueue<Integer> queue = new SynchronousQueue<>();

        // 场景1：生产者先到
        System.out.println("=== 场景1：生产者先到 ===");
        Thread producer1 = new Thread(() -> {
            try {
                System.out.println("[生产者1] 尝试put(1)...");
                queue.put(1);
                System.out.println("[生产者1] put成功");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer1.start();
        Thread.sleep(500);
        System.out.println("[主线程] 生产者1正在等待...");

        Thread consumer1 = new Thread(() -> {
            try {
                System.out.println("[消费者1] 开始take...");
                Integer item = queue.take();
                System.out.println("[消费者1] 取到: " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer1.start();
        producer1.join();
        consumer1.join();

        Thread.sleep(500);

        // 场景2：消费者先到
        System.out.println("\n=== 场景2：消费者先到 ===");
        Thread consumer2 = new Thread(() -> {
            try {
                System.out.println("[消费者2] 尝试take()...");
                Integer item = queue.take();
                System.out.println("[消费者2] 取到: " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer2.start();
        Thread.sleep(500);
        System.out.println("[主线程] 消费者2正在等待...");

        Thread producer2 = new Thread(() -> {
            try {
                System.out.println("[生产者2] 开始put(2)...");
                queue.put(2);
                System.out.println("[生产者2] put成功");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer2.start();
        consumer2.join();
        producer2.join();

        System.out.println("\n关键点: 必须配对，一方等待另一方");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 公平 vs 非公平
     */
    private static void demonstrateFairness() throws InterruptedException {
        System.out.println("4. 公平 vs 非公平");
        System.out.println("特点: 公平模式FIFO，非公平模式LIFO\n");

        // 非公平模式（默认）
        System.out.println("=== 非公平模式（默认） ===");
        BlockingQueue<String> unfairQueue = new SynchronousQueue<>();
        testFairness(unfairQueue, "非公平");

        Thread.sleep(500);

        // 公平模式
        System.out.println("\n=== 公平模式 ===");
        BlockingQueue<String> fairQueue = new SynchronousQueue<>(true);
        testFairness(fairQueue, "公平");

        System.out.println("\n关键点: 公平模式按FIFO顺序，非公平模式性能更高");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void testFairness(BlockingQueue<String> queue, String mode) throws InterruptedException {
        // 创建3个消费者
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    System.out.println("[" + mode + "-消费者" + id + "] 开始等待");
                    long start = System.currentTimeMillis();
                    String item = queue.take();
                    long end = System.currentTimeMillis();
                    System.out.println("[" + mode + "-消费者" + id + "] 接收: " + item + 
                                     "，等待" + (end - start) + "ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            Thread.sleep(50);  // 确保按顺序启动
        }

        Thread.sleep(200);  // 确保所有消费者都在等待

        // 依次put 3个元素
        for (int i = 1; i <= 3; i++) {
            queue.put("Item-" + i);
            Thread.sleep(50);
        }

        Thread.sleep(200);  // 等待完成
    }

    /**
     * 5. 超时操作
     */
    private static void demonstrateTimeout() throws InterruptedException {
        System.out.println("5. 超时操作");
        System.out.println("特点: 支持超时的offer/poll\n");

        BlockingQueue<String> queue = new SynchronousQueue<>();

        // offer超时
        System.out.println("=== offer超时 ===");
        long start = System.currentTimeMillis();
        boolean success = queue.offer("item", 500, TimeUnit.MILLISECONDS);
        long end = System.currentTimeMillis();
        System.out.println("offer结果: " + success + "，耗时: " + (end - start) + "ms");

        // poll超时
        System.out.println("\n=== poll超时 ===");
        start = System.currentTimeMillis();
        String item = queue.poll(500, TimeUnit.MILLISECONDS);
        end = System.currentTimeMillis();
        System.out.println("poll结果: " + item + "，耗时: " + (end - start) + "ms");

        System.out.println("\n关键点: 超时后返回false/null，避免永久阻塞");
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
