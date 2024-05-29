package com.fragment.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * @Author huabin
 * @DateTime 2024-05-29 14:03
 * @Desc Redis实现令牌桶限流算法
 */

public class RedisTokenBucket {
    private final int capacity; // 桶的容量
    private final int refillRate; // 令牌生成速率（每秒生成的令牌数）
    private final String key; // Redis键
    private final Jedis jedis; // Redis客户端

    public RedisTokenBucket(int capacity, int refillRate, String key, Jedis jedis) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.key = key;
        this.jedis = jedis;
        // 初始化令牌桶
        if (jedis.setnx(key + ":tokens", String.valueOf(capacity)) == 1) {
            jedis.set(key + ":timestamp", String.valueOf(System.nanoTime()));
        }
    }

    // 填充令牌
    private void refill() {
        long now = System.nanoTime();
        String lastRefillTimestampStr = jedis.get(key + ":timestamp");
        if (lastRefillTimestampStr == null) {
            jedis.set(key + ":timestamp", String.valueOf(now));
            return;
        }
        long lastRefillTimestamp = Long.parseLong(lastRefillTimestampStr);
        long duration = now - lastRefillTimestamp;
        int newTokens = (int) (duration / 1_000_000_000L * refillRate);
        if (newTokens > 0) {
            jedis.watch(key + ":tokens");
            int currentTokens = Integer.parseInt(jedis.get(key + ":tokens"));
            int updatedTokens = Math.min(currentTokens + newTokens, capacity);
            Transaction transaction = jedis.multi();
            transaction.set(key + ":tokens", String.valueOf(updatedTokens));
            transaction.set(key + ":timestamp", String.valueOf(now));
            transaction.exec();
        }
    }

    // 获取令牌
    public boolean tryConsume(int numTokens) {
        refill();
        jedis.watch(key + ":tokens");
        int currentTokens = Integer.parseInt(jedis.get(key + ":tokens"));
        if (currentTokens >= numTokens) {
            Transaction transaction = jedis.multi();
            transaction.set(key + ":tokens", String.valueOf(currentTokens - numTokens));
            transaction.exec();
            return true;
        } else {
            jedis.unwatch();
            return false;
        }
    }

    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);
        RedisTokenBucket bucket = new RedisTokenBucket(10, 1, "myBucket", jedis); // 容量为10，每秒生成1个令牌

        // 模拟请求
        for (int i = 0; i < 15; i++) {
            if (bucket.tryConsume(1)) {
                System.out.println("Request " + i + " was allowed.");
            } else {
                System.out.println("Request " + i + " was denied.");
            }

            // 模拟请求间隔
            try {
                Thread.sleep(20); // 每20毫秒发起一次请求
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        jedis.close();
    }
}

