package com.fragment.core.collections.linkedhashmap.demo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LinkedHashMap基础使用演示
 * 
 * 演示内容：
 * 1. 插入顺序模式
 * 2. 访问顺序模式
 * 3. 与HashMap的对比
 * 4. 遍历顺序
 * 
 * @author huabin
 */
public class LinkedHashMapBasicDemo {

    public static void main(String[] args) {
        System.out.println("========== LinkedHashMap基础使用演示 ==========\n");
        
        // 1. 插入顺序模式
        insertionOrderMode();
        
        // 2. 访问顺序模式
        accessOrderMode();
        
        // 3. 与HashMap的对比
        compareWithHashMap();
    }

    /**
     * 1. 插入顺序模式（默认）
     */
    private static void insertionOrderMode() {
        System.out.println("1. 插入顺序模式（accessOrder=false，默认）");
        System.out.println("----------------------------------------");
        
        Map<String, Integer> map = new LinkedHashMap<>();
        
        // 添加元素
        map.put("张三", 20);
        map.put("李四", 25);
        map.put("王五", 30);
        map.put("赵六", 35);
        
        System.out.println("添加元素后:");
        printMap(map);
        
        // 访问元素（不改变顺序）
        map.get("张三");
        map.get("王五");
        
        System.out.println("\n访问元素后（顺序不变）:");
        printMap(map);
        
        // 更新元素（不改变顺序）
        map.put("李四", 26);
        
        System.out.println("\n更新元素后（顺序不变）:");
        printMap(map);
        
        System.out.println();
    }

    /**
     * 2. 访问顺序模式
     */
    private static void accessOrderMode() {
        System.out.println("2. 访问顺序模式（accessOrder=true）");
        System.out.println("----------------------------------------");
        
        // accessOrder=true：按访问顺序
        Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
        
        // 添加元素
        map.put("张三", 20);
        map.put("李四", 25);
        map.put("王五", 30);
        map.put("赵六", 35);
        
        System.out.println("添加元素后:");
        printMap(map);
        
        // 访问元素（改变顺序）
        map.get("张三");
        
        System.out.println("\n访问张三后（张三移到最后）:");
        printMap(map);
        
        map.get("王五");
        
        System.out.println("\n访问王五后（王五移到最后）:");
        printMap(map);
        
        // 更新元素（改变顺序）
        map.put("李四", 26);
        
        System.out.println("\n更新李四后（李四移到最后）:");
        printMap(map);
        
        System.out.println();
    }

    /**
     * 3. 与HashMap的对比
     */
    private static void compareWithHashMap() {
        System.out.println("3. 与HashMap的对比");
        System.out.println("----------------------------------------");
        
        // HashMap：无序
        Map<String, Integer> hashMap = new HashMap<>();
        hashMap.put("key1", 1);
        hashMap.put("key2", 2);
        hashMap.put("key3", 3);
        hashMap.put("key4", 4);
        hashMap.put("key5", 5);
        
        System.out.println("HashMap（无序）:");
        printMap(hashMap);
        
        // LinkedHashMap：有序
        Map<String, Integer> linkedHashMap = new LinkedHashMap<>();
        linkedHashMap.put("key1", 1);
        linkedHashMap.put("key2", 2);
        linkedHashMap.put("key3", 3);
        linkedHashMap.put("key4", 4);
        linkedHashMap.put("key5", 5);
        
        System.out.println("\nLinkedHashMap（按插入顺序）:");
        printMap(linkedHashMap);
        
        System.out.println("\n结论:");
        System.out.println("  HashMap的遍历顺序不可预测");
        System.out.println("  LinkedHashMap的遍历顺序与插入顺序一致");
        
        System.out.println();
    }

    /**
     * 打印Map
     */
    private static void printMap(Map<String, Integer> map) {
        map.forEach((key, value) -> System.out.println("  " + key + " -> " + value));
    }
}
