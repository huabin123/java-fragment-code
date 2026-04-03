package com.fragment.juc.container.demo;

/**
 * @Author huabin
 * @DateTime 2026-01-26 14:19
 * @Desc
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 完整的跳表实现（生产级代码）
 */
public class SkipList<K extends Comparable<K>, V> {

    private static final int MAX_LEVEL = 32;
    private static final double P = 0.25;

    private final Node<K, V> header;
    private int level;
    private int size;
    private final Random random;

    /**
     * 跳表节点
     */
    static class Node<K, V> {
        K key;
        V value;
        Node<K, V>[] forward;

        @SuppressWarnings("unchecked")
        Node(int level) {
            forward = new Node[level];
        }

        Node(K key, V value, int level) {
            this.key = key;
            this.value = value;
            this.forward = new Node[level];
        }
    }

    public SkipList() {
        header = new Node<>(MAX_LEVEL);
        level = 1;
        size = 0;
        random = new Random();
    }

    /**
     * 插入元素
     */
    public void put(K key, V value) {
        @SuppressWarnings("unchecked")
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V> current = header;

        // 1. 查找插入位置
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null &&
                    current.forward[i].key.compareTo(key) < 0) {
                current = current.forward[i];
            }
            update[i] = current;
        }

        current = current.forward[0];

        // 2. 如果 key 已存在，更新 value
        if (current != null && current.key.compareTo(key) == 0) {
            current.value = value;
            return;
        }

        // 3. 生成随机层数
        int newLevel = randomLevel();

        // 4. 如果新层数大于当前最大层数
        if (newLevel > level) {
            for (int i = level; i < newLevel; i++) {
                update[i] = header;
            }
            level = newLevel;
        }

        // 5. 创建新节点并插入
        Node<K, V> newNode = new Node<>(key, value, newLevel);
        for (int i = 0; i < newLevel; i++) {
            newNode.forward[i] = update[i].forward[i];
            update[i].forward[i] = newNode;
        }

        size++;
    }

    /**
     * 查找元素
     */
    public V get(K key) {
        Node<K, V> current = header;

        // 从最高层开始查找
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null &&
                    current.forward[i].key.compareTo(key) < 0) {
                current = current.forward[i];
            }
        }

        current = current.forward[0];

        if (current != null && current.key.compareTo(key) == 0) {
            return current.value;
        }

        return null;
    }

    /**
     * 删除元素
     */
    public boolean remove(K key) {
        @SuppressWarnings("unchecked")
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V> current = header;

        // 1. 查找要删除的节点
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null &&
                    current.forward[i].key.compareTo(key) < 0) {
                current = current.forward[i];
            }
            update[i] = current;
        }

        current = current.forward[0];

        // 2. 如果节点不存在
        if (current == null || current.key.compareTo(key) != 0) {
            return false;
        }

        // 3. 删除节点
        for (int i = 0; i < level; i++) {
            if (update[i].forward[i] == current) {
                update[i].forward[i] = current.forward[i];
            }
        }

        // 4. 更新最大层数
        while (level > 1 && header.forward[level - 1] == null) {
            level--;
        }

        size--;
        return true;
    }

    /**
     * 范围查询
     */
    public List<Entry<K, V>> range(K minKey, K maxKey) {
        List<Entry<K, V>> result = new ArrayList<>();
        Node<K, V> current = header;

        // 1. 找到第一个 >= minKey 的节点
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null &&
                    current.forward[i].key.compareTo(minKey) < 0) {
                current = current.forward[i];
            }
        }

        current = current.forward[0];

        // 2. 收集范围内的节点
        while (current != null && current.key.compareTo(maxKey) <= 0) {
            result.add(new Entry<>(current.key, current.value));
            current = current.forward[0];
        }

        return result;
    }

    /**
     * 获取大小
     */
    public int size() {
        return size;
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 随机层数生成
     */
    private int randomLevel() {
        int lvl = 1;
        while (random.nextDouble() < P && lvl < MAX_LEVEL) {
            lvl++;
        }
        return lvl;
    }

    /**
     * 打印跳表结构（调试用）
     */
    public void print() {
        for (int i = level - 1; i >= 0; i--) {
            System.out.print("Level " + i + ": ");
            Node<K, V> current = header.forward[i];
            while (current != null) {
                System.out.print(current.key + "(" + current.value + ") -> ");
                current = current.forward[i];
            }
            System.out.println("NULL");
        }
    }

    /**
     * 键值对
     */
    public static class Entry<K, V> {
        private final K key;
        private final V value;

        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }
}
