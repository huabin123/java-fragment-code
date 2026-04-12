package com.fragment.core.collections.queue.project;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 延迟任务调度器（基于 DelayQueue）
 *
 * 使用场景：
 * - 订单超时未支付自动取消（下单后 30 分钟未付款关闭订单）
 * - 用户登录 Token 过期自动清除
 * - 消息重试（失败后延迟 N 秒重新投递）
 * - 会话超时踢出
 *
 * DelayQueue 特点：
 * - 无界阻塞队列，元素必须实现 Delayed 接口
 * - take() 阻塞直到有元素到期，到期前不会返回
 * - 内部使用 PriorityQueue 按到期时间排序，最近到期的在队头
 * - 不允许存放 null 元素
 * - 线程安全
 *
 * @author huabin
 */
public class DelayTaskScheduler {

    private final DelayQueue<DelayedTask> queue = new DelayQueue<>();
    private final AtomicInteger taskIdGenerator = new AtomicInteger(0);
    private volatile boolean running = false;
    private Thread workerThread;

    /**
     * 提交延迟任务
     *
     * @param name        任务名称
     * @param delayMs     延迟毫秒数
     * @param action      任务逻辑
     * @return 任务 ID
     */
    public int schedule(String name, long delayMs, Runnable action) {
        int id = taskIdGenerator.incrementAndGet();
        DelayedTask task = new DelayedTask(id, name, delayMs, action);
        queue.offer(task);
        System.out.printf("[Scheduler] 提交任务 #%d [%s]，将在 %dms 后执行%n", id, name, delayMs);
        return id;
    }

    /**
     * 启动调度器（后台线程持续消费到期任务）
     */
    public void start() {
        running = true;
        workerThread = new Thread(() -> {
            System.out.println("[Scheduler] 调度器启动");
            while (running || !queue.isEmpty()) {
                try {
                    // take() 阻塞直到有任务到期
                    DelayedTask task = queue.take();
                    System.out.printf("[Scheduler] 执行任务 #%d [%s]%n", task.id, task.name);
                    task.action.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.out.println("[Scheduler] 任务执行异常: " + e.getMessage());
                }
            }
            System.out.println("[Scheduler] 调度器停止");
        }, "DelayScheduler-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * 停止调度器
     */
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    /**
     * 获取当前队列中待执行的任务数
     */
    public int pendingCount() {
        return queue.size();
    }

    /**
     * 延迟任务实现类
     *
     * 必须实现 Delayed 接口，DelayQueue 依赖其判断元素是否到期。
     */
    static class DelayedTask implements Delayed {

        final int id;
        final String name;
        final Runnable action;
        /** 任务到期的绝对时间（纳秒） */
        private final long expireNanos;

        DelayedTask(int id, String name, long delayMs, Runnable action) {
            this.id = id;
            this.name = name;
            this.action = action;
            this.expireNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        }

        /**
         * 返回剩余延迟时间，负数表示已到期。
         * DelayQueue 的 take() 依赖此方法判断是否可出队。
         */
        @Override
        public long getDelay(TimeUnit unit) {
            long remainNanos = expireNanos - System.nanoTime();
            return unit.convert(remainNanos, TimeUnit.NANOSECONDS);
        }

        /**
         * 排序依据：剩余延迟时间越短，优先级越高（最先到期的在队头）
         */
        @Override
        public int compareTo(Delayed other) {
            long diff = this.getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
            return Long.compare(diff, 0);
        }
    }

    /**
     * 测试示例：模拟订单超时取消场景
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 延迟任务调度器测试 ==========\n");

        DelayTaskScheduler scheduler = new DelayTaskScheduler();
        scheduler.start();

        // 模拟提交多个延迟任务（乱序提交，验证按到期时间执行）
        scheduler.schedule("订单#1001 超时取消", 1000, () ->
            System.out.println("  → 订单#1001 已超时，执行取消逻辑"));

        scheduler.schedule("Token#2001 过期清除", 500, () ->
            System.out.println("  → Token#2001 已过期，从缓存移除"));

        scheduler.schedule("消息#3001 重试投递", 1500, () ->
            System.out.println("  → 消息#3001 重试投递"));

        scheduler.schedule("订单#1002 超时取消", 800, () ->
            System.out.println("  → 订单#1002 已超时，执行取消逻辑"));

        System.out.println("\n当前待执行任务数: " + scheduler.pendingCount());
        System.out.println("（等待任务逐步到期执行...）\n");

        // 等待所有任务执行完毕
        Thread.sleep(2000);

        System.out.println("\n测试完成，剩余待执行任务: " + scheduler.pendingCount());

        // 演示：动态追加任务
        System.out.println("\n--- 动态追加新任务 ---");
        scheduler.schedule("会话#4001 超时踢出", 300, () ->
            System.out.println("  → 会话#4001 超时，强制下线"));

        Thread.sleep(500);
        scheduler.stop();
    }
}
