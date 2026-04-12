package com.fragment.core.collections.treemap.demo;

import java.util.*;

/**
 * TreeMap 基础操作演示
 *
 * 演示内容：
 * 1. 自然排序与自定义排序
 * 2. 有序导航方法：firstKey/lastKey/floorKey/ceilingKey/higherKey/lowerKey
 * 3. 范围视图：subMap/headMap/tailMap
 * 4. 与 HashMap 的对比
 */
public class TreeMapBasicDemo {

    public static void main(String[] args) {
        demonstrateNaturalOrder();
        demonstrateCustomComparator();
        demonstrateNavigationMethods();
        demonstrateRangeViews();
    }

    private static void demonstrateNaturalOrder() {
        System.out.println("=== 1. 自然排序（键实现 Comparable）===");

        // TreeMap 按 key 的自然顺序（Comparable）维护红黑树
        TreeMap<String, Integer> map = new TreeMap<>();
        map.put("banana", 3);
        map.put("apple", 5);
        map.put("cherry", 1);
        map.put("date", 8);
        map.put("apricot", 2);

        // 遍历始终按 key 升序
        System.out.println("升序遍历: " + map);
        System.out.println("firstKey: " + map.firstKey());  // apple
        System.out.println("lastKey:  " + map.lastKey());   // date
        System.out.println();
    }

    private static void demonstrateCustomComparator() {
        System.out.println("=== 2. 自定义 Comparator ===");

        // 按字符串长度排序，长度相同按字母顺序
        TreeMap<String, Integer> map = new TreeMap<>(
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())
        );
        map.put("banana", 3);
        map.put("apple", 5);
        map.put("fig", 1);
        map.put("date", 8);
        map.put("kiwi", 2);

        System.out.println("按长度排序: " + map);

        // 倒序 TreeMap
        TreeMap<Integer, String> descMap = new TreeMap<>(Comparator.reverseOrder());
        descMap.put(3, "three");
        descMap.put(1, "one");
        descMap.put(4, "four");
        descMap.put(2, "two");
        System.out.println("整数倒序: " + descMap);
        System.out.println();
    }

    private static void demonstrateNavigationMethods() {
        System.out.println("=== 3. 导航方法 ===");

        TreeMap<Integer, String> map = new TreeMap<>();
        for (int i = 10; i <= 50; i += 10) map.put(i, "val" + i);
        System.out.println("map: " + map.keySet());

        // floorKey(k)：≤k 的最大 key
        System.out.println("floorKey(25) = " + map.floorKey(25));   // 20
        System.out.println("floorKey(30) = " + map.floorKey(30));   // 30（包含等于）

        // ceilingKey(k)：≥k 的最小 key
        System.out.println("ceilingKey(25) = " + map.ceilingKey(25)); // 30
        System.out.println("ceilingKey(30) = " + map.ceilingKey(30)); // 30（包含等于）

        // lowerKey(k)：< k 的最大 key（严格小于）
        System.out.println("lowerKey(30) = " + map.lowerKey(30));    // 20

        // higherKey(k)：> k 的最小 key（严格大于）
        System.out.println("higherKey(30) = " + map.higherKey(30));  // 40

        // pollFirstEntry / pollLastEntry：取出并删除
        Map.Entry<Integer, String> first = map.pollFirstEntry();
        System.out.println("pollFirstEntry: " + first + ", 剩余: " + map.keySet());
        System.out.println();
    }

    private static void demonstrateRangeViews() {
        System.out.println("=== 4. 范围视图 ===");

        TreeMap<Integer, String> map = new TreeMap<>();
        for (int i = 1; i <= 10; i++) map.put(i, "v" + i);

        // subMap(fromKey, inclusive, toKey, inclusive)
        System.out.println("subMap(3,true,7,true): " + map.subMap(3, true, 7, true).keySet());
        System.out.println("subMap(3,false,7,false): " + map.subMap(3, false, 7, false).keySet());

        // headMap(toKey, inclusive)：小于等于 toKey
        System.out.println("headMap(5,true):  " + map.headMap(5, true).keySet());
        System.out.println("headMap(5,false): " + map.headMap(5, false).keySet());

        // tailMap(fromKey, inclusive)：大于等于 fromKey
        System.out.println("tailMap(8,true):  " + map.tailMap(8, true).keySet());

        // ⚠️ 范围视图是原 map 的视图，修改视图会影响原 map
        map.subMap(1, 3).clear();
        System.out.println("清除 [1,3) 后: " + map.keySet());
        System.out.println();
    }
}
