package com.fragment.core.collections.linkedhashmap.demo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LinkedHashMap实现LRU缓存演示
 * 
 * 演示内容：
 * 1. 简单LRU缓存实现
 * 2. LRU缓存的工作原理
 * 3. removeEldestEntry方法的使用
 * 
 * @author huabin
 */
public class LinkedHashMapLRUDemo {

    public static void main(String[] args) {
        System.out.println("========== LinkedHashMap实现LRU缓存演示 ==========\n");
        
        // 1. 简单LRU缓存
        simpleLRUCache();
        
        // 2. LRU缓存工作原理
        lruCacheWorkflow();
    }

    /**
     * 1. 简单LRU缓存
     */
    private static void simpleLRUCache() {
        System.out.println("1. 简单LRU缓存实现");
        System.out.println("----------------------------------------");
        
        // 创建容量为3的LRU缓存
        Map<String, String> cache = new LinkedHashMap<String, String>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                boolean shouldRemove = size() > 3;
                if (shouldRemove) {
                    System.out.println("  [淘汰] " + eldest.getKey() + " = " + eldest.getValue());
                }
                return shouldRemove;
            }
        };
        
        System.out.println("最大容量: 3\n");
        
        // 添加元素
        System.out.println("添加 key1=value1:");
        cache.put("key1", "value1");
        printCache(cache);
        
        System.out.println("添加 key2=value2:");
        cache.put("key2", "value2");
        printCache(cache);
        
        System.out.println("添加 key3=value3:");
        cache.put("key3", "value3");
        printCache(cache);
        
        // 访问key1（key1移到最后）
        System.out.println("访问 key1:");
        cache.get("key1");
        printCache(cache);
        
        // 添加key4（淘汰最久未使用的key2）
        System.out.println("添加 key4=value4:");
        cache.put("key4", "value4");
        printCache(cache);
        
        // 访问key3（key3移到最后）
        System.out.println("访问 key3:");
        cache.get("key3");
        printCache(cache);
        
        // 添加key5（淘汰最久未使用的key1）
        System.out.println("添加 key5=value5:");
        cache.put("key5", "value5");
        printCache(cache);
        
        System.out.println();
    }

    /**
     * 2. LRU缓存工作原理
     */
    private static void lruCacheWorkflow() {
        System.out.println("2. LRU缓存工作原理");
        System.out.println("----------------------------------------");
        
        System.out.println("LRU（Least Recently Used）最近最少使用算法");
        System.out.println("\n核心思想:");
        System.out.println("  1. 最近访问的元素放在链表尾部");
        System.out.println("  2. 最久未访问的元素在链表头部");
        System.out.println("  3. 当缓存满时，删除链表头部的元素");
        
        System.out.println("\n实现要点:");
        System.out.println("  1. accessOrder=true：按访问顺序维护链表");
        System.out.println("  2. removeEldestEntry：返回true时删除最老的元素");
        System.out.println("  3. get操作会将元素移到链表尾部");
        System.out.println("  4. put操作会将元素添加到链表尾部");
        
        System.out.println("\n时间复杂度:");
        System.out.println("  get: O(1)");
        System.out.println("  put: O(1)");
        System.out.println("  remove: O(1)");
        
        System.out.println("\n适用场景:");
        System.out.println("  1. 数据库查询缓存");
        System.out.println("  2. 图片缓存");
        System.out.println("  3. API响应缓存");
        System.out.println("  4. 页面缓存");
        
        System.out.println();
    }

    /**
     * 打印缓存内容
     */
    private static void printCache(Map<String, String> cache) {
        System.out.print("  缓存内容: [");
        boolean first = true;
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (!first) {
                System.out.print(", ");
            }
            System.out.print(entry.getKey());
            first = false;
        }
        System.out.println("]");
    }
}
