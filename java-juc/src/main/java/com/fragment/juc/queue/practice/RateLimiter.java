package com.fragment.juc.queue.practice;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流器实战 - 基于DelayQueue的令牌桶算法
 * 
 * <p>场景：API限流，控制请求速率
 * <ul>
 *   <li>令牌桶算法实现</li>
 *   <li>支持突发流量</li>
 *   <li>平滑限流</li>
 * </ul>
 * 
 * <p>技术要点：
 * <ul>
 *   <li>DelayQueue实现令牌生成</li>
 *   <li>延迟获取机制</li>
 *   <li>线程安全</li>
 * </ul>
 * 
 * @author fragment
 */
public class RateLimiter {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 限流器实战 ==========\n");

        // 场景1：固定速率限流
        demonstrateFixedRateLimiter();

        Thread.sleep(1000);

        // 场景2：突发流量处理
        demonstrateBurstTraffic();

        Thread.sleep(1000);

        // 场景3：API限流
        demonstrateApiRateLimiting();
    }

    /**
     * 场景1：固定速率限流
     */
    private static void demonstrateFixedRateLimiter() throws InterruptedException {
        System.out.println("=== 场景1：固定速率限流 ===");
        System.out.println("限制：每秒5个请求\n");

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 5);
        limiter.start();

        // 模拟10个请求
        for (int i = 1; i <= 10; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    long start = System.currentTimeMillis();
                    limiter.acquire();
                    long end = System.currentTimeMillis();
                    System.out.println("请求" + requestId + " 通过，等待" + (end - start) + "ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            Thread.sleep(100);
        }

        Thread.sleep(3000);
        limiter.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景2：突发流量处理
     */
    private static void demonstrateBurstTraffic() throws InterruptedException {
        System.out.println("=== 场景2：突发流量处理 ===");
        System.out.println("限制：每秒10个请求，桶容量20\n");

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 20);
        limiter.start();

        // 突发20个请求
        System.out.println("突发20个请求:");
        long start = System.currentTimeMillis();
        for (int i = 1; i <= 20; i++) {
            limiter.acquire();
            System.out.println("请求" + i + " 通过");
        }
        long end = System.currentTimeMillis();
        System.out.println("前20个请求耗时: " + (end - start) + "ms (桶中有令牌，快速通过)");

        // 再发10个请求
        System.out.println("\n继续10个请求:");
        start = System.currentTimeMillis();
        for (int i = 21; i <= 30; i++) {
            limiter.acquire();
            System.out.println("请求" + i + " 通过");
        }
        end = System.currentTimeMillis();
        System.out.println("后10个请求耗时: " + (end - start) + "ms (需要等待令牌生成)");

        limiter.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 场景3：API限流
     */
    private static void demonstrateApiRateLimiting() throws InterruptedException {
        System.out.println("=== 场景3：API限流 ===");
        System.out.println("限制：每秒100个请求\n");

        ApiRateLimiter apiLimiter = new ApiRateLimiter(100);
        apiLimiter.start();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);

        // 模拟200个并发请求
        Thread[] threads = new Thread[200];
        for (int i = 0; i < 200; i++) {
            final int requestId = i;
            threads[i] = new Thread(() -> {
                if (apiLimiter.tryAcquire(100)) {
                    successCount.incrementAndGet();
                    // System.out.println("请求" + requestId + " 通过");
                } else {
                    rejectCount.incrementAndGet();
                    System.out.println("请求" + requestId + " 被拒绝（超过限流）");
                }
            });
            threads[i].start();
        }

        // 等待所有请求完成
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("\n统计:");
        System.out.println("成功请求: " + successCount.get());
        System.out.println("拒绝请求: " + rejectCount.get());

        apiLimiter.shutdown();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 令牌桶限流器
     */
    static class TokenBucketRateLimiter {
        private final int rate;  // 每秒生成令牌数
        private final int capacity;  // 桶容量
        private final DelayQueue<Token> tokenQueue;
        private volatile boolean running = true;
        private Thread tokenGenerator;

        public TokenBucketRateLimiter(int rate, int capacity) {
            this.rate = rate;
            this.capacity = capacity;
            this.tokenQueue = new DelayQueue<>();
        }

        /**
         * 启动令牌生成器
         */
        public void start() {
            // 预填充令牌
            for (int i = 0; i < capacity; i++) {
                tokenQueue.offer(new Token(0));
            }

            // 启动令牌生成线程
            tokenGenerator = new Thread(() -> {
                long interval = 1000 / rate;  // 令牌生成间隔（毫秒）
                while (running) {
                    try {
                        // 如果桶未满，生成令牌
                        if (tokenQueue.size() < capacity) {
                            tokenQueue.offer(new Token(interval));
                        }
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            tokenGenerator.start();
        }

        /**
         * 获取令牌（阻塞）
         */
        public void acquire() throws InterruptedException {
            tokenQueue.take();  // 阻塞直到获取到令牌
        }

        /**
         * 尝试获取令牌（非阻塞）
         */
        public boolean tryAcquire() {
            return tokenQueue.poll() != null;
        }

        /**
         * 停止限流器
         */
        public void shutdown() {
            running = false;
            if (tokenGenerator != null) {
                tokenGenerator.interrupt();
            }
        }
    }

    /**
     * API限流器（简化版）
     */
    static class ApiRateLimiter {
        private final int maxRequestsPerSecond;
        private final DelayQueue<Token> tokenQueue;
        private volatile boolean running = true;
        private Thread tokenGenerator;

        public ApiRateLimiter(int maxRequestsPerSecond) {
            this.maxRequestsPerSecond = maxRequestsPerSecond;
            this.tokenQueue = new DelayQueue<>();
        }

        public void start() {
            // 预填充令牌
            for (int i = 0; i < maxRequestsPerSecond; i++) {
                tokenQueue.offer(new Token(0));
            }

            // 令牌生成器
            tokenGenerator = new Thread(() -> {
                long interval = 1000 / maxRequestsPerSecond;
                while (running) {
                    try {
                        if (tokenQueue.size() < maxRequestsPerSecond) {
                            tokenQueue.offer(new Token(interval));
                        }
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            tokenGenerator.start();
        }

        /**
         * 尝试获取令牌（带超时）
         */
        public boolean tryAcquire(long timeoutMs) {
            try {
                Token token = tokenQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                return token != null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public void shutdown() {
            running = false;
            if (tokenGenerator != null) {
                tokenGenerator.interrupt();
            }
        }
    }

    /**
     * 令牌
     */
    static class Token implements Delayed {
        private final long expireTime;

        public Token(long delay) {
            this.expireTime = System.currentTimeMillis() + delay;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expireTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expireTime, ((Token) o).expireTime);
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
