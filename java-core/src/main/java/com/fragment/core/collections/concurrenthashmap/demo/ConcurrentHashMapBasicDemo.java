package com.fragment.core.collections.concurrenthashmap.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap 基础操作演示
 *
 * 演示内容：
 * 1. 基础 CRUD 操作
 * 2. 原子复合操作：putIfAbsent、computeIfAbsent、computeIfPresent、merge
 * 3. 并发计数（replace + CAS）
 * 4. 批量操作：forEach、reduce、search
 */
public class ConcurrentHashMapBasicDemo {

    public static void main(String[] args) {
        demonstrateBasicOps();
        demonstrateAtomicOps();
        demonstrateConcurrentCount();
        demonstrateBulkOps();
    }

    private static void demonstrateBasicOps() {
        System.out.println("=== 1. 基础操作 ===");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        map.put("apple", 3);
        map.put("banana", 5);
        map.put("cherry", 1);

        System.out.println("get: " + map.get("apple"));         // 3
        System.out.println("contains: " + map.containsKey("banana")); // true
        System.out.println("size: " + map.size());              // 3

        // ❌ ConcurrentHashMap 不允许 null key 和 null value（与 HashMap 不同）
        try {
            map.put(null, 1);
        } catch (NullPointerException e) {
            System.out.println("null key → NullPointerException");
        }
        try {
            map.put("key", null);
        } catch (NullPointerException e) {
            System.out.println("null value → NullPointerException");
        }
        System.out.println();
    }

    private static void demonstrateAtomicOps() {
        System.out.println("=== 2. 原子复合操作 ===");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // putIfAbsent：key 不存在时才 put，原子操作
        map.putIfAbsent("apple", 1);   // 插入
        map.putIfAbsent("apple", 99);  // 已存在，不覆盖
        System.out.println("putIfAbsent apple: " + map.get("apple"));  // 1

        // computeIfAbsent：key 不存在时计算并放入（延迟初始化）
        // 典型用法：初始化嵌套集合
        ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> nested = new ConcurrentHashMap<>();
        nested.computeIfAbsent("group1", k -> new ConcurrentHashMap<>()).put("a", 1);
        nested.computeIfAbsent("group1", k -> new ConcurrentHashMap<>()).put("b", 2);  // 直接用已存在的
        System.out.println("computeIfAbsent nested: " + nested.get("group1"));

        // computeIfPresent：key 存在时更新
        map.computeIfPresent("apple", (k, v) -> v + 10);  // apple: 1 → 11
        System.out.println("computeIfPresent apple: " + map.get("apple"));  // 11

        // merge：存在时合并，不存在时插入（词频统计的理想方法）
        String[] words = {"a", "b", "a", "c", "a", "b"};
        ConcurrentHashMap<String, Integer> freq = new ConcurrentHashMap<>();
        for (String w : words) {
            freq.merge(w, 1, Integer::sum);  // 原子合并
        }
        System.out.println("merge 词频: " + freq);  // {a=3, b=2, c=1}
        System.out.println();
    }

    private static void demonstrateConcurrentCount() {
        System.out.println("=== 3. 并发计数（replace CAS）===");

        ConcurrentHashMap<String, Integer> counter = new ConcurrentHashMap<>();
        counter.put("visits", 0);

        // replace(key, expectedValue, newValue)：CAS 语义
        boolean updated = counter.replace("visits", 0, 1);  // 期望是0，替换为1
        System.out.println("replace(0→1): " + updated + ", visits=" + counter.get("visits"));

        boolean failed = counter.replace("visits", 0, 99);  // 期望是0，但现在是1 → 失败
        System.out.println("replace(0→99): " + failed + ", visits=" + counter.get("visits"));

        // 实际并发计数推荐用 merge 或 LongAdder
        ConcurrentHashMap<String, Long> betterCounter = new ConcurrentHashMap<>();
        betterCounter.merge("visits", 1L, Long::sum);  // 原子递增
        betterCounter.merge("visits", 1L, Long::sum);
        System.out.println("merge 计数: " + betterCounter.get("visits"));  // 2
        System.out.println();
    }

    private static void demonstrateBulkOps() {
        System.out.println("=== 4. 批量操作（JDK 8+）===");

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        for (int i = 1; i <= 10; i++) map.put("key" + i, i);

        // forEach(parallelismThreshold, action)：并行遍历
        // parallelismThreshold=1：强制并行；Long.MAX_VALUE：始终串行
        System.out.print("forEach: ");
        map.forEach(Long.MAX_VALUE, (k, v) -> System.out.print(v + " "));
        System.out.println();

        // reduce：并行聚合
        int sum = map.reduceValues(1, Integer::sum);
        System.out.println("reduceValues sum: " + sum);  // 55

        // search：找到第一个满足条件的元素（并行）
        String found = map.search(1, (k, v) -> v > 8 ? k : null);
        System.out.println("search(v>8): " + found);
        System.out.println();
    }
}
