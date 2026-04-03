package com.fragment.core.collections.hashmap.demo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * HashMap扩容机制演示
 * 
 * 演示内容：
 * 1. 扩容的触发条件
 * 2. 扩容的过程
 * 3. 扩容对性能的影响
 * 4. 如何避免频繁扩容
 * 
 * @author huabin
 */
public class HashMapResizeDemo {

    public static void main(String[] args) {
        System.out.println("========== HashMap扩容机制演示 ==========\n");
        
        // 1. 扩容的触发条件
        resizeTrigger();
        
        // 2. 扩容对性能的影响
        resizePerformance();
        
        // 3. 设置初始容量避免扩容
        avoidResize();
    }

    /**
     * 1. 扩容的触发条件
     */
    private static void resizeTrigger() {
        System.out.println("1. 扩容的触发条件");
        System.out.println("----------------------------------------");
        
        // 创建初始容量为4的HashMap
        Map<Integer, String> map = new HashMap<>(4);
        
        System.out.println("初始状态:");
        printMapInfo(map);
        
        // 添加元素，观察扩容
        for (int i = 1; i <= 10; i++) {
            map.put(i, "value" + i);
            System.out.println("\n添加第" + i + "个元素后:");
            printMapInfo(map);
            
            // 当size > capacity * loadFactor时，会触发扩容
            // 默认loadFactor=0.75
            // capacity=4时，threshold=4*0.75=3，添加第4个元素时扩容
            // capacity=8时，threshold=8*0.75=6，添加第7个元素时扩容
        }
        
        System.out.println();
    }

    /**
     * 2. 扩容对性能的影响
     */
    private static void resizePerformance() {
        System.out.println("2. 扩容对性能的影响");
        System.out.println("----------------------------------------");
        
        // 场景1：频繁扩容（使用默认容量）
        long start1 = System.nanoTime();
        Map<Integer, String> map1 = new HashMap<>();
        for (int i = 0; i < 100000; i++) {
            map1.put(i, "value" + i);
        }
        long end1 = System.nanoTime();
        
        // 场景2：不扩容（设置合适的初始容量）
        long start2 = System.nanoTime();
        Map<Integer, String> map2 = new HashMap<>(150000);
        for (int i = 0; i < 100000; i++) {
            map2.put(i, "value" + i);
        }
        long end2 = System.nanoTime();
        
        System.out.println("插入100000个元素:");
        System.out.println("  频繁扩容: " + (end1 - start1) / 1000000.0 + "ms");
        System.out.println("  不扩容: " + (end2 - start2) / 1000000.0 + "ms");
        System.out.println("  性能提升: " + String.format("%.2f", (end1 - start1) / (double) (end2 - start2)) + "倍");
        
        System.out.println();
    }

    /**
     * 3. 设置初始容量避免扩容
     */
    private static void avoidResize() {
        System.out.println("3. 设置初始容量避免扩容");
        System.out.println("----------------------------------------");
        
        int expectedSize = 1000;
        
        // 错误做法：直接使用expectedSize
        Map<Integer, String> map1 = new HashMap<>(expectedSize);
        System.out.println("错误做法：new HashMap(" + expectedSize + ")");
        printMapInfo(map1);
        System.out.println("  问题：capacity=" + getCapacity(map1) + ", threshold=" + getThreshold(map1));
        System.out.println("  当添加" + getThreshold(map1) + "个元素后会扩容");
        
        // 正确做法：考虑负载因子
        int initialCapacity = (int) (expectedSize / 0.75f + 1);
        Map<Integer, String> map2 = new HashMap<>(initialCapacity);
        System.out.println("\n正确做法：new HashMap(" + initialCapacity + ")");
        printMapInfo(map2);
        System.out.println("  capacity=" + getCapacity(map2) + ", threshold=" + getThreshold(map2));
        System.out.println("  可以容纳" + getThreshold(map2) + "个元素而不扩容");
        
        // 验证
        System.out.println("\n验证：添加" + expectedSize + "个元素");
        for (int i = 0; i < expectedSize; i++) {
            map2.put(i, "value" + i);
        }
        System.out.println("  添加后capacity=" + getCapacity(map2) + "（未扩容）");
        
        System.out.println();
    }

    /**
     * 打印HashMap的内部信息
     */
    private static void printMapInfo(Map<?, ?> map) {
        System.out.println("  size=" + map.size() + 
                         ", capacity=" + getCapacity(map) + 
                         ", threshold=" + getThreshold(map) + 
                         ", loadFactor=" + getLoadFactor(map));
    }

    /**
     * 通过反射获取HashMap的capacity
     */
    private static int getCapacity(Map<?, ?> map) {
        try {
            Field tableField = HashMap.class.getDeclaredField("table");
            tableField.setAccessible(true);
            Object[] table = (Object[]) tableField.get(map);
            return table == null ? 0 : table.length;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 通过反射获取HashMap的threshold
     */
    private static int getThreshold(Map<?, ?> map) {
        try {
            Field thresholdField = HashMap.class.getDeclaredField("threshold");
            thresholdField.setAccessible(true);
            return (int) thresholdField.get(map);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 通过反射获取HashMap的loadFactor
     */
    private static float getLoadFactor(Map<?, ?> map) {
        try {
            Field loadFactorField = HashMap.class.getDeclaredField("loadFactor");
            loadFactorField.setAccessible(true);
            return (float) loadFactorField.get(map);
        } catch (Exception e) {
            return 0.75f;
        }
    }
}
