package com.fragment.juc.queue.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LinkedBlockingQueue 演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>双锁机制：读写并发</li>
 *   <li>有界 vs 无界</li>
 *   <li>高并发性能测试</li>
 *   <li>与ArrayBlockingQueue对比</li>
 *   <li>批量操作：drainTo</li>
 *   <li>内存占用分析</li>
 * </ul>
 * 
 * @author fragment
 */
public class LinkedBlockingQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== LinkedBlockingQueue 演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 双锁并发演示
        demonstrateDualLock();

        // 3. 有界 vs 无界
        demonstrateBoundedVsUnbounded();

        // 4. 高并发性能测试
        demonstrateHighConcurrency();

        // 5. 批量操作
        demonstrateBatchOperations();

        // 6. 与ArrayBlockingQueue对比
        compareWithArrayBlockingQueue();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() throws InterruptedException {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: 链表实现，动态扩展\n");

        // 有界队列
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(5);

        // 添加元素
        System.out.println("=== 添加元素 ===");
        queue.put("A");
        queue.put("B");
        queue.put("C");
        System.out.println("添加3个元素后，队列大小: " + queue.size());
        System.out.println("队列内容: " + queue);

        // 查看队头
        System.out.println("\n=== 查看队头 ===");
        String head = queue.peek();
        System.out.println("peek: " + head);
        System.out.println("队列大小: " + queue.size() + " (未改变)");

        // 取出元素
        System.out.println("\n=== 取出元素 ===");
        String item = queue.take();
        System.out.println("take: " + item);
        System.out.println("队列大小: " + queue.size());

        // 剩余容量
        System.out.println("\n=== 剩余容量 ===");
        int remaining = queue.remainingCapacity();
        System.out.println("剩余容量: " + remaining);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 双锁并发演示
     */
    private static void demonstrateDualLock() throws InterruptedException {
        System.out.println("2. 双锁并发演示");
        System.out.println("特点: putLock和takeLock独立，读写可并发\n");

        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(10);
        AtomicInteger produceCount = new AtomicInteger(0);
        AtomicInteger consumeCount = new AtomicInteger(0);

        // 预填充一些数据
        for (int i = 0; i < 5; i++) {
            queue.put(i);
        }

        // 生产者线程
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    queue.put(i);
                    produceCount.incrementAndGet();
                    System.out.println("[生产者] 生产: " + i + "，队列大小: " + queue.size());
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer");

        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    Integer item = queue.take();
                    consumeCount.incrementAndGet();
                    System.out.println("  [消费者] 消费: " + item + "，队列大小: " + queue.size());
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer");

        long start = System.currentTimeMillis();
        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
        long end = System.currentTimeMillis();

        System.out.println("\n总耗时: " + (end - start) + "ms");
        System.out.println("生产总数: " + produceCount.get());
        System.out.println("消费总数: " + consumeCount.get());
        System.out.println("最终队列大小: " + queue.size());
        System.out.println("\n关键点: 生产和消费并发执行，总耗时约500ms（而不是1000ms）");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 有界 vs 无界
     */
    private static void demonstrateBoundedVsUnbounded() throws InterruptedException {
        System.out.println("3. 有界 vs 无界");
        System.out.println("特点: 默认无界（Integer.MAX_VALUE），可指定容量\n");

        // 无界队列
        System.out.println("=== 无界队列 ===");
        BlockingQueue<String> unbounded = new LinkedBlockingQueue<>();
        System.out.println("容量: " + unbounded.remainingCapacity());  // Integer.MAX_VALUE

        // 添加大量元素（演示用，实际会OOM）
        for (int i = 0; i < 1000; i++) {
            unbounded.offer("Item-" + i);
        }
        System.out.println("添加1000个元素后，队列大小: " + unbounded.size());
        System.out.println("剩余容量: " + unbounded.remainingCapacity());

        // 有界队列
        System.out.println("\n=== 有界队列 ===");
        BlockingQueue<String> bounded = new LinkedBlockingQueue<>(100);
        System.out.println("容量: 100");

        // 填满队列
        for (int i = 0; i < 100; i++) {
            bounded.offer("Item-" + i);
        }
        System.out.println("添加100个元素后，队列大小: " + bounded.size());
        System.out.println("剩余容量: " + bounded.remainingCapacity());

        // 尝试再添加
        boolean success = bounded.offer("Extra");
        System.out.println("队列满时offer: " + success);  // false

        System.out.println("\n⚠️  警告: 无界队列可能导致OOM，生产环境建议指定容量");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 高并发性能测试
     */
    private static void demonstrateHighConcurrency() throws InterruptedException {
        System.out.println("4. 高并发性能测试");
        System.out.println("特点: 多生产者 + 多消费者，测试吞吐量\n");

        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1000);
        AtomicInteger produceCount = new AtomicInteger(0);
        AtomicInteger consumeCount = new AtomicInteger(0);
        final int TOTAL = 100000;
        final int PRODUCER_COUNT = 5;
        final int CONSUMER_COUNT = 5;

        List<Thread> threads = new ArrayList<>();

        long start = System.currentTimeMillis();

        // 创建多个生产者
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            Thread producer = new Thread(() -> {
                int count = 0;
                while (produceCount.get() < TOTAL) {
                    try {
                        int value = produceCount.getAndIncrement();
                        if (value < TOTAL) {
                            queue.put(value);
                            count++;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("[生产者-" + Thread.currentThread().getName() + "] 生产了 " + count + " 个");
            }, "P-" + i);
            threads.add(producer);
            producer.start();
        }

        // 创建多个消费者
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            Thread consumer = new Thread(() -> {
                int count = 0;
                while (consumeCount.get() < TOTAL) {
                    try {
                        Integer item = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (item != null) {
                            consumeCount.incrementAndGet();
                            count++;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("  [消费者-" + Thread.currentThread().getName() + "] 消费了 " + count + " 个");
            }, "C-" + i);
            threads.add(consumer);
            consumer.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        long end = System.currentTimeMillis();
        long duration = end - start;

        System.out.println("\n=== 性能统计 ===");
        System.out.println("总元素数: " + TOTAL);
        System.out.println("生产者数: " + PRODUCER_COUNT);
        System.out.println("消费者数: " + CONSUMER_COUNT);
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("吞吐量: " + (TOTAL * 1000 / duration) + " ops/s");
        System.out.println("最终队列大小: " + queue.size());

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 批量操作演示
     */
    private static void demonstrateBatchOperations() throws InterruptedException {
        System.out.println("5. 批量操作演示");
        System.out.println("特点: drainTo批量取出，提高效率\n");

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        // 添加100个元素
        for (int i = 0; i < 100; i++) {
            queue.put("Item-" + i);
        }
        System.out.println("队列大小: " + queue.size());

        // 批量取出
        System.out.println("\n=== drainTo批量取出 ===");
        List<String> batch = new ArrayList<>();
        int count = queue.drainTo(batch, 20);  // 最多取20个
        System.out.println("取出数量: " + count);
        System.out.println("batch大小: " + batch.size());
        System.out.println("剩余队列大小: " + queue.size());

        // 取出所有
        System.out.println("\n=== drainTo取出所有 ===");
        List<String> all = new ArrayList<>();
        count = queue.drainTo(all);
        System.out.println("取出数量: " + count);
        System.out.println("all大小: " + all.size());
        System.out.println("剩余队列大小: " + queue.size());

        // 性能对比
        System.out.println("\n=== 性能对比 ===");
        queue.clear();
        for (int i = 0; i < 10000; i++) {
            queue.put("Item-" + i);
        }

        // 方式1：逐个poll
        long start = System.currentTimeMillis();
        int pollCount = 0;
        while (queue.poll() != null) {
            pollCount++;
        }
        long end = System.currentTimeMillis();
        System.out.println("逐个poll: " + pollCount + " 个，耗时: " + (end - start) + "ms");

        // 方式2：drainTo
        for (int i = 0; i < 10000; i++) {
            queue.put("Item-" + i);
        }
        List<String> result = new ArrayList<>();
        start = System.currentTimeMillis();
        queue.drainTo(result);
        end = System.currentTimeMillis();
        System.out.println("drainTo: " + result.size() + " 个，耗时: " + (end - start) + "ms");

        System.out.println("\n关键点: drainTo批量操作效率更高（只获取一次锁）");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 6. 与ArrayBlockingQueue对比
     */
    private static void compareWithArrayBlockingQueue() throws InterruptedException {
        System.out.println("6. 与ArrayBlockingQueue对比");
        System.out.println("特点: 吞吐量、内存占用、GC压力对比\n");

        final int CAPACITY = 1000;
        final int OPERATIONS = 50000;

        // LinkedBlockingQueue测试
        System.out.println("=== LinkedBlockingQueue ===");
        BlockingQueue<Integer> linkedQueue = new LinkedBlockingQueue<>(CAPACITY);
        long linkedTime = testQueuePerformance(linkedQueue, OPERATIONS);
        System.out.println("耗时: " + linkedTime + "ms");
        System.out.println("吞吐量: " + (OPERATIONS * 2 * 1000 / linkedTime) + " ops/s");

        Thread.sleep(100);

        // ArrayBlockingQueue测试
        System.out.println("\n=== ArrayBlockingQueue ===");
        BlockingQueue<Integer> arrayQueue = new java.util.concurrent.ArrayBlockingQueue<>(CAPACITY);
        long arrayTime = testQueuePerformance(arrayQueue, OPERATIONS);
        System.out.println("耗时: " + arrayTime + "ms");
        System.out.println("吞吐量: " + (OPERATIONS * 2 * 1000 / arrayTime) + " ops/s");

        // 对比
        System.out.println("\n=== 性能对比 ===");
        System.out.println("LinkedBlockingQueue耗时: " + linkedTime + "ms");
        System.out.println("ArrayBlockingQueue耗时: " + arrayTime + "ms");
        double speedup = (double) arrayTime / linkedTime;
        System.out.println("LinkedBlockingQueue是ArrayBlockingQueue的 " + 
                         String.format("%.2f", speedup) + " 倍");

        System.out.println("\n=== 内存占用对比 ===");
        System.out.println("LinkedBlockingQueue:");
        System.out.println("  - 每个元素额外的Node对象（约24字节）");
        System.out.println("  - 1000个元素约占用: 24KB（仅Node开销）");
        System.out.println("\nArrayBlockingQueue:");
        System.out.println("  - 固定数组，无额外对象");
        System.out.println("  - 1000个元素约占用: 4KB（仅引用数组）");

        System.out.println("\n=== 选型建议 ===");
        System.out.println("LinkedBlockingQueue适合:");
        System.out.println("  ✅ 高并发场景（多生产者+多消费者）");
        System.out.println("  ✅ 追求吞吐量");
        System.out.println("  ✅ 需要动态容量");
        System.out.println("\nArrayBlockingQueue适合:");
        System.out.println("  ✅ 固定容量场景");
        System.out.println("  ✅ 内存敏感");
        System.out.println("  ✅ GC敏感");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 测试队列性能
     */
    private static long testQueuePerformance(BlockingQueue<Integer> queue, int operations) 
            throws InterruptedException {
        AtomicInteger produceCount = new AtomicInteger(0);
        AtomicInteger consumeCount = new AtomicInteger(0);

        long start = System.currentTimeMillis();

        // 生产者
        Thread producer = new Thread(() -> {
            for (int i = 0; i < operations; i++) {
                try {
                    queue.put(i);
                    produceCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // 消费者
        Thread consumer = new Thread(() -> {
            for (int i = 0; i < operations; i++) {
                try {
                    queue.take();
                    consumeCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        long end = System.currentTimeMillis();
        return end - start;
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
