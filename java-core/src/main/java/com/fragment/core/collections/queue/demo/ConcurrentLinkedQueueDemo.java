package com.fragment.core.collections.queue.demo;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentLinkedQueue 基础使用演示
 *
 * 演示内容：
 * 1. 基本操作
 * 2. 多线程并发安全验证
 * 3. 与 LinkedBlockingQueue 对比
 *
 * 核心特点：
 * - 基于 CAS（Compare-And-Swap）实现的无锁并发队列
 * - 非阻塞：offer/poll 永远不会阻塞线程
 * - size() 方法时间复杂度为 O(n)，高并发下不推荐频繁调用
 * - 不允许存放 null 元素
 * - 适合：读多写少、高并发、不需要阻塞等待的场景
 *   （需要阻塞等待时选 LinkedBlockingQueue）
 *
 * @author huabin
 */
public class ConcurrentLinkedQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== ConcurrentLinkedQueue 演示 ==========\n");

        // 1. 基本操作
        basicOperations();

        // 2. 多线程并发安全验证
        concurrentSafetyDemo();

        // 3. ConcurrentLinkedQueue vs LinkedBlockingQueue 选型对比
        selectionGuide();
    }

    /**
     * 1. 基本操作
     */
    private static void basicOperations() {
        System.out.println("1. 基本操作");
        System.out.println("----------------------------------------");

        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        // offer：入队（永远返回 true，因为无界）
        queue.offer("A");
        queue.offer("B");
        queue.offer("C");
        System.out.println("入队 A, B, C");

        // peek：查看队头，不移除（队列为空返回 null）
        System.out.println("peek: " + queue.peek());

        // poll：出队（队列为空返回 null，不阻塞）
        System.out.println("poll: " + queue.poll());
        System.out.println("poll: " + queue.poll());
        System.out.println("剩余: " + queue);

        // contains：判断是否包含
        System.out.println("contains(C): " + queue.contains("C"));
        System.out.println("contains(X): " + queue.contains("X"));

        // remove：删除指定元素
        queue.offer("D");
        queue.offer("E");
        queue.remove("D");
        System.out.println("remove(D) 后: " + queue);

        // isEmpty：判断是否为空（O(1)，推荐用来代替 size() == 0）
        System.out.println("isEmpty: " + queue.isEmpty());

        // ⚠️ size() 是 O(n) 操作，高并发下慎用
        System.out.println("size（O(n)，慎用）: " + queue.size());

        System.out.println();
    }

    /**
     * 2. 多线程并发安全验证
     *
     * 10 个生产者线程各写入 100 个元素，10 个消费者线程各读取 100 个元素，
     * 验证最终队列中元素数量正确，无数据丢失或重复。
     */
    private static void concurrentSafetyDemo() throws InterruptedException {
        System.out.println("2. 多线程并发安全验证");
        System.out.println("----------------------------------------");

        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        int threadCount = 10;
        int perThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount * 2);
        AtomicInteger produced = new AtomicInteger(0);
        AtomicInteger consumed = new AtomicInteger(0);

        // 10 个生产者线程
        for (int t = 0; t < threadCount; t++) {
            final int base = t * perThread;
            new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    queue.offer(base + i);
                    produced.incrementAndGet();
                }
                latch.countDown();
            }, "Producer-" + t).start();
        }

        // 10 个消费者线程
        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                int count = 0;
                long deadline = System.currentTimeMillis() + 3000; // 最多等 3 秒
                while (count < perThread && System.currentTimeMillis() < deadline) {
                    Integer val = queue.poll();
                    if (val != null) {
                        consumed.incrementAndGet();
                        count++;
                    }
                }
                latch.countDown();
            }, "Consumer-" + t).start();
        }

        latch.await();

        System.out.println("总生产数: " + produced.get());
        System.out.println("总消费数: " + consumed.get());
        System.out.println("队列剩余: " + queue.size());
        System.out.println("并发安全验证: " + (produced.get() == threadCount * perThread ? "✓ 生产无丢失" : "✗ 生产有问题"));
        System.out.println();
    }

    /**
     * 3. 选型对比指南
     */
    private static void selectionGuide() {
        System.out.println("3. ConcurrentLinkedQueue vs LinkedBlockingQueue 选型");
        System.out.println("----------------------------------------");
        System.out.println();
        System.out.println("  特性                    ConcurrentLinkedQueue   LinkedBlockingQueue");
        System.out.println("  ────────────────────────────────────────────────────────────────────");
        System.out.println("  实现机制                无锁 CAS               ReentrantLock 锁");
        System.out.println("  是否阻塞                否（非阻塞）            是（put/take 会阻塞）");
        System.out.println("  容量限制                无界                   可选有界（推荐设置）");
        System.out.println("  size() 复杂度           O(n)                   O(1)");
        System.out.println("  null 元素               不允许                  不允许");
        System.out.println("  适用场景                高并发、非阻塞消费      生产者-消费者（需等待）");
        System.out.println("  典型使用                事件日志收集、结果汇总  线程池任务队列");
        System.out.println();
        System.out.println("  选型建议：");
        System.out.println("  - 消费者需要等待数据（阻塞）         → LinkedBlockingQueue");
        System.out.println("  - 消费者不需要等待，有就取没就跳过   → ConcurrentLinkedQueue");
        System.out.println("  - 需要限制队列大小防止堆积           → LinkedBlockingQueue（指定容量）");
        System.out.println();
    }
}
