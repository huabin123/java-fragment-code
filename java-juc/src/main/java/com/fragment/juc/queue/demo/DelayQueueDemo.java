package com.fragment.juc.queue.demo;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * DelayQueue 演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>Delayed接口实现</li>
 *   <li>延迟获取机制</li>
 *   <li>优先级队列排序</li>
 *   <li>Leader-Follower模式</li>
 *   <li>订单超时取消场景</li>
 *   <li>缓存过期管理场景</li>
 * </ul>
 * 
 * @author fragment
 */
public class DelayQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== DelayQueue 演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 延迟获取演示
        demonstrateDelayedRetrieval();

        // 3. 优先级排序演示
        demonstratePriorityOrdering();

        // 4. Leader-Follower模式演示
        demonstrateLeaderFollower();

        // 5. 订单超时场景
        demonstrateOrderTimeout();

        // 6. 缓存过期场景
        demonstrateCacheExpiration();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() throws InterruptedException {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: 元素必须实现Delayed接口\n");

        DelayQueue<DelayedTask> queue = new DelayQueue<>();

        // 添加延迟任务
        System.out.println("=== 添加任务 ===");
        queue.put(new DelayedTask("Task-1", 3000));  // 3秒后到期
        queue.put(new DelayedTask("Task-2", 1000));  // 1秒后到期
        queue.put(new DelayedTask("Task-3", 2000));  // 2秒后到期

        System.out.println("添加3个任务，队列大小: " + queue.size());

        // 查看队头（不移除）
        System.out.println("\n=== 查看队头 ===");
        DelayedTask head = queue.peek();
        System.out.println("队头任务: " + head.getName());
        System.out.println("剩余延迟: " + head.getDelay(TimeUnit.MILLISECONDS) + "ms");

        // poll：未到期返回null
        System.out.println("\n=== poll操作 ===");
        DelayedTask task = queue.poll();
        System.out.println("poll结果: " + task);  // null（未到期）

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 延迟获取演示
     */
    private static void demonstrateDelayedRetrieval() throws InterruptedException {
        System.out.println("2. 延迟获取演示");
        System.out.println("特点: take()会阻塞到元素到期\n");

        DelayQueue<DelayedTask> queue = new DelayQueue<>();

        // 添加任务
        queue.put(new DelayedTask("Task-A", 1000));
        queue.put(new DelayedTask("Task-B", 2000));
        queue.put(new DelayedTask("Task-C", 3000));

        System.out.println("添加3个任务，延迟分别为1s、2s、3s");
        System.out.println("开始take，观察阻塞行为...\n");

        // 依次取出（会阻塞）
        for (int i = 0; i < 3; i++) {
            long start = System.currentTimeMillis();
            DelayedTask task = queue.take();
            long end = System.currentTimeMillis();
            
            System.out.println("取出: " + task.getName() + 
                             "，等待了: " + (end - start) + "ms" +
                             "，剩余延迟: " + task.getDelay(TimeUnit.MILLISECONDS) + "ms");
        }

        System.out.println("\n关键点: take()会精准等待到元素到期");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 优先级排序演示
     */
    private static void demonstratePriorityOrdering() throws InterruptedException {
        System.out.println("3. 优先级排序演示");
        System.out.println("特点: 按到期时间排序，最早到期的在队头\n");

        DelayQueue<DelayedTask> queue = new DelayQueue<>();

        // 乱序添加
        System.out.println("=== 乱序添加任务 ===");
        queue.put(new DelayedTask("Task-5s", 5000));
        System.out.println("添加Task-5s (5秒)");
        
        queue.put(new DelayedTask("Task-2s", 2000));
        System.out.println("添加Task-2s (2秒)");
        
        queue.put(new DelayedTask("Task-8s", 8000));
        System.out.println("添加Task-8s (8秒)");
        
        queue.put(new DelayedTask("Task-1s", 1000));
        System.out.println("添加Task-1s (1秒)");

        // 查看队头
        System.out.println("\n=== 队头元素 ===");
        DelayedTask head = queue.peek();
        System.out.println("队头: " + head.getName() + 
                         " (最早到期，剩余" + head.getDelay(TimeUnit.MILLISECONDS) + "ms)");

        // 按顺序取出
        System.out.println("\n=== 按到期顺序取出 ===");
        while (!queue.isEmpty()) {
            DelayedTask task = queue.take();
            System.out.println("取出: " + task.getName());
        }

        System.out.println("\n关键点: 内部使用PriorityQueue，自动按到期时间排序");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. Leader-Follower模式演示
     */
    private static void demonstrateLeaderFollower() throws InterruptedException {
        System.out.println("4. Leader-Follower模式演示");
        System.out.println("特点: Leader等待指定时间，Follower无限期等待\n");

        DelayQueue<DelayedTask> queue = new DelayQueue<>();
        queue.put(new DelayedTask("Task", 2000));  // 2秒后到期

        System.out.println("队列中有1个任务，2秒后到期");
        System.out.println("启动3个消费者线程...\n");

        // 创建3个消费者
        for (int i = 1; i <= 3; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    long start = System.currentTimeMillis();
                    System.out.println("[Thread-" + threadId + "] 开始take...");
                    
                    DelayedTask task = queue.take();
                    
                    long end = System.currentTimeMillis();
                    System.out.println("[Thread-" + threadId + "] 取到任务: " + task.getName() + 
                                     "，等待了: " + (end - start) + "ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i).start();
            
            Thread.sleep(100);  // 确保按顺序启动
        }

        Thread.sleep(3000);  // 等待所有线程完成

        System.out.println("\n分析:");
        System.out.println("- Thread-1成为Leader，等待2秒");
        System.out.println("- Thread-2、Thread-3是Follower，无限期等待");
        System.out.println("- 2秒后Thread-1被唤醒，取到任务");
        System.out.println("- Thread-2、Thread-3继续等待（队列已空）");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 订单超时场景
     */
    private static void demonstrateOrderTimeout() throws InterruptedException {
        System.out.println("5. 订单超时场景");
        System.out.println("场景: 订单创建后30秒未支付自动取消\n");

        DelayQueue<Order> orderQueue = new DelayQueue<>();

        // 创建订单
        System.out.println("=== 创建订单 ===");
        Order order1 = new Order("ORDER-001", 3000);  // 3秒超时
        Order order2 = new Order("ORDER-002", 5000);  // 5秒超时
        Order order3 = new Order("ORDER-003", 7000);  // 7秒超时

        orderQueue.put(order1);
        orderQueue.put(order2);
        orderQueue.put(order3);

        System.out.println("创建3个订单，超时时间分别为3s、5s、7s");

        // 启动超时检查线程
        Thread checker = new Thread(() -> {
            try {
                while (true) {
                    Order order = orderQueue.take();
                    
                    // 检查订单状态
                    if (order.getStatus() == OrderStatus.UNPAID) {
                        order.cancel();
                        System.out.println("[超时检查] 订单超时取消: " + order.getOrderId());
                    } else {
                        System.out.println("[超时检查] 订单已支付，忽略: " + order.getOrderId());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        checker.start();

        // 模拟支付
        Thread.sleep(2000);
        System.out.println("\n[用户] 支付订单: ORDER-002");
        order2.pay();

        // 等待所有订单处理完成
        Thread.sleep(6000);
        checker.interrupt();

        System.out.println("\n最终状态:");
        System.out.println("ORDER-001: " + order1.getStatus() + " (超时取消)");
        System.out.println("ORDER-002: " + order2.getStatus() + " (已支付)");
        System.out.println("ORDER-003: " + order3.getStatus() + " (超时取消)");

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 6. 缓存过期场景
     */
    private static void demonstrateCacheExpiration() throws InterruptedException {
        System.out.println("6. 缓存过期场景");
        System.out.println("场景: 缓存项过期自动删除\n");

        SimpleCache cache = new SimpleCache();

        // 添加缓存
        System.out.println("=== 添加缓存 ===");
        cache.put("key1", "value1", 2000);  // 2秒过期
        cache.put("key2", "value2", 4000);  // 4秒过期
        cache.put("key3", "value3", 6000);  // 6秒过期

        System.out.println("添加3个缓存项，过期时间分别为2s、4s、6s");

        // 启动清理线程
        cache.startCleanup();

        // 查询缓存
        System.out.println("\n=== 查询缓存 ===");
        System.out.println("key1: " + cache.get("key1"));
        System.out.println("key2: " + cache.get("key2"));
        System.out.println("key3: " + cache.get("key3"));

        // 等待过期
        System.out.println("\n等待缓存过期...");
        Thread.sleep(3000);

        System.out.println("\n=== 3秒后查询 ===");
        System.out.println("key1: " + cache.get("key1") + " (已过期)");
        System.out.println("key2: " + cache.get("key2"));
        System.out.println("key3: " + cache.get("key3"));

        Thread.sleep(4000);

        System.out.println("\n=== 7秒后查询 ===");
        System.out.println("key1: " + cache.get("key1"));
        System.out.println("key2: " + cache.get("key2") + " (已过期)");
        System.out.println("key3: " + cache.get("key3") + " (已过期)");

        cache.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 延迟任务
     */
    static class DelayedTask implements Delayed {
        private final String name;
        private final long expireTime;

        public DelayedTask(String name, long delay) {
            this.name = name;
            this.expireTime = System.currentTimeMillis() + delay;
        }

        public String getName() {
            return name;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expireTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expireTime, ((DelayedTask) o).expireTime);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 订单
     */
    static class Order implements Delayed {
        private final String orderId;
        private final long expireTime;
        private OrderStatus status = OrderStatus.UNPAID;

        public Order(String orderId, long timeout) {
            this.orderId = orderId;
            this.expireTime = System.currentTimeMillis() + timeout;
        }

        public String getOrderId() {
            return orderId;
        }

        public OrderStatus getStatus() {
            return status;
        }

        public void pay() {
            this.status = OrderStatus.PAID;
        }

        public void cancel() {
            this.status = OrderStatus.CANCELLED;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expireTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expireTime, ((Order) o).expireTime);
        }
    }

    enum OrderStatus {
        UNPAID, PAID, CANCELLED
    }

    /**
     * 简单缓存
     */
    static class SimpleCache {
        private final java.util.Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
        private final DelayQueue<CacheEntry> expireQueue = new DelayQueue<>();
        private Thread cleanupThread;
        private volatile boolean running = true;

        public void put(String key, String value, long ttl) {
            cache.put(key, value);
            expireQueue.put(new CacheEntry(key, ttl));
            System.out.println("缓存添加: " + key + " (TTL: " + ttl + "ms)");
        }

        public String get(String key) {
            return cache.get(key);
        }

        public void startCleanup() {
            cleanupThread = new Thread(() -> {
                while (running) {
                    try {
                        CacheEntry entry = expireQueue.take();
                        cache.remove(entry.getKey());
                        System.out.println("[清理线程] 缓存过期删除: " + entry.getKey());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            cleanupThread.start();
        }

        public void shutdown() {
            running = false;
            if (cleanupThread != null) {
                cleanupThread.interrupt();
            }
        }

        static class CacheEntry implements Delayed {
            private final String key;
            private final long expireTime;

            public CacheEntry(String key, long ttl) {
                this.key = key;
                this.expireTime = System.currentTimeMillis() + ttl;
            }

            public String getKey() {
                return key;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                long diff = expireTime - System.currentTimeMillis();
                return unit.convert(diff, TimeUnit.MILLISECONDS);
            }

            @Override
            public int compareTo(Delayed o) {
                return Long.compare(this.expireTime, ((CacheEntry) o).expireTime);
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
