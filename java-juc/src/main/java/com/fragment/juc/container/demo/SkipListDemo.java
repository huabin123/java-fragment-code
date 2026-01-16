package com.fragment.juc.container.demo;

import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 跳表容器演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>跳表数据结构原理</li>
 *   <li>ConcurrentSkipListMap使用</li>
 *   <li>NavigableMap导航方法</li>
 *   <li>范围查询</li>
 *   <li>并发性能</li>
 * </ul>
 * 
 * @author fragment
 */
public class SkipListDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 跳表容器演示 ==========\n");

        // 1. 基本操作演示
        demonstrateBasicOperations();

        // 2. 导航方法演示
        demonstrateNavigationMethods();

        // 3. 范围查询演示
        demonstrateRangeQueries();

        // 4. 并发性能演示
        demonstrateConcurrentPerformance();

        // 5. ConcurrentSkipListSet演示
        demonstrateConcurrentSkipListSet();

        // 6. 实际应用场景
        demonstratePracticalScenario();
    }

    /**
     * 1. 基本操作演示
     */
    private static void demonstrateBasicOperations() {
        System.out.println("1. 基本操作演示");
        System.out.println("特点: 线程安全的有序Map，基于跳表实现\n");

        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();

        // 添加元素（乱序）
        System.out.println("=== 添加元素 ===");
        map.put(5, "E");
        map.put(1, "A");
        map.put(3, "C");
        map.put(2, "B");
        map.put(4, "D");
        System.out.println("添加顺序: 5, 1, 3, 2, 4");
        System.out.println("实际存储（自动排序）: " + map);

        // 获取元素
        System.out.println("\n=== 获取元素 ===");
        System.out.println("get(3): " + map.get(3));
        System.out.println("get(6): " + map.get(6));

        // 删除元素
        System.out.println("\n=== 删除元素 ===");
        String removed = map.remove(3);
        System.out.println("remove(3): " + removed);
        System.out.println("删除后: " + map);

        System.out.println("\n关键点: 自动按key排序，O(log n)复杂度");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 导航方法演示
     */
    private static void demonstrateNavigationMethods() {
        System.out.println("2. 导航方法演示");
        System.out.println("特点: NavigableMap提供丰富的导航方法\n");

        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
        map.put(10, "Ten");
        map.put(20, "Twenty");
        map.put(30, "Thirty");
        map.put(40, "Forty");
        map.put(50, "Fifty");

        System.out.println("原始数据: " + map);

        // 首尾元素
        System.out.println("\n=== 首尾元素 ===");
        System.out.println("firstKey: " + map.firstKey());
        System.out.println("lastKey: " + map.lastKey());
        System.out.println("firstEntry: " + map.firstEntry());
        System.out.println("lastEntry: " + map.lastEntry());

        // 向下查找（小于等于）
        System.out.println("\n=== 向下查找（≤） ===");
        System.out.println("floorKey(25): " + map.floorKey(25));    // 20
        System.out.println("floorKey(30): " + map.floorKey(30));    // 30
        System.out.println("floorEntry(25): " + map.floorEntry(25));

        // 向上查找（大于等于）
        System.out.println("\n=== 向上查找（≥） ===");
        System.out.println("ceilingKey(25): " + map.ceilingKey(25));  // 30
        System.out.println("ceilingKey(30): " + map.ceilingKey(30));  // 30
        System.out.println("ceilingEntry(25): " + map.ceilingEntry(25));

        // 严格小于
        System.out.println("\n=== 严格小于（<） ===");
        System.out.println("lowerKey(30): " + map.lowerKey(30));    // 20
        System.out.println("lowerEntry(30): " + map.lowerEntry(30));

        // 严格大于
        System.out.println("\n=== 严格大于（>） ===");
        System.out.println("higherKey(30): " + map.higherKey(30));  // 40
        System.out.println("higherEntry(30): " + map.higherEntry(30));

        System.out.println("\n关键点: 提供灵活的导航查找方法");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 范围查询演示
     */
    private static void demonstrateRangeQueries() {
        System.out.println("3. 范围查询演示");
        System.out.println("特点: 支持高效的范围查询\n");

        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
        for (int i = 10; i <= 100; i += 10) {
            map.put(i, "Value-" + i);
        }

        System.out.println("原始数据: " + map);

        // subMap - 子Map（左闭右开）
        System.out.println("\n=== subMap [30, 70) ===");
        NavigableMap<Integer, String> subMap = map.subMap(30, 70);
        System.out.println("subMap: " + subMap);

        // headMap - 头部Map（小于）
        System.out.println("\n=== headMap (< 40) ===");
        NavigableMap<Integer, String> headMap = map.headMap(40);
        System.out.println("headMap: " + headMap);

        // tailMap - 尾部Map（大于等于）
        System.out.println("\n=== tailMap (≥ 60) ===");
        NavigableMap<Integer, String> tailMap = map.tailMap(60);
        System.out.println("tailMap: " + tailMap);

        // 降序视图
        System.out.println("\n=== 降序视图 ===");
        NavigableMap<Integer, String> descendingMap = map.descendingMap();
        System.out.println("descendingMap: " + descendingMap);

        System.out.println("\n关键点: 范围查询不复制数据，是原Map的视图");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 并发性能演示
     */
    private static void demonstrateConcurrentPerformance() throws InterruptedException {
        System.out.println("4. 并发性能演示");
        System.out.println("场景: 10个线程并发读写\n");

        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
        final int THREAD_COUNT = 10;
        final int OPERATIONS = 10000;

        AtomicInteger putCount = new AtomicInteger(0);
        AtomicInteger getCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        long start = System.currentTimeMillis();

        // 创建并发线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < OPERATIONS; j++) {
                    int key = threadId * OPERATIONS + j;
                    
                    // 50%写，50%读
                    if (j % 2 == 0) {
                        map.put(key, "Value-" + key);
                        putCount.incrementAndGet();
                    } else {
                        map.get(key - 1);
                        getCount.incrementAndGet();
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        long end = System.currentTimeMillis();

        System.out.println("=== 性能统计 ===");
        System.out.println("put操作: " + putCount.get());
        System.out.println("get操作: " + getCount.get());
        System.out.println("总操作数: " + (putCount.get() + getCount.get()));
        System.out.println("Map大小: " + map.size());
        System.out.println("总耗时: " + (end - start) + "ms");
        System.out.println("吞吐量: " + ((putCount.get() + getCount.get()) * 1000 / (end - start)) + " ops/s");

        System.out.println("\n关键点: 跳表结构，并发性能优秀");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. ConcurrentSkipListSet演示
     */
    private static void demonstrateConcurrentSkipListSet() {
        System.out.println("5. ConcurrentSkipListSet演示");
        System.out.println("特点: 基于ConcurrentSkipListMap实现的有序Set\n");

        ConcurrentSkipListSet<Integer> set = new ConcurrentSkipListSet<>();

        // 添加元素（乱序）
        System.out.println("=== 添加元素 ===");
        set.add(5);
        set.add(1);
        set.add(3);
        set.add(2);
        set.add(4);
        set.add(3);  // 重复
        System.out.println("添加: 5, 1, 3, 2, 4, 3");
        System.out.println("实际存储: " + set);

        // 导航方法
        System.out.println("\n=== 导航方法 ===");
        System.out.println("first: " + set.first());
        System.out.println("last: " + set.last());
        System.out.println("floor(3): " + set.floor(3));    // 小于等于3的最大元素
        System.out.println("ceiling(3): " + set.ceiling(3));  // 大于等于3的最小元素

        // 范围查询
        System.out.println("\n=== 范围查询 ===");
        NavigableSet<Integer> subSet = set.subSet(2, true, 4, true);
        System.out.println("subSet [2, 4]: " + subSet);

        System.out.println("\n关键点: 自动排序，自动去重");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 6. 实际应用场景演示
     */
    private static void demonstratePracticalScenario() {
        System.out.println("6. 实际应用场景演示");
        System.out.println("场景: 排行榜系统\n");

        // 使用ConcurrentSkipListMap实现排行榜
        // Key: 分数（降序），Value: 用户名
        ConcurrentSkipListMap<Integer, String> leaderboard = 
            new ConcurrentSkipListMap<>((a, b) -> b - a);  // 降序

        // 添加玩家分数
        System.out.println("=== 添加玩家分数 ===");
        leaderboard.put(1000, "Alice");
        leaderboard.put(1500, "Bob");
        leaderboard.put(800, "Charlie");
        leaderboard.put(2000, "David");
        leaderboard.put(1200, "Eve");

        System.out.println("排行榜（按分数降序）:");
        int rank = 1;
        for (Map.Entry<Integer, String> entry : leaderboard.entrySet()) {
            System.out.println("第" + rank + "名: " + entry.getValue() + 
                             " - " + entry.getKey() + "分");
            rank++;
        }

        // Top 3
        System.out.println("\n=== Top 3 ===");
        rank = 1;
        for (Map.Entry<Integer, String> entry : leaderboard.entrySet()) {
            if (rank > 3) break;
            System.out.println("第" + rank + "名: " + entry.getValue() + 
                             " - " + entry.getKey() + "分");
            rank++;
        }

        // 查询分数范围
        System.out.println("\n=== 1000-1500分的玩家 ===");
        NavigableMap<Integer, String> rangeMap = leaderboard.subMap(1500, true, 1000, true);
        for (Map.Entry<Integer, String> entry : rangeMap.entrySet()) {
            System.out.println(entry.getValue() + ": " + entry.getKey() + "分");
        }

        // 更新分数
        System.out.println("\n=== 更新分数 ===");
        leaderboard.remove(800);  // 删除旧分数
        leaderboard.put(1800, "Charlie");  // 添加新分数
        System.out.println("Charlie分数更新为1800");

        System.out.println("\n更新后的排行榜:");
        rank = 1;
        for (Map.Entry<Integer, String> entry : leaderboard.entrySet()) {
            System.out.println("第" + rank + "名: " + entry.getValue() + 
                             " - " + entry.getKey() + "分");
            rank++;
        }

        System.out.println("\n关键点: 适合需要排序和范围查询的场景");
        System.out.println("\n" + createSeparator(60) + "\n");
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
