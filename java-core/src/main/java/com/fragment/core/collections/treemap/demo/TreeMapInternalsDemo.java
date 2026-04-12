package com.fragment.core.collections.treemap.demo;

import java.util.*;

/**
 * TreeMap 内部原理演示
 *
 * 演示内容：
 * 1. 红黑树的平衡性（插入任意顺序，查询性能稳定）
 * 2. 插入顺序 vs 遍历顺序
 * 3. TreeMap 的性能特征：所有操作 O(log n)
 * 4. key 必须可比较（Comparable 或 Comparator）
 */
public class TreeMapInternalsDemo {

    public static void main(String[] args) {
        demonstrateBalancedSearch();
        demonstrateKeyRequirement();
        demonstratePerformanceVsHashMap();
    }

    /**
     * 红黑树保证无论插入顺序，查找性能都是 O(log n)
     */
    private static void demonstrateBalancedSearch() {
        System.out.println("=== 1. 红黑树平衡性 ===");

        TreeMap<Integer, String> map = new TreeMap<>();

        // 即使按有序顺序插入，红黑树也会自动保持平衡（不退化为链表）
        // 这与 BST（普通二叉搜索树）的区别：BST 按顺序插入会退化为链表 O(n)
        for (int i = 1; i <= 15; i++) map.put(i, "v" + i);
        System.out.println("顺序插入15个元素后，树高约为 log2(15)+1 ≈ 5");
        System.out.println("任意 key 的查找始终是 O(log n)");

        // 按逆序插入，结果仍然有序
        TreeMap<Integer, String> map2 = new TreeMap<>();
        for (int i = 15; i >= 1; i--) map2.put(i, "v" + i);
        System.out.println("逆序插入后遍历: " + map2.keySet());
        System.out.println();
    }

    /**
     * Key 必须实现 Comparable 或构造时传入 Comparator，否则运行时异常
     */
    private static void demonstrateKeyRequirement() {
        System.out.println("=== 2. Key 必须可比较 ===");

        // ✅ String 实现了 Comparable
        TreeMap<String, Integer> stringMap = new TreeMap<>();
        stringMap.put("b", 2);
        stringMap.put("a", 1);
        System.out.println("String key（自然排序）: " + stringMap);

        // ✅ 自定义类 + 传入 Comparator
        TreeMap<Point, String> pointMap = new TreeMap<>(
            Comparator.comparingInt((Point p) -> p.x).thenComparingInt(p -> p.y)
        );
        pointMap.put(new Point(3, 1), "P3");
        pointMap.put(new Point(1, 5), "P1");
        pointMap.put(new Point(2, 3), "P2");
        System.out.println("Point key（自定义比较）: " + pointMap);

        // ❌ 不可比较的 key 且无 Comparator → put 时抛 ClassCastException
        // TreeMap<Object, String> bad = new TreeMap<>();
        // bad.put(new Object(), "a");  // ClassCastException: Object cannot be cast to Comparable
        System.out.println();
    }

    /**
     * TreeMap 与 HashMap 性能对比
     */
    private static void demonstratePerformanceVsHashMap() {
        System.out.println("=== 3. TreeMap vs HashMap 性能 ===");
        int count = 500_000;

        // HashMap：O(1) 均摊
        Map<Integer, Integer> hashMap = new HashMap<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) hashMap.put(i, i);
        for (int i = 0; i < count; i++) hashMap.get(i);
        System.out.println("HashMap put+get " + count + " 次: " + (System.currentTimeMillis() - start) + "ms");

        // TreeMap：O(log n)，但提供有序性
        Map<Integer, Integer> treeMap = new TreeMap<>();
        start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) treeMap.put(i, i);
        for (int i = 0; i < count; i++) treeMap.get(i);
        System.out.println("TreeMap put+get " + count + " 次: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("结论：不需要有序时用 HashMap；需要有序/范围查询时用 TreeMap");
    }

    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
        public String toString() { return "(" + x + "," + y + ")"; }
    }
}
