package com.fragment.core.collections.concurrenthashmap.demo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * ConcurrentHashMap 常用并发模式演示
 *
 * 演示内容：
 * 1. 并发计数器（merge vs LongAdder）
 * 2. 并发分组统计（computeIfAbsent + LongAdder）
 * 3. 并发缓存（computeIfAbsent 实现懒加载）
 * 4. 并发去重（putIfAbsent）
 */
public class ConcurrentHashMapPatternDemo {

    public static void main(String[] args) throws Exception {
        demonstrateCounting();
        demonstrateGroupStats();
        demonstrateLazyCache();
        demonstrateDeduplication();
    }

    /**
     * 并发计数：merge 适合低并发；LongAdder 适合高并发（减少 CAS 竞争）
     */
    private static void demonstrateCounting() {
        System.out.println("=== 1. 并发计数模式 ===");

        // 方案1：merge（低并发，代码简洁）
        ConcurrentHashMap<String, Integer> counter1 = new ConcurrentHashMap<>();
        String[] events = {"click", "click", "view", "click", "view", "purchase"};
        for (String e : events) {
            counter1.merge(e, 1, Integer::sum);
        }
        System.out.println("merge 计数: " + counter1);

        // 方案2：LongAdder（高并发首选，分散竞争）
        ConcurrentHashMap<String, LongAdder> counter2 = new ConcurrentHashMap<>();
        for (String e : events) {
            counter2.computeIfAbsent(e, k -> new LongAdder()).increment();
        }
        counter2.forEach((k, v) -> System.out.println("LongAdder " + k + ": " + v.sum()));
        System.out.println();
    }

    /**
     * 并发分组统计：每个 key 对应一个 LongAdder，线程安全累加
     */
    private static void demonstrateGroupStats() {
        System.out.println("=== 2. 并发分组统计 ===");

        ConcurrentHashMap<String, LongAdder> stats = new ConcurrentHashMap<>();

        // 模拟多线程统计不同城市的访问量
        String[] cities = {"北京", "上海", "北京", "广州", "上海", "北京"};
        for (String city : cities) {
            // computeIfAbsent 保证每个 city 只创建一次 LongAdder（原子操作）
            stats.computeIfAbsent(city, k -> new LongAdder()).increment();
        }

        System.out.println("城市访问统计:");
        stats.forEach((city, count) ->
            System.out.printf("  %s: %d次%n", city, count.sum()));
        System.out.println();
    }

    /**
     * 并发缓存：computeIfAbsent 保证 key 对应的初始化逻辑只执行一次
     */
    private static void demonstrateLazyCache() throws Exception {
        System.out.println("=== 3. 并发懒加载缓存 ===");

        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

        // computeIfAbsent 是原子的：即使多线程同时 miss，初始化函数只执行一次
        // （JDK 8 的 ConcurrentHashMap.computeIfAbsent 对同一 key 有锁保护）
        for (int i = 0; i < 3; i++) {
            String result = cache.computeIfAbsent("user:123", key -> {
                System.out.println("  [DB] 查询 " + key);  // 只打印一次
                return "User{id=123, name=张三}";
            });
            System.out.println("  缓存结果: " + result);
        }
        System.out.println();
    }

    /**
     * 并发去重：putIfAbsent 保证第一个写入的赢
     */
    private static void demonstrateDeduplication() {
        System.out.println("=== 4. 并发去重（putIfAbsent）===");

        ConcurrentHashMap<String, String> registry = new ConcurrentHashMap<>();

        // 模拟多线程抢注用户名
        String[] attempts = {"alice", "bob", "alice", "carol", "bob"};
        for (String name : attempts) {
            String prev = registry.putIfAbsent(name, "registered");
            if (prev == null) {
                System.out.println(name + " → 注册成功");
            } else {
                System.out.println(name + " → 用户名已被占用");
            }
        }

        System.out.println("已注册用户: " + registry.keySet());
    }
}
