package com.fragment.core.collections.hashmap.project;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU缓存实现
 * 
 * 使用LinkedHashMap实现LRU（Least Recently Used）缓存
 * 
 * 特性：
 * 1. 固定容量
 * 2. 自动淘汰最久未使用的元素
 * 3. O(1)时间复杂度的get和put操作
 * 4. 线程不安全（如需线程安全，需要外部同步）
 * 
 * @author huabin
 */
public class LRUCache<K, V> {
    
    private final Map<K, V> cache;
    private final int maxSize;
    private int hitCount = 0;
    private int missCount = 0;
    private int evictionCount = 0;

    /**
     * 构造函数
     * 
     * @param maxSize 最大容量
     */
    public LRUCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldRemove = size() > maxSize;
                if (shouldRemove) {
                    evictionCount++;
                    System.out.println("[LRU] 淘汰: " + eldest.getKey() + " = " + eldest.getValue());
                }
                return shouldRemove;
            }
        };
    }

    /**
     * 获取缓存值
     * 
     * @param key 键
     * @return 值，如果不存在返回null
     */
    public V get(K key) {
        V value = cache.get(key);
        if (value != null) {
            hitCount++;
            System.out.println("[LRU] 命中: " + key);
        } else {
            missCount++;
            System.out.println("[LRU] 未命中: " + key);
        }
        return value;
    }

    /**
     * 添加缓存
     * 
     * @param key 键
     * @param value 值
     * @return 旧值，如果不存在返回null
     */
    public V put(K key, V value) {
        System.out.println("[LRU] 添加: " + key + " = " + value);
        return cache.put(key, value);
    }

    /**
     * 删除缓存
     * 
     * @param key 键
     * @return 被删除的值，如果不存在返回null
     */
    public V remove(K key) {
        V value = cache.remove(key);
        if (value != null) {
            System.out.println("[LRU] 删除: " + key + " = " + value);
        }
        return value;
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
        hitCount = 0;
        missCount = 0;
        evictionCount = 0;
        System.out.println("[LRU] 清空缓存");
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 获取最大容量
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * 获取命中次数
     */
    public int getHitCount() {
        return hitCount;
    }

    /**
     * 获取未命中次数
     */
    public int getMissCount() {
        return missCount;
    }

    /**
     * 获取淘汰次数
     */
    public int getEvictionCount() {
        return evictionCount;
    }

    /**
     * 获取命中率
     */
    public double getHitRate() {
        int total = hitCount + missCount;
        return total == 0 ? 0 : (double) hitCount / total;
    }

    /**
     * 打印统计信息
     */
    public void printStats() {
        System.out.println("\n========== LRU缓存统计 ==========");
        System.out.println("最大容量: " + maxSize);
        System.out.println("当前大小: " + size());
        System.out.println("命中次数: " + hitCount);
        System.out.println("未命中次数: " + missCount);
        System.out.println("命中率: " + String.format("%.2f%%", getHitRate() * 100));
        System.out.println("淘汰次数: " + evictionCount);
        System.out.println("================================\n");
    }

    /**
     * 打印缓存内容
     */
    public void printCache() {
        System.out.println("\n当前缓存内容（按访问顺序）:");
        cache.forEach((key, value) -> System.out.println("  " + key + " = " + value));
        System.out.println();
    }

    /**
     * 测试示例
     */
    public static void main(String[] args) {
        System.out.println("========== LRU缓存测试 ==========\n");
        
        // 创建容量为3的LRU缓存
        LRUCache<String, String> cache = new LRUCache<>(3);
        
        // 添加元素
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        cache.printCache();
        
        // 访问key1（key1移到最后）
        cache.get("key1");
        cache.printCache();
        
        // 添加key4（淘汰最久未使用的key2）
        cache.put("key4", "value4");
        cache.printCache();
        
        // 访问key3（key3移到最后）
        cache.get("key3");
        cache.printCache();
        
        // 添加key5（淘汰最久未使用的key1）
        cache.put("key5", "value5");
        cache.printCache();
        
        // 访问不存在的key
        cache.get("key1");  // 未命中
        cache.get("key2");  // 未命中
        
        // 打印统计信息
        cache.printStats();
    }
}
