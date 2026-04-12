# 第二章：TreeMap 核心原理与红黑树

## 2.1 为什么用红黑树而不是普通 BST？

普通二叉搜索树（BST）的问题：按有序数据插入会退化为链表，查找退化为 O(n)：

```
按 1,2,3,4,5 顺序插入 BST：
1
 \
  2
   \
    3
     \
      4
       \
        5
查找 5：需要遍历 5 个节点 → O(n)
```

红黑树通过**旋转和变色**保持树的平衡，保证树高始终 ≤ 2·log₂(n)，查找始终 O(log n)。

---

## 2.2 红黑树的五条性质

```
1. 每个节点是红色或黑色
2. 根节点是黑色
3. 叶子节点（NIL 哨兵节点）是黑色
4. 红色节点的两个子节点都是黑色（不能有连续红色节点）
5. 从任一节点到其所有后代叶子节点的路径，经过相同数量的黑色节点（黑高相同）
```

这五条性质共同保证：**最长路径（红黑交替）不超过最短路径（全黑）的 2 倍**，从而保证 O(log n)。

---

## 2.3 TreeMap 的节点结构

```java
// TreeMap 源码（JDK 8）
static final class Entry<K,V> implements Map.Entry<K,V> {
    K key;
    V value;
    Entry<K,V> left;    // 左子节点（key 更小）
    Entry<K,V> right;   // 右子节点（key 更大）
    Entry<K,V> parent;  // 父节点
    boolean color;      // RED = false, BLACK = true
}
```

**TreeMap.get() 的完整路径**：

```java
// 从根节点开始，按 key 大小向左/右走，直到找到目标
public V get(Object key) {
    Entry<K,V> p = getEntry(key);
    return (p == null ? null : p.value);
}

final Entry<K,V> getEntry(Object key) {
    Entry<K,V> p = root;
    while (p != null) {
        int cmp = compare(key, p.key);
        if (cmp < 0) p = p.left;        // key < 当前节点，走左子树
        else if (cmp > 0) p = p.right;  // key > 当前节点，走右子树
        else return p;                   // 找到
    }
    return null;
}
```

---

## 2.4 导航方法的实现原理

`TreeMapBasicDemo.java → demonstrateNavigationMethods()` 演示的 `floorKey`、`ceilingKey` 等方法：

```java
// floorKey(k)：找到 ≤ k 的最大 key
// 原理：在 BST 中搜索 k，记录沿途遇到的最后一个 ≤ k 的节点
final Entry<K,V> getFloorEntry(K key) {
    Entry<K,V> p = root;
    while (p != null) {
        int cmp = compare(key, p.key);
        if (cmp > 0) {
            if (p.right != null) p = p.right;
            else return p;  // 没有更大的了，当前节点就是 floor
        } else if (cmp < 0) {
            p = p.left;     // key 更小，向左走
        } else {
            return p;       // 精确匹配
        }
    }
    return null;
}
```

这就是为什么 `floorKey`、`ceilingKey` 是 O(log n) 而不是 O(n)——它们利用了红黑树的有序结构。

---

## 2.5 范围视图（subMap）的实现

`TreeMapBasicDemo.java → demonstrateRangeViews()` 中的 `subMap` 返回的是视图：

```java
// subMap 内部实现（简化）：
// 返回一个 SubMap 视图对象，包含 lo/hi 边界
// 所有操作在执行前先检查是否在边界范围内
// 遍历时利用 TreeMap 的 NavigableMap 接口从 loEntry 遍历到 hiEntry

// ⚠️ 这是视图，不是拷贝：
NavigableMap<Integer, String> sub = map.subMap(3, true, 7, true);
sub.put(5, "NEW");   // 修改 sub → 原 map 也变
map.put(4, "ORIG");  // 修改原 map → sub 也变
map.put(8, "OUT");   // 超出范围 → 在 sub 中不可见，但 map 中存在
sub.put(8, "OUT");   // ❌ IllegalArgumentException：超出 subMap 范围
```

---

## 2.6 本章总结

- **红黑树 vs BST**：红黑树通过旋转变色保持平衡，避免 BST 在有序数据下退化为 O(n)
- **五条性质**：红黑交替、黑高相同，保证树高 ≤ 2·log₂(n)
- **get/put/remove**：沿树按 key 大小向左右走，O(log n)
- **导航方法**：floor/ceiling/lower/higher 也是 O(log n)，利用 BST 有序结构
- **subMap 是视图**：修改视图影响原 map，超出范围的操作抛异常

> **本章对应演示代码**：`TreeMapInternalsDemo.java`（平衡性演示、key 要求、性能对比）

**继续阅读**：[03_TreeMap导航与范围查询.md](./03_TreeMap导航与范围查询.md)
