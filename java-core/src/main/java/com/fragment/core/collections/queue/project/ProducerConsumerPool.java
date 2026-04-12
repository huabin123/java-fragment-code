package com.fragment.core.collections.queue.project;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生产者-消费者线程池（基于 ArrayBlockingQueue）
 *
 * 使用场景：
 * - 异步日志写入（业务线程生产日志，后台线程批量写磁盘）
 * - 消息队列本地消费（从 MQ 拉取消息放入本地队列，多线程并行处理）
 * - 数据采集管道（采集线程生产数据，处理线程消费分析）
 * - 接口限流（超出处理能力的请求进队列排队，而非直接丢弃）
 *
 * 设计要点：
 * 1. 使用有界队列（ArrayBlockingQueue），防止内存无限堆积
 * 2. put() 在队满时阻塞生产者，实现反压（Back Pressure）
 * 3. take() 在队空时阻塞消费者，避免空转浪费 CPU
 * 4. 优雅关闭：先停止生产者，再等队列清空，最后停止消费者
 *
 * @author huabin
 */
public class ProducerConsumerPool {

    private final BlockingQueue<Message> queue;
    private final ExecutorService producerPool;
    private final ExecutorService consumerPool;
    private final int consumerCount;

    private final AtomicInteger producedCount = new AtomicInteger(0);
    private final AtomicInteger consumedCount = new AtomicInteger(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);

    private volatile boolean producerStopped = false;

    /**
     * @param queueCapacity  队列容量（超出时生产者阻塞，实现反压）
     * @param producerCount  生产者线程数
     * @param consumerCount  消费者线程数
     */
    public ProducerConsumerPool(int queueCapacity, int producerCount, int consumerCount) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.producerPool = Executors.newFixedThreadPool(producerCount);
        this.consumerPool = Executors.newFixedThreadPool(consumerCount);
        this.consumerCount = consumerCount;
    }

    /**
     * 启动消费者线程
     */
    public void startConsumers() {
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i + 1;
            consumerPool.submit(() -> {
                System.out.printf("[消费者-%d] 启动%n", consumerId);
                while (true) {
                    try {
                        // poll 带超时：避免生产者已停止但消费者永久阻塞
                        Message msg = queue.poll(200, TimeUnit.MILLISECONDS);
                        if (msg == null) {
                            // 超时未取到数据，检查是否应该退出
                            if (producerStopped && queue.isEmpty()) {
                                System.out.printf("[消费者-%d] 队列已空且生产者已停止，退出%n", consumerId);
                                break;
                            }
                            continue;
                        }
                        // 处理消息
                        process(consumerId, msg);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    /**
     * 提交生产任务（异步执行）
     *
     * @param producerId 生产者 ID
     * @param count      生产消息条数
     */
    public void submitProducer(int producerId, int count) {
        producerPool.submit(() -> {
            System.out.printf("[生产者-%d] 启动，计划生产 %d 条消息%n", producerId, count);
            for (int i = 0; i < count; i++) {
                try {
                    Message msg = new Message(
                        producerId + "-" + i,
                        "消息内容_" + i,
                        System.nanoTime()
                    );
                    // put：队满时阻塞，实现反压（不会丢失消息）
                    queue.put(msg);
                    producedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.printf("[生产者-%d] 生产完成%n", producerId);
        });
    }

    /**
     * 优雅关闭
     *
     * 关闭顺序：
     * 1. 停止接收新的生产任务
     * 2. 等待生产者线程全部完成
     * 3. 标记生产者已停止（消费者可感知退出信号）
     * 4. 等待消费者线程处理完队列中的剩余消息
     */
    public void shutdown() throws InterruptedException {
        System.out.println("\n[Pool] 开始优雅关闭...");

        // 1. 停止生产者
        producerPool.shutdown();
        producerPool.awaitTermination(10, TimeUnit.SECONDS);
        producerStopped = true;
        System.out.println("[Pool] 生产者已全部停止，队列剩余: " + queue.size());

        // 2. 等待消费者处理完剩余消息
        consumerPool.shutdown();
        consumerPool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("[Pool] 消费者已全部停止");
    }

    /**
     * 打印统计信息
     */
    public void printStats() {
        System.out.println("\n========== 统计信息 ==========");
        System.out.println("总生产消息数: " + producedCount.get());
        System.out.println("总消费消息数: " + consumedCount.get());
        System.out.println("队列剩余消息: " + queue.size());
        if (consumedCount.get() > 0) {
            long avgLatencyMs = totalLatencyNanos.get() / consumedCount.get() / 1_000_000;
            System.out.println("平均消费延迟: " + avgLatencyMs + "ms");
        }
        System.out.println("==============================\n");
    }

    /**
     * 消息处理逻辑（模拟实际业务处理）
     */
    private void process(int consumerId, Message msg) {
        try {
            // 模拟处理耗时
            Thread.sleep(5);
            long latency = System.nanoTime() - msg.createNanos;
            totalLatencyNanos.addAndGet(latency);
            consumedCount.incrementAndGet();

            if (consumedCount.get() % 50 == 0) {
                System.out.printf("[消费者-%d] 已消费 %d 条，当前队列: %d%n",
                    consumerId, consumedCount.get(), queue.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 消息类
     */
    static class Message {
        final String id;
        final String content;
        final long createNanos; // 创建时间（用于计算延迟）

        Message(String id, String content, long createNanos) {
            this.id = id;
            this.content = content;
            this.createNanos = createNanos;
        }
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 生产者-消费者线程池测试 ==========\n");

        // 队列容量=50，3个生产者，2个消费者
        ProducerConsumerPool pool = new ProducerConsumerPool(50, 3, 2);

        // 启动消费者
        pool.startConsumers();

        // 启动 3 个生产者，各生产 100 条消息
        pool.submitProducer(1, 100);
        pool.submitProducer(2, 100);
        pool.submitProducer(3, 100);

        System.out.println("（生产者和消费者并发运行中...）\n");

        // 优雅关闭并等待完成
        pool.shutdown();
        pool.printStats();

        System.out.println("测试完成");
    }
}
