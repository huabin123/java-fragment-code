package com.fragment.juc.queue.demo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ArrayBlockingQueue 演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>基本操作：put/take、offer/poll</li>
 *   <li>阻塞特性：队列满/空时的阻塞行为</li>
 *   <li>超时操作：带超时的offer/poll</li>
 *   <li>生产者-消费者模式</li>
 *   <li>公平锁 vs 非公平锁</li>
 *   <li>中断处理</li>
 * </ul>
 * 
 * @author fragment
 */
public class ArrayBlockingQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== ArrayBlockingQueue 演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 阻塞特性演示
        demonstrateBlockingBehavior();

        // 3. 超时操作演示
        demonstrateTimeoutOperations();

        // 4. 生产者-消费者模式
        demonstrateProducerConsumer();

        // 5. 公平锁 vs 非公平锁
        demonstrateFairness();

        // 6. 中断处理
        demonstrateInterruption();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() throws InterruptedException {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: put/take阻塞，offer/poll非阻塞\n");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

        // put操作（阻塞）
        System.out.println("=== put操作 ===");
        queue.put("A");
        queue.put("B");
        queue.put("C");
        System.out.println("put 3个元素后，队列大小: " + queue.size());
        System.out.println("队列内容: " + queue);

        // offer操作（非阻塞）
        System.out.println("\n=== offer操作 ===");
        boolean success = queue.offer("D");
        System.out.println("队列满时offer: " + success);  // false

        // take操作（阻塞）
        System.out.println("\n=== take操作 ===");
        String item = queue.take();
        System.out.println("take: " + item);
        System.out.println("队列大小: " + queue.size());

        // poll操作（非阻塞）
        System.out.println("\n=== poll操作 ===");
        item = queue.poll();
        System.out.println("poll: " + item);
        System.out.println("队列大小: " + queue.size());

        // peek操作（查看不移除）
        System.out.println("\n=== peek操作 ===");
        item = queue.peek();
        System.out.println("peek: " + item);
        System.out.println("队列大小: " + queue.size() + " (未改变)");

        // 清空队列
        queue.clear();
        System.out.println("\n清空后，队列大小: " + queue.size());
        item = queue.poll();
        System.out.println("空队列poll: " + item);  // null

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 阻塞特性演示
     */
    private static void demonstrateBlockingBehavior() throws InterruptedException {
        System.out.println("2. 阻塞特性演示");
        System.out.println("特点: 队列满时put阻塞，队列空时take阻塞\n");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        // 场景1：队列满时put阻塞
        System.out.println("=== 场景1：队列满时put阻塞 ===");
        queue.put("A");
        queue.put("B");
        System.out.println("队列已满，大小: " + queue.size());

        Thread producer = new Thread(() -> {
            try {
                System.out.println("[生产者] 尝试put第3个元素...");
                long start = System.currentTimeMillis();
                queue.put("C");  // 阻塞
                long end = System.currentTimeMillis();
                System.out.println("[生产者] put成功，阻塞了 " + (end - start) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        Thread.sleep(1000);  // 让生产者先阻塞

        System.out.println("[主线程] 1秒后取出一个元素，释放空间");
        queue.take();
        producer.join();

        // 场景2：队列空时take阻塞
        System.out.println("\n=== 场景2：队列空时take阻塞 ===");
        queue.clear();
        System.out.println("队列已清空，大小: " + queue.size());

        Thread consumer = new Thread(() -> {
            try {
                System.out.println("[消费者] 尝试take元素...");
                long start = System.currentTimeMillis();
                String item = queue.take();  // 阻塞
                long end = System.currentTimeMillis();
                System.out.println("[消费者] take到: " + item + "，阻塞了 " + (end - start) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        Thread.sleep(1000);  // 让消费者先阻塞

        System.out.println("[主线程] 1秒后放入一个元素");
        queue.put("X");
        consumer.join();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 超时操作演示
     */
    private static void demonstrateTimeoutOperations() throws InterruptedException {
        System.out.println("3. 超时操作演示");
        System.out.println("特点: 超时后返回false/null，不会永久阻塞\n");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        // offer超时
        System.out.println("=== offer超时 ===");
        queue.put("A");
        queue.put("B");
        System.out.println("队列已满");

        long start = System.currentTimeMillis();
        boolean success = queue.offer("C", 500, TimeUnit.MILLISECONDS);
        long end = System.currentTimeMillis();
        System.out.println("offer超时结果: " + success + "，耗时: " + (end - start) + "ms");

        // poll超时
        System.out.println("\n=== poll超时 ===");
        queue.clear();
        System.out.println("队列已清空");

        start = System.currentTimeMillis();
        String item = queue.poll(500, TimeUnit.MILLISECONDS);
        end = System.currentTimeMillis();
        System.out.println("poll超时结果: " + item + "，耗时: " + (end - start) + "ms");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 生产者-消费者模式
     */
    private static void demonstrateProducerConsumer() throws InterruptedException {
        System.out.println("4. 生产者-消费者模式");
        System.out.println("特点: 自动协调生产和消费速度\n");

        BlockingQueue<Task> taskQueue = new ArrayBlockingQueue<>(5);
        final boolean[] running = {true};

        // 生产者线程
        Thread producer = new Thread(() -> {
            int taskId = 0;
            while (running[0]) {
                try {
                    Task task = new Task(taskId++);
                    taskQueue.put(task);
                    System.out.println("[生产者] 生产任务: " + task + "，队列大小: " + taskQueue.size());
                    Thread.sleep(100);  // 模拟生产耗时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[生产者] 退出");
        }, "Producer");

        // 消费者线程1
        Thread consumer1 = new Thread(() -> {
            while (running[0] || !taskQueue.isEmpty()) {
                try {
                    Task task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        System.out.println("  [消费者1] 消费任务: " + task);
                        Thread.sleep(200);  // 模拟处理耗时
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("  [消费者1] 退出");
        }, "Consumer-1");

        // 消费者线程2
        Thread consumer2 = new Thread(() -> {
            while (running[0] || !taskQueue.isEmpty()) {
                try {
                    Task task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        System.out.println("    [消费者2] 消费任务: " + task);
                        Thread.sleep(200);  // 模拟处理耗时
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("    [消费者2] 退出");
        }, "Consumer-2");

        producer.start();
        consumer1.start();
        consumer2.start();

        // 运行2秒后停止
        Thread.sleep(2000);
        running[0] = false;

        producer.join();
        consumer1.join();
        consumer2.join();

        System.out.println("\n最终队列大小: " + taskQueue.size());
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 公平锁 vs 非公平锁
     */
    private static void demonstrateFairness() throws InterruptedException {
        System.out.println("5. 公平锁 vs 非公平锁");
        System.out.println("特点: 公平锁按FIFO顺序获取锁，非公平锁允许插队\n");

        // 非公平锁（默认）
        System.out.println("=== 非公平锁（默认） ===");
        BlockingQueue<String> unfairQueue = new ArrayBlockingQueue<>(1);
        testFairness(unfairQueue, "非公平");

        Thread.sleep(500);

        // 公平锁
        System.out.println("\n=== 公平锁 ===");
        BlockingQueue<String> fairQueue = new ArrayBlockingQueue<>(1, true);
        testFairness(fairQueue, "公平");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void testFairness(BlockingQueue<String> queue, String type) throws InterruptedException {
        // 创建3个等待线程
        for (int i = 1; i <= 3; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    System.out.println("[" + type + "-线程" + threadId + "] 开始等待take");
                    long start = System.currentTimeMillis();
                    String item = queue.take();
                    long end = System.currentTimeMillis();
                    System.out.println("[" + type + "-线程" + threadId + "] 获取到: " + item + 
                                     "，等待了 " + (end - start) + "ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            Thread.sleep(10);  // 确保按顺序启动
        }

        Thread.sleep(100);  // 确保所有线程都在等待

        // 依次放入3个元素
        for (int i = 1; i <= 3; i++) {
            queue.put("Item-" + i);
            Thread.sleep(50);
        }

        Thread.sleep(200);  // 等待所有线程完成
    }

    /**
     * 6. 中断处理演示
     */
    private static void demonstrateInterruption() throws InterruptedException {
        System.out.println("6. 中断处理演示");
        System.out.println("特点: put/take支持中断，可以优雅退出\n");

        BlockingQueue<String> queue = new ArrayBlockingQueue<>(1);

        // 场景1：中断阻塞的put
        System.out.println("=== 场景1：中断阻塞的put ===");
        queue.put("A");  // 填满队列

        Thread putThread = new Thread(() -> {
            try {
                System.out.println("[put线程] 尝试put（会阻塞）");
                queue.put("B");
                System.out.println("[put线程] put成功");
            } catch (InterruptedException e) {
                System.out.println("[put线程] 被中断，优雅退出");
                Thread.currentThread().interrupt();
            }
        });

        putThread.start();
        Thread.sleep(500);

        System.out.println("[主线程] 中断put线程");
        putThread.interrupt();
        putThread.join();

        // 场景2：中断阻塞的take
        System.out.println("\n=== 场景2：中断阻塞的take ===");
        queue.clear();  // 清空队列

        Thread takeThread = new Thread(() -> {
            try {
                System.out.println("[take线程] 尝试take（会阻塞）");
                String item = queue.take();
                System.out.println("[take线程] take到: " + item);
            } catch (InterruptedException e) {
                System.out.println("[take线程] 被中断，优雅退出");
                Thread.currentThread().interrupt();
            }
        });

        takeThread.start();
        Thread.sleep(500);

        System.out.println("[主线程] 中断take线程");
        takeThread.interrupt();
        takeThread.join();

        System.out.println("\n关键点:");
        System.out.println("1. put/take使用lockInterruptibly，支持中断");
        System.out.println("2. 捕获InterruptedException后应恢复中断状态");
        System.out.println("3. 中断是优雅停止线程的推荐方式");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 任务类
     */
    static class Task {
        private final int id;

        public Task(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Task-" + id;
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
