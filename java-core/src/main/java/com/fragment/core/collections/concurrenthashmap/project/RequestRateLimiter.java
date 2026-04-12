package com.fragment.core.collections.concurrenthashmap.project;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 请求限流器（基于滑动窗口计数）
 *
 * 使用 ConcurrentHashMap + LongAdder 实现高并发限流，
 * 无需加锁，适合高 QPS 场景。
 */
public class RequestRateLimiter {

    // key = userId + 时间窗口, value = 该窗口内的请求次数
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    // key = userId + 时间窗口, value = 窗口过期时间
    private final ConcurrentHashMap<String, Long> expireAt = new ConcurrentHashMap<>();

    private final int maxRequests;   // 时间窗口内最大请求数
    private final long windowMillis; // 时间窗口大小（毫秒）

    public RequestRateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public static void main(String[] args) throws Exception {
        // 每秒最多 5 次请求
        RequestRateLimiter limiter = new RequestRateLimiter(5, 1000);

        System.out.println("=== 限流演示（每秒 5 次）===");
        for (int i = 1; i <= 8; i++) {
            boolean allowed = limiter.tryAcquire("user001");
            System.out.printf("请求 %d: %s%n", i, allowed ? "✅ 允许" : "❌ 限流");
        }

        System.out.println("\n等待 1 秒后重试...");
        Thread.sleep(1000);

        for (int i = 1; i <= 3; i++) {
            boolean allowed = limiter.tryAcquire("user001");
            System.out.printf("请求 %d: %s%n", i, allowed ? "✅ 允许" : "❌ 限流");
        }
    }

    /**
     * 尝试获取请求许可
     * @return true=允许，false=限流
     */
    public boolean tryAcquire(String userId) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now / windowMillis * windowMillis;
        String key = userId + ":" + windowStart;

        // 清理过期 key（简化版，生产环境用 ScheduledExecutor 定期清理）
        expireAt.entrySet().removeIf(e -> e.getValue() < now);
        counters.keySet().removeIf(k -> !expireAt.containsKey(k));

        // computeIfAbsent 保证同一个 key 只创建一次 LongAdder（原子）
        LongAdder counter = counters.computeIfAbsent(key, k -> {
            expireAt.put(k, windowStart + windowMillis);
            return new LongAdder();
        });

        long current = counter.longValue();
        if (current >= maxRequests) return false;

        counter.increment();
        // 二次检查（increment 后可能超过限制）
        return counter.longValue() <= maxRequests;
    }

    public long getCurrentCount(String userId) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now / windowMillis * windowMillis;
        String key = userId + ":" + windowStart;
        LongAdder counter = counters.get(key);
        return counter != null ? counter.longValue() : 0;
    }
}
