package com.fragment.core.collections.arraylist.demo;

import java.util.*;

/**
 * ArrayList 基础操作演示
 *
 * 演示内容：
 * 1. 创建与初始化（容量指定）
 * 2. 增删改查核心操作
 * 3. 遍历的三种方式及性能差异
 * 4. subList、contains、indexOf
 */
public class ArrayListBasicDemo {

    public static void main(String[] args) {
        demonstrateCreation();
        demonstrateCRUD();
        demonstrateTraversal();
        demonstrateUtilityMethods();
    }

    private static void demonstrateCreation() {
        System.out.println("=== 1. 创建与初始化 ===");

        // 默认容量 10
        List<String> list1 = new ArrayList<>();

        // 指定初始容量（已知大致数量时，避免扩容）
        List<String> list2 = new ArrayList<>(100);

        // 从已有集合构建
        List<String> source = Arrays.asList("A", "B", "C");
        List<String> list3 = new ArrayList<>(source);

        // 工厂方法（JDK 9+，不可变）
        List<String> immutable = List.of("X", "Y", "Z");

        System.out.println("list3: " + list3);
        System.out.println("immutable: " + immutable);
        System.out.println();
    }

    private static void demonstrateCRUD() {
        System.out.println("=== 2. 增删改查 ===");

        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));

        // 增：末尾 O(1) 均摊；指定位置 O(n) 需要移动元素
        list.add("F");           // 末尾添加
        list.add(2, "X");        // 索引 2 插入，后续元素右移

        // 删：按索引 O(n)；按对象 O(n) 先找再删
        list.remove(0);          // 按索引删除
        list.remove("X");        // 按对象删除（删第一个匹配）

        // 改：O(1) 直接数组索引
        list.set(0, "Z");

        // 查：按索引 O(1)；contains/indexOf O(n) 线性扫描
        String val = list.get(1);
        int idx = list.indexOf("C");

        System.out.println("操作后: " + list);
        System.out.println("get(1)=" + val + ", indexOf(C)=" + idx);
        System.out.println();
    }

    private static void demonstrateTraversal() {
        System.out.println("=== 3. 遍历方式 ===");

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) list.add(i);

        // 方式1：for 索引（ArrayList 首选，O(1) 随机访问）
        System.out.print("索引遍历: ");
        for (int i = 0; i < list.size(); i++) {
            System.out.print(list.get(i) + " ");
        }
        System.out.println();

        // 方式2：增强 for（底层 Iterator，简洁）
        System.out.print("增强for: ");
        for (int val : list) {
            System.out.print(val + " ");
        }
        System.out.println();

        // 方式3：Stream（函数式，适合链式操作）
        System.out.print("Stream: ");
        list.stream().forEach(v -> System.out.print(v + " "));
        System.out.println("\n");
    }

    private static void demonstrateUtilityMethods() {
        System.out.println("=== 4. 实用方法 ===");

        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));

        // subList：视图，不是拷贝！对 subList 的修改会反映到原 list
        List<String> sub = list.subList(1, 4);  // [B, C, D]
        System.out.println("subList(1,4): " + sub);
        sub.set(0, "X");
        System.out.println("修改 subList 后原 list: " + list);  // [A, X, C, D, E]

        // 批量操作
        list.removeIf(s -> s.equals("X") || s.equals("C"));
        System.out.println("removeIf 后: " + list);

        // 排序
        List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6));
        Collections.sort(nums);
        System.out.println("排序后: " + nums);

        nums.sort(Comparator.reverseOrder());
        System.out.println("逆序后: " + nums);
    }
}
