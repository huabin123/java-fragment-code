package com.fragment.core.collections.set.demo;

import java.util.*;

/**
 * Set 基础操作演示
 *
 * 演示内容：
 * 1. HashSet / LinkedHashSet / TreeSet 的基础 CRUD
 * 2. 三者的顺序差异
 * 3. Set 的集合运算：并集、交集、差集
 * 4. contains 的 O(1) vs List.contains 的 O(n)
 */
public class SetBasicDemo {

    public static void main(String[] args) {
        demonstrateThreeTypes();
        demonstrateSetOperations();
        demonstrateContainsPerformance();
    }

    private static void demonstrateThreeTypes() {
        System.out.println("=== 1. 三种 Set 的顺序差异 ===");

        String[] data = {"banana", "apple", "cherry", "apple", "date", "banana"};

        // HashSet：无序，自动去重
        Set<String> hashSet = new HashSet<>(Arrays.asList(data));
        System.out.println("HashSet（无序去重）: " + hashSet);

        // LinkedHashSet：保持插入顺序，自动去重
        Set<String> linkedHashSet = new LinkedHashSet<>(Arrays.asList(data));
        System.out.println("LinkedHashSet（插入顺序）: " + linkedHashSet);

        // TreeSet：按自然顺序排序，自动去重
        Set<String> treeSet = new TreeSet<>(Arrays.asList(data));
        System.out.println("TreeSet（字母排序）: " + treeSet);

        System.out.println();
    }

    private static void demonstrateSetOperations() {
        System.out.println("=== 2. 集合运算 ===");

        Set<Integer> setA = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
        Set<Integer> setB = new HashSet<>(Arrays.asList(3, 4, 5, 6, 7));

        // 并集（A ∪ B）：addAll
        Set<Integer> union = new HashSet<>(setA);
        union.addAll(setB);
        System.out.println("A ∪ B: " + new TreeSet<>(union));  // 排序输出更直观

        // 交集（A ∩ B）：retainAll
        Set<Integer> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        System.out.println("A ∩ B: " + new TreeSet<>(intersection));

        // 差集（A - B）：removeAll
        Set<Integer> difference = new HashSet<>(setA);
        difference.removeAll(setB);
        System.out.println("A - B: " + new TreeSet<>(difference));

        // 对称差（只在一个集合中出现的元素）
        Set<Integer> symDiff = new HashSet<>(union);
        symDiff.removeAll(intersection);
        System.out.println("A △ B: " + new TreeSet<>(symDiff));

        System.out.println();
    }

    private static void demonstrateContainsPerformance() {
        System.out.println("=== 3. contains 性能：Set O(1) vs List O(n) ===");

        int count = 1_000_000;
        List<Integer> list = new ArrayList<>();
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < count; i++) {
            list.add(i);
            set.add(i);
        }

        int queries = 100_000;
        Random random = new Random();

        long start = System.currentTimeMillis();
        for (int i = 0; i < queries; i++) list.contains(random.nextInt(count));
        System.out.println("List.contains " + queries + " 次: " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        for (int i = 0; i < queries; i++) set.contains(random.nextInt(count));
        System.out.println("Set.contains  " + queries + " 次: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("结论：需要频繁判断元素是否存在时，Set 比 List 快数百倍");
    }
}
