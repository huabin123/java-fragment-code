package com.fragment.core.collections.set.demo;

import java.util.*;

/**
 * Set 内部原理演示
 *
 * 演示内容：
 * 1. HashSet 底层就是 HashMap（value 是 PRESENT 占位对象）
 * 2. hashCode/equals 对 HashSet 的决定性作用
 * 3. TreeSet 底层是 TreeMap，key 必须可比较
 * 4. LinkedHashSet 底层是 LinkedHashMap
 */
public class SetInternalsDemo {

    public static void main(String[] args) {
        demonstrateHashCodeEquals();
        demonstrateTreeSetComparator();
        demonstrateMutableKeyPitfall();
    }

    /**
     * hashCode 和 equals 决定了对象在 HashSet 中是否"相同"
     */
    private static void demonstrateHashCodeEquals() {
        System.out.println("=== 1. hashCode/equals 对 HashSet 的影响 ===");

        // 情况1：只重写 equals，不重写 hashCode
        Set<BadPoint> badSet = new HashSet<>();
        badSet.add(new BadPoint(1, 1));
        badSet.add(new BadPoint(1, 1));  // 期望去重，但 hashCode 不同，认为是两个对象
        System.out.println("只重写 equals，size=" + badSet.size());  // 2（没去重！）

        // 情况2：正确重写 hashCode 和 equals
        Set<GoodPoint> goodSet = new HashSet<>();
        goodSet.add(new GoodPoint(1, 1));
        goodSet.add(new GoodPoint(1, 1));  // hashCode 相同，equals 也相同 → 去重
        System.out.println("正确重写，size=" + goodSet.size());  // 1

        // 情况3：hashCode 相同，equals 不同（哈希冲突）
        Set<CollisionPoint> collisionSet = new HashSet<>();
        collisionSet.add(new CollisionPoint(1, 1));
        collisionSet.add(new CollisionPoint(2, 2));  // hashCode 相同，但 equals 不同
        System.out.println("哈希冲突但不同对象，size=" + collisionSet.size());  // 2
        System.out.println();
    }

    /**
     * TreeSet 需要 Comparable 或 Comparator，否则运行时异常
     */
    private static void demonstrateTreeSetComparator() {
        System.out.println("=== 2. TreeSet 的排序 ===");

        // 自定义排序：按字符串长度
        TreeSet<String> byLength = new TreeSet<>(
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())
        );
        byLength.addAll(Arrays.asList("banana", "fig", "cherry", "kiwi", "apple"));
        System.out.println("按长度排序: " + byLength);

        // TreeSet 的导航方法（与 TreeMap 类似）
        TreeSet<Integer> nums = new TreeSet<>(Arrays.asList(1, 3, 5, 7, 9));
        System.out.println("floor(4)=" + nums.floor(4));     // 3
        System.out.println("ceiling(4)=" + nums.ceiling(4)); // 5
        System.out.println("headSet(5)=" + nums.headSet(5)); // [1, 3]（不含5）
        System.out.println("tailSet(5)=" + nums.tailSet(5)); // [5, 7, 9]（含5）
        System.out.println("subSet(3,7)=" + nums.subSet(3, 7)); // [3, 5]（不含7）
        System.out.println();
    }

    /**
     * 陷阱：修改已放入 HashSet 的可变对象的 hashCode 相关字段，导致无法找到该元素
     */
    private static void demonstrateMutableKeyPitfall() {
        System.out.println("=== 3. 可变对象作为 Set 元素的陷阱 ===");

        Set<GoodPoint> set = new HashSet<>();
        GoodPoint p = new GoodPoint(1, 1);
        set.add(p);
        System.out.println("add 后 contains: " + set.contains(p));  // true

        // 修改 p 的字段（会改变 hashCode）
        p.x = 99;
        System.out.println("修改后 contains: " + set.contains(p));   // false！
        System.out.println("set.size(): " + set.size());             // 还是 1，元素还在但找不到

        // 结论：不要修改已放入 Set/Map 的对象中参与 hashCode 计算的字段
        System.out.println("提示：Set 的 key 应该使用不可变对象（String、Integer、自定义不可变类）");
    }

    // 只重写 equals，不重写 hashCode（错误示范）
    static class BadPoint {
        int x, y;
        BadPoint(int x, int y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BadPoint)) return false;
            BadPoint p = (BadPoint) o;
            return x == p.x && y == p.y;
        }
        // ❌ 没有重写 hashCode，使用 Object 默认的（基于内存地址），两个 new 的对象不同
    }

    // 正确重写 hashCode 和 equals
    static class GoodPoint {
        int x, y;
        GoodPoint(int x, int y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof GoodPoint)) return false;
            GoodPoint p = (GoodPoint) o;
            return x == p.x && y == p.y;
        }
        @Override
        public int hashCode() { return Objects.hash(x, y); }
    }

    // hashCode 固定返回同一个值（哈希冲突演示）
    static class CollisionPoint {
        int x, y;
        CollisionPoint(int x, int y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CollisionPoint)) return false;
            CollisionPoint p = (CollisionPoint) o;
            return x == p.x && y == p.y;
        }
        @Override
        public int hashCode() { return 42; }  // 所有实例 hashCode 相同
    }
}
