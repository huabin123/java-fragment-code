package com.fragment.core.collections.linkedlist.demo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * LinkedList性能对比演示
 * 
 * 演示内容：
 * 1. 头部插入性能对比
 * 2. 尾部插入性能对比
 * 3. 中间插入性能对比
 * 4. 随机访问性能对比
 * 5. 遍历性能对比
 * 
 * @author huabin
 */
public class LinkedListPerformanceDemo {

    private static final int SIZE = 10000;

    public static void main(String[] args) {
        System.out.println("========== LinkedList性能对比演示 ==========\n");
        
        // 1. 头部插入性能对比
        testHeadInsert();
        
        // 2. 尾部插入性能对比
        testTailInsert();
        
        // 3. 中间插入性能对比
        testMiddleInsert();
        
        // 4. 随机访问性能对比
        testRandomAccess();
        
        // 5. 遍历性能对比
        testIteration();
    }

    /**
     * 1. 头部插入性能对比
     */
    private static void testHeadInsert() {
        System.out.println("1. 头部插入性能对比（插入" + SIZE + "个元素）");
        System.out.println("----------------------------------------");
        
        // ArrayList头部插入
        List<Integer> arrayList = new ArrayList<>();
        long start1 = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            arrayList.add(0, i);
        }
        long end1 = System.nanoTime();
        
        // LinkedList头部插入
        LinkedList<Integer> linkedList = new LinkedList<>();
        long start2 = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            linkedList.addFirst(i);
        }
        long end2 = System.nanoTime();
        
        double time1 = (end1 - start1) / 1000000.0;
        double time2 = (end2 - start2) / 1000000.0;
        
        System.out.println("ArrayList头部插入: " + String.format("%.2f", time1) + "ms");
        System.out.println("LinkedList头部插入: " + String.format("%.2f", time2) + "ms");
        System.out.println("LinkedList快: " + String.format("%.2f", time1 / time2) + "倍");
        System.out.println("结论: LinkedList头部插入性能远优于ArrayList\n");
    }

    /**
     * 2. 尾部插入性能对比
     */
    private static void testTailInsert() {
        System.out.println("2. 尾部插入性能对比（插入" + SIZE * 10 + "个元素）");
        System.out.println("----------------------------------------");
        
        // ArrayList尾部插入
        List<Integer> arrayList = new ArrayList<>();
        long start1 = System.nanoTime();
        for (int i = 0; i < SIZE * 10; i++) {
            arrayList.add(i);
        }
        long end1 = System.nanoTime();
        
        // LinkedList尾部插入
        LinkedList<Integer> linkedList = new LinkedList<>();
        long start2 = System.nanoTime();
        for (int i = 0; i < SIZE * 10; i++) {
            linkedList.add(i);
        }
        long end2 = System.nanoTime();
        
        double time1 = (end1 - start1) / 1000000.0;
        double time2 = (end2 - start2) / 1000000.0;
        
        System.out.println("ArrayList尾部插入: " + String.format("%.2f", time1) + "ms");
        System.out.println("LinkedList尾部插入: " + String.format("%.2f", time2) + "ms");
        System.out.println("ArrayList快: " + String.format("%.2f", time2 / time1) + "倍");
        System.out.println("结论: ArrayList尾部插入性能略优于LinkedList（内存连续，缓存友好）\n");
    }

    /**
     * 3. 中间插入性能对比
     */
    private static void testMiddleInsert() {
        System.out.println("3. 中间插入性能对比（插入" + SIZE + "个元素）");
        System.out.println("----------------------------------------");
        
        // 预先填充数据
        List<Integer> arrayList = new ArrayList<>();
        LinkedList<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < SIZE; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }
        
        // ArrayList中间插入
        long start1 = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            arrayList.add(SIZE / 2, i);
        }
        long end1 = System.nanoTime();
        
        // LinkedList中间插入
        long start2 = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            linkedList.add(SIZE / 2, i);
        }
        long end2 = System.nanoTime();
        
        double time1 = (end1 - start1) / 1000000.0;
        double time2 = (end2 - start2) / 1000000.0;
        
        System.out.println("ArrayList中间插入: " + String.format("%.2f", time1) + "ms");
        System.out.println("LinkedList中间插入: " + String.format("%.2f", time2) + "ms");
        System.out.println("ArrayList快: " + String.format("%.2f", time2 / time1) + "倍");
        System.out.println("结论: ArrayList中间插入性能优于LinkedList（LinkedList需要先遍历到指定位置）\n");
    }

    /**
     * 4. 随机访问性能对比
     */
    private static void testRandomAccess() {
        System.out.println("4. 随机访问性能对比（访问" + SIZE + "次）");
        System.out.println("----------------------------------------");
        
        // 预先填充数据
        List<Integer> arrayList = new ArrayList<>();
        LinkedList<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < SIZE * 10; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }
        
        // ArrayList随机访问
        long start1 = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            int index = (int) (Math.random() * SIZE * 10);
            arrayList.get(index);
        }
        long end1 = System.nanoTime();
        
        // LinkedList随机访问
        long start2 = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            int index = (int) (Math.random() * SIZE * 10);
            linkedList.get(index);
        }
        long end2 = System.nanoTime();
        
        double time1 = (end1 - start1) / 1000000.0;
        double time2 = (end2 - start2) / 1000000.0;
        
        System.out.println("ArrayList随机访问: " + String.format("%.2f", time1) + "ms");
        System.out.println("LinkedList随机访问: " + String.format("%.2f", time2) + "ms");
        System.out.println("ArrayList快: " + String.format("%.2f", time2 / time1) + "倍");
        System.out.println("结论: ArrayList随机访问性能远优于LinkedList\n");
    }

    /**
     * 5. 遍历性能对比
     */
    private static void testIteration() {
        System.out.println("5. 遍历性能对比（遍历" + SIZE * 10 + "个元素）");
        System.out.println("----------------------------------------");
        
        // 预先填充数据
        List<Integer> arrayList = new ArrayList<>();
        LinkedList<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < SIZE * 10; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }
        
        // ArrayList使用for循环遍历
        long start1 = System.nanoTime();
        for (int i = 0; i < arrayList.size(); i++) {
            int value = arrayList.get(i);
        }
        long end1 = System.nanoTime();
        
        // ArrayList使用foreach遍历
        long start2 = System.nanoTime();
        for (int value : arrayList) {
        }
        long end2 = System.nanoTime();
        
        // LinkedList使用for循环遍历（不推荐）
        long start3 = System.nanoTime();
        for (int i = 0; i < linkedList.size(); i++) {
            int value = linkedList.get(i);
        }
        long end3 = System.nanoTime();
        
        // LinkedList使用foreach遍历（推荐）
        long start4 = System.nanoTime();
        for (int value : linkedList) {
        }
        long end4 = System.nanoTime();
        
        double time1 = (end1 - start1) / 1000000.0;
        double time2 = (end2 - start2) / 1000000.0;
        double time3 = (end3 - start3) / 1000000.0;
        double time4 = (end4 - start4) / 1000000.0;
        
        System.out.println("ArrayList for循环: " + String.format("%.2f", time1) + "ms");
        System.out.println("ArrayList foreach: " + String.format("%.2f", time2) + "ms");
        System.out.println("LinkedList for循环: " + String.format("%.2f", time3) + "ms（超慢！）");
        System.out.println("LinkedList foreach: " + String.format("%.2f", time4) + "ms");
        
        System.out.println("\n结论:");
        System.out.println("1. LinkedList绝对不能用for循环+get遍历（时间复杂度O(n²)）");
        System.out.println("2. LinkedList必须用foreach（迭代器）遍历");
        System.out.println("3. 即使用迭代器，ArrayList仍然比LinkedList快（内存连续，缓存友好）");
        
        System.out.println();
    }
}
