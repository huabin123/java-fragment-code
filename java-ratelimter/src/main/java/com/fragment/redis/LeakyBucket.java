package com.fragment.redis;

/**
 * @Author huabin
 * @DateTime 2024-05-29 16:44
 * @Desc
 */

import com.google.common.util.concurrent.RateLimiter;

public class LeakyBucket {
    private final RateLimiter rateLimiter;
    private final int capacity;
    private int currentWaterLevel;

    public LeakyBucket(double refillRate, int capacity) {
        this.rateLimiter = RateLimiter.create(refillRate);
        this.capacity = capacity;
        this.currentWaterLevel = 0;
    }

    // 尝试向漏桶中添加消息
    public synchronized boolean addMessage(int messageSize) {
        if (currentWaterLevel + messageSize > capacity) {
            // 如果当前水量加上消息大小超过容量，则拒绝消息
            return false;
        } else {
            // 否则，添加消息并更新桶中的水量
            currentWaterLevel += messageSize;
            return true;
        }
    }

    // 消耗令牌，模拟漏桶的漏出
    public void leak() {
        if (rateLimiter.tryAcquire()) {
            synchronized (this) {
                if (currentWaterLevel > 0) {
                    currentWaterLevel--;
                }
            }
        }
    }

    public int getCurrentWaterLevel() {
        return currentWaterLevel;
    }

    public static void main(String[] args) {
        LeakyBucket leakyBucket = new LeakyBucket(1.0, 10); // 每秒漏出1个单位，容量为10

        // 模拟添加消息
        for (int i = 0; i < 15; i++) {
            if (leakyBucket.addMessage(1)) {
                System.out.println("消息已添加到漏桶中");
            } else {
                System.out.println("漏桶已满，消息被拒绝");
            }
        }

        // 模拟漏桶的漏出
        for (int i = 0; i < 15; i++) {
            leakyBucket.leak();
            System.out.println("当前水量: " + leakyBucket.getCurrentWaterLevel());
            try {
                Thread.sleep(1000); // 每秒漏出一次
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

