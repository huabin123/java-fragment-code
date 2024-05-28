package com.fragment.redis;
import redis.clients.jedis.Jedis;

/**
 * @Author huabin
 * @DateTime 2024-05-28 14:43
 * @Desc Redis实现滑动窗口限流算法
 */
public class RedisSlidingWindowRateLimiter {
    private final Jedis jedis;
    private final String key;
    private final int maxRequests;
    private final long windowSizeInMillis;

    public RedisSlidingWindowRateLimiter(Jedis jedis, String key, int maxRequests, long windowSizeInMillis) {
        this.jedis = jedis;
        this.key = key;
        this.maxRequests = maxRequests;
        this.windowSizeInMillis = windowSizeInMillis;
    }

    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeInMillis;

        // 使用Redis事务进行原子操作
        jedis.zremrangeByScore(key, 0, windowStart); // 移除窗口外的请求
        long currentCount = jedis.zcard(key); // 获取当前窗口内的请求数

        if (currentCount < maxRequests) {
            jedis.zadd(key, now, String.valueOf(now)); // 添加当前请求的时间戳
            jedis.expire(key, (int) (windowSizeInMillis / 1000)); // 设置过期时间
            return true;
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);
        RedisSlidingWindowRateLimiter rateLimiter = new RedisSlidingWindowRateLimiter(jedis, "rate_limiter", 2, 1000); // 每秒最多2个请求

        // 模拟请求
        for (int i = 0; i < 10; i++) {
            if (rateLimiter.allowRequest()) {
                System.out.println("Request " + i + " was allowed.");
            } else {
                System.out.println("Request " + i + " was denied.");
            }

            // 模拟请求间隔
            try {
                Thread.sleep(200); // 每200毫秒发起一次请求
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        jedis.close();
    }
}
