package com.fragment.juc.Synchronized.project;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全缓存实现
 * 
 * 实现方式：
 * 1. 使用synchronized实现
 * 2. 使用ConcurrentHashMap实现
 * 3. 双重检查锁定优化
 * 4. 缓存过期策略
 * 
 * @author huabin
 */
public class ThreadSafeCache {
    
    /**
     * 方式1：使用synchronized实现简单缓存
     */
    static class SynchronizedCache<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        
        public synchronized V get(K key) {
            return cache.get(key);
        }
        
        public synchronized void put(K key, V value) {
            cache.put(key, value);
        }
        
        public synchronized void remove(K key) {
            cache.remove(key);
        }
        
        public synchronized void clear() {
            cache.clear();
        }
        
        public synchronized int size() {
            return cache.size();
        }
    }
    
    /**
     * 方式2：使用ConcurrentHashMap实现
     */
    static class ConcurrentCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();
        
        public V get(K key) {
            return cache.get(key);
        }
        
        public void put(K key, V value) {
            cache.put(key, value);
        }
        
        public void remove(K key) {
            cache.remove(key);
        }
        
        public void clear() {
            cache.clear();
        }
        
        public int size() {
            return cache.size();
        }
    }
    
    /**
     * 方式3：带过期时间的缓存
     */
    static class ExpirableCache<K, V> {
        private static class CacheEntry<V> {
            private final V value;
            private final long expireTime;
            
            public CacheEntry(V value, long ttl) {
                this.value = value;
                this.expireTime = System.currentTimeMillis() + ttl;
            }
            
            public boolean isExpired() {
                return System.currentTimeMillis() > expireTime;
            }
            
            public V getValue() {
                return value;
            }
        }
        
        private final Map<K, CacheEntry<V>> cache = new HashMap<>();
        private final long defaultTTL; // 默认过期时间（毫秒）
        
        public ExpirableCache(long defaultTTL) {
            this.defaultTTL = defaultTTL;
        }
        
        public synchronized V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            
            if (entry.isExpired()) {
                cache.remove(key);
                return null;
            }
            
            return entry.getValue();
        }
        
        public synchronized void put(K key, V value) {
            put(key, value, defaultTTL);
        }
        
        public synchronized void put(K key, V value, long ttl) {
            cache.put(key, new CacheEntry<>(value, ttl));
        }
        
        public synchronized void remove(K key) {
            cache.remove(key);
        }
        
        public synchronized void clear() {
            cache.clear();
        }
        
        public synchronized void cleanExpired() {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
        
        public synchronized int size() {
            return cache.size();
        }
    }
    
    /**
     * 方式4：懒加载缓存（双重检查锁定）
     */
    static class LazyLoadCache<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        
        public interface ValueLoader<K, V> {
            V load(K key);
        }
        
        public V get(K key, ValueLoader<K, V> loader) {
            // 第一次检查（无锁）
            V value = cache.get(key);
            if (value != null) {
                return value;
            }
            
            // 加锁
            synchronized(this) {
                // 第二次检查（有锁）
                value = cache.get(key);
                if (value != null) {
                    return value;
                }
                
                // 加载数据
                value = loader.load(key);
                if (value != null) {
                    cache.put(key, value);
                }
                
                return value;
            }
        }
        
        public synchronized void put(K key, V value) {
            cache.put(key, value);
        }
        
        public synchronized void remove(K key) {
            cache.remove(key);
        }
        
        public synchronized void clear() {
            cache.clear();
        }
    }
    
    /**
     * 方式5：LRU缓存（最近最少使用）
     */
    static class LRUCache<K, V> {
        private static class Node<K, V> {
            K key;
            V value;
            Node<K, V> prev;
            Node<K, V> next;
            
            public Node(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }
        
        private final int capacity;
        private final Map<K, Node<K, V>> cache;
        private final Node<K, V> head;
        private final Node<K, V> tail;
        
        public LRUCache(int capacity) {
            this.capacity = capacity;
            this.cache = new HashMap<>();
            this.head = new Node<>(null, null);
            this.tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }
        
        public synchronized V get(K key) {
            Node<K, V> node = cache.get(key);
            if (node == null) {
                return null;
            }
            
            // 移动到头部
            moveToHead(node);
            return node.value;
        }
        
        public synchronized void put(K key, V value) {
            Node<K, V> node = cache.get(key);
            
            if (node != null) {
                // 更新值
                node.value = value;
                moveToHead(node);
            } else {
                // 新增节点
                node = new Node<>(key, value);
                cache.put(key, node);
                addToHead(node);
                
                // 检查容量
                if (cache.size() > capacity) {
                    Node<K, V> removed = removeTail();
                    cache.remove(removed.key);
                }
            }
        }
        
        private void addToHead(Node<K, V> node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
        }
        
        private void removeNode(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }
        
        private void moveToHead(Node<K, V> node) {
            removeNode(node);
            addToHead(node);
        }
        
        private Node<K, V> removeTail() {
            Node<K, V> node = tail.prev;
            removeNode(node);
            return node;
        }
        
        public synchronized void remove(K key) {
            Node<K, V> node = cache.get(key);
            if (node != null) {
                removeNode(node);
                cache.remove(key);
            }
        }
        
        public synchronized void clear() {
            cache.clear();
            head.next = tail;
            tail.prev = head;
        }
        
        public synchronized int size() {
            return cache.size();
        }
    }
    
    /**
     * 测试代码
     */
    public static void main(String[] args) throws InterruptedException {
        // 测试1：基本缓存
        System.out.println("========== 测试1：基本缓存 ==========");
        SynchronizedCache<String, String> cache1 = new SynchronizedCache<>();
        cache1.put("key1", "value1");
        cache1.put("key2", "value2");
        System.out.println("key1: " + cache1.get("key1"));
        System.out.println("size: " + cache1.size());
        
        // 测试2：并发访问
        System.out.println("\n========== 测试2：并发访问 ==========");
        SynchronizedCache<Integer, Integer> cache2 = new SynchronizedCache<>();
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    cache2.put(threadId * 100 + j, j);
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        System.out.println("缓存大小: " + cache2.size());
        
        // 测试3：过期缓存
        System.out.println("\n========== 测试3：过期缓存 ==========");
        ExpirableCache<String, String> cache3 = new ExpirableCache<>(2000); // 2秒过期
        cache3.put("key1", "value1");
        System.out.println("立即获取: " + cache3.get("key1"));
        
        Thread.sleep(2500);
        System.out.println("2.5秒后获取: " + cache3.get("key1"));
        
        // 测试4：懒加载缓存
        System.out.println("\n========== 测试4：懒加载缓存 ==========");
        LazyLoadCache<Integer, String> cache4 = new LazyLoadCache<>();
        
        LazyLoadCache.ValueLoader<Integer, String> loader = key -> {
            System.out.println("加载数据: " + key);
            try {
                Thread.sleep(100); // 模拟耗时操作
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "value-" + key;
        };
        
        System.out.println("第一次获取: " + cache4.get(1, loader));
        System.out.println("第二次获取: " + cache4.get(1, loader)); // 不会重新加载
        
        // 测试5：LRU缓存
        System.out.println("\n========== 测试5：LRU缓存 ==========");
        LRUCache<Integer, String> cache5 = new LRUCache<>(3);
        cache5.put(1, "value1");
        cache5.put(2, "value2");
        cache5.put(3, "value3");
        System.out.println("缓存大小: " + cache5.size());
        
        cache5.put(4, "value4"); // 淘汰最久未使用的
        System.out.println("添加第4个元素后，缓存大小: " + cache5.size());
        System.out.println("获取key=1: " + cache5.get(1)); // null，已被淘汰
        
        System.out.println("\n所有测试完成！");
    }
}
