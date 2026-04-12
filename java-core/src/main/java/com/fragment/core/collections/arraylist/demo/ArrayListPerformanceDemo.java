package com.fragment.core.collections.arraylist.demo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * ArrayList vs LinkedList 性能对比演示
 *
 * 演示内容：
 * 1. 末尾追加：两者相近
 * 2. 头部插入：LinkedList 胜（O(1) vs O(n)）
 * 3. 随机读取：ArrayList 胜（O(1) vs O(n)）
 * 4. 随机删除：ArrayList 批量删除用 removeIf 更快
 */
public class ArrayListPerformanceDemo {

    private static final int COUNT = 100_000;

    public static void main(String[] args) {
        testAppend();
        testHeadInsert();
        testRandomAccess();
        testIteration();
    }

    private static void testAppend() {
        System.out.println("=== 1. 末尾追加 " + COUNT + " 个元素 ===");

        long start = System.currentTimeMillis();
        List<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) arrayList.add(i);
        System.out.println("ArrayList: " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < COUNT; i++) linkedList.add(i);
        System.out.println("LinkedList: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println();
    }

    private static void testHeadInsert() {
        System.out.println("=== 2. 头部插入 10000 个元素 ===");

        List<Integer> arrayList = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) arrayList.add(0, i);  // O(n) 每次移动
        System.out.println("ArrayList: " + (System.currentTimeMillis() - start) + "ms");

        List<Integer> linkedList = new LinkedList<>();
        start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) linkedList.add(0, i);  // O(1)
        System.out.println("LinkedList: " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("结论：频繁头部插入用 LinkedList 或 ArrayDeque");
        System.out.println();
    }

    private static void testRandomAccess() {
        System.out.println("=== 3. 随机读取 " + COUNT + " 次 ===");

        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < COUNT; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < COUNT; i++) arrayList.get(i % COUNT);  // O(1)
        System.out.println("ArrayList: " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) linkedList.get(i % COUNT);  // O(n) 每次从头遍历
        System.out.println("LinkedList(只测1000次): " + (System.currentTimeMillis() - start) + "ms");
        System.out.println("结论：随机访问必用 ArrayList");
        System.out.println();
    }

    private static void testIteration() {
        System.out.println("=== 4. 顺序遍历（增强for）===");

        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < COUNT; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }

        long sum = 0;
        long start = System.currentTimeMillis();
        for (int v : arrayList) sum += v;
        System.out.println("ArrayList: " + (System.currentTimeMillis() - start) + "ms, sum=" + sum);

        sum = 0;
        start = System.currentTimeMillis();
        for (int v : linkedList) sum += v;
        System.out.println("LinkedList: " + (System.currentTimeMillis() - start) + "ms, sum=" + sum);
        System.out.println("结论：顺序遍历两者差异不大，但 ArrayList 缓存局部性更好");
    }
}
