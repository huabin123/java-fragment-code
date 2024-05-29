package com.fragment.redis;

/**
 * @Author huabin
 * @DateTime 2024-05-29 15:28
 * @Desc
 */

import redis.clients.jedis.Jedis;

public class RedisLeakyBucket {
    private Jedis jedis;
    private String bucketKey;
    private int capacity;
    private int refillRate; // 每秒钟漏桶的漏出速率
    private long lastRefillTime;

    public RedisLeakyBucket(String redisHost, int redisPort, String bucketKey, int capacity, int refillRate) {
        this.jedis = new Jedis(redisHost, redisPort);
        this.bucketKey = bucketKey;
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.lastRefillTime = System.currentTimeMillis();
        // 初始化Redis中的漏桶值
        if (jedis.get(bucketKey) == null) {
            jedis.set(bucketKey, "0");
        }
    }

    // 关闭Jedis连接
    public void close() {
        jedis.close();
    }

    // 计算当前桶中的水量
    private int getCurrentWaterLevel() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastRefillTime;
        int leakedWater = (int) (elapsedTime / 1000 * refillRate);
        int currentWaterLevel = Math.max(0, Integer.parseInt(jedis.get(bucketKey)) - leakedWater);
        return currentWaterLevel;
    }

    // 尝试向漏桶中添加消息
    public boolean addMessage(int messageSize) {
        int currentWaterLevel = getCurrentWaterLevel();
        if (currentWaterLevel + messageSize > capacity) {
            // 如果当前水量加上消息大小超过容量，则拒绝消息
            return false;
        } else {
            // 否则，添加消息并更新桶中的水量
            jedis.set(bucketKey, String.valueOf(currentWaterLevel + messageSize));
            lastRefillTime = System.currentTimeMillis();
            return true;
        }
    }

    public static void main(String[] args) {
        RedisLeakyBucket leakyBucket = new RedisLeakyBucket("localhost", 6379, "myBucket", 10, 1);

        // 尝试添加消息
        for (int i = 0; i < 15; i++) {
            if (leakyBucket.addMessage(3)) {
                System.out.println("消息已添加到漏桶中");
            } else {
                System.out.println("漏桶已满，消息被拒绝");
            }
            // 模拟请求间隔
            try {
                Thread.sleep(20); // 每20毫秒发起一次请求
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 关闭Jedis连接
        leakyBucket.close();
    }
}

