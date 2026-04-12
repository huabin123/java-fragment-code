# 第一章：LinkedHashMap 的必要性与应用场景

## 1.1 核心问题：HashMap 丢失了什么？

HashMap 的 O(1) 查找是通过 hash 散列实现的——元素的存储位置由 hash 值决定，与插入顺序完全无关。这意味着遍历 HashMap 时，得到的顺序是不可预期的（取决于 hash 值分布和数组容量）。

```java
Map<String, Integer> hashMap = new HashMap<>();
hashMap.put("banana", 2);
hashMap.put("apple", 1);
hashMap.put("cherry", 3);

// 遍历结果可能是：banana=2, cherry=3, apple=1（顺序不定）
hashMap.forEach((k, v) -> System.out.println(k + "=" + v));
```

在很多业务场景中，**顺序本身就是数据的一部分**：
- JSON 序列化：配置文件、API 响应中字段顺序有业务含义
- 配置管理：`server.host` 应该在 `server.port` 之前（`ConfigurationManager.java`）
- 操作日志：最近的操作排在前面/后面
- LRU 缓存：最近访问的在最后，最久未访问的在最前（可驱逐）

LinkedHashMap 在 HashMap 的基础上，增加了一条**贯穿所有节点的双向链表**，维护遍历顺序。

---

## 1.2 两种顺序模式

LinkedHashMap 支持两种顺序模式，通过构造函数的第三个参数 `accessOrder` 控制：

### 模式一：插入顺序（accessOrder=false，默认）

```java
// LinkedHashMapBasicDemo.java → insertionOrderMode()
Map<String, Integer> map = new LinkedHashMap<>();  // 等价于 new LinkedHashMap<>(16, 0.75f, false)
map.put("banana", 2);
map.put("apple", 1);
map.put("cherry", 3);

// 遍历结果固定是插入顺序：banana, apple, cherry
map.forEach((k, v) -> System.out.println(k + "=" + v));

// 重要：put 已存在的 key 不改变顺序（只更新 value）
map.put("banana", 99);  // banana 的位置不变，仍然在最前
```

**使用场景**：配置文件、JSON 序列化、需要稳定输出顺序的任何场景。

### 模式二：访问顺序（accessOrder=true）

```java
// LinkedHashMapBasicDemo.java → accessOrderMode()
Map<String, Integer> map = new LinkedHashMap<>(16, 0.75f, true);
map.put("A", 1);
map.put("B", 2);
map.put("C", 3);

// 初始顺序：A, B, C
map.get("A");  // 访问 A → A 移到链表尾部，顺序变为：B, C, A
map.get("B");  // 访问 B → B 移到链表尾部，顺序变为：C, A, B

// 遍历结果：C=3, A=1, B=2（按最近访问时间排序，最近的在尾部）
map.forEach((k, v) -> System.out.println(k + "=" + v));
```

**使用场景**：LRU 缓存（最久未访问的元素在链表头部，是驱逐的候选者）。

---

## 1.3 与 HashMap 的关系：继承而非组合

```
LinkedHashMap extends HashMap
```

LinkedHashMap 不是"HashMap + 单独的 LinkedList"的组合，而是**继承 HashMap 并在其节点上额外加了两个指针**：

```java
// LinkedHashMap.Entry 继承 HashMap.Node，增加了双向链表指针
static class Entry<K,V> extends HashMap.Node<K,V> {
    Entry<K,V> before, after;  // 双向链表指针
}
```

这意味着每个节点**同时存在于 HashMap 的桶数组和 LinkedHashMap 的双向链表**中。所有的 HashMap 操作（put/get/remove）原有逻辑不变，LinkedHashMap 只是在操作完成后，通过 HashMap 预留的钩子方法（`afterNodeAccess`、`afterNodeInsertion`、`afterNodeRemoval`）维护双向链表的顺序。

---

## 1.4 LRU 缓存：最经典的应用

**LRU（Least Recently Used）**缓存的核心逻辑：
- 每次访问（get/put）把元素移到链表尾部（"最新"）
- 链表头部始终是最久未访问的元素（"最旧"，驱逐候选）
- 当容量超限时，驱逐链表头部元素

LinkedHashMap 在 `accessOrder=true` 模式下，`get` 操作会自动将节点移到链表尾部，配合 `removeEldestEntry` 回调，可以用极少代码实现完整的 LRU：

```java
// LinkedHashMapLRUDemo.java → simpleLRUCache()
Map<String, String> lruCache = new LinkedHashMap<String, String>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > MAX_CAPACITY;  // 超容量时，自动驱逐最老的（链表头部）
    }
};
```

`DatabaseQueryCache.java` 将这个模式升级为可统计命中率的实战版本：

```java
// DatabaseQueryCache.java
this.cache = new LinkedHashMap<String, QueryResult>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, QueryResult> eldest) {
        boolean shouldRemove = size() > maxSize;
        if (shouldRemove) evictionCount++;  // 统计驱逐次数
        return shouldRemove;
    }
};

// 使用：cache.get() 自动触发 LRU 顺序更新
QueryResult result = cache.get(cacheKey);
if (result != null) { hitCount++; return result; }
```

---

## 1.5 插入顺序的保证：比你想象的更细致

插入顺序模式下，遍历顺序等于 **put 操作的发生顺序**，但有一个重要细节：

```java
Map<String, Integer> map = new LinkedHashMap<>();
map.put("A", 1);  // A 排在第1位
map.put("B", 2);  // B 排在第2位
map.put("A", 99); // 更新 A 的值，但 A 的位置不变（仍然第1位）

// 遍历：A=99, B=2
// 对比 HashMap：可能是 B=2, A=99（无序）
```

对于需要维护"首次插入顺序"的场景，这个特性很关键（如 JSON 序列化时保持字段声明顺序）。

---

## 1.6 与 TreeMap 的区别：两种不同的"有序"

| 维度 | LinkedHashMap | TreeMap |
|------|--------------|---------|
| 有序类型 | 插入顺序或访问顺序 | key 的自然顺序（升序）|
| 底层结构 | HashMap + 双向链表 | 红黑树 |
| get/put | O(1) | O(log n) |
| 额外功能 | LRU 缓存 | `firstKey`/`lastKey`/`subMap` 范围查询 |
| 适用场景 | 需要维护处理顺序 | 需要按 key 范围查询 |

---

## 1.7 本章总结

- **存在原因**：HashMap 不保证遍历顺序，LinkedHashMap 通过双向链表维护顺序
- **两种模式**：插入顺序（默认，适合配置/JSON）；访问顺序（`accessOrder=true`，适合 LRU）
- **继承关系**：LinkedHashMap extends HashMap，Entry 增加 `before/after` 指针，通过钩子方法维护链表
- **LRU 的简洁性**：3 行代码（覆盖 `removeEldestEntry`）就能实现完整的 LRU 驱逐逻辑
- **put 已存在 key**：不改变该 key 的顺序（只更新 value），这是常见的认知误区

> **本章对应演示代码**：`LinkedHashMapBasicDemo.java`（两种顺序模式）、`LinkedHashMapLRUDemo.java`（LRU 缓存工作原理）、`DatabaseQueryCache.java`（实战 LRU 缓存）

**继续阅读**：[02_LinkedHashMap核心原理与数据结构.md](./02_LinkedHashMap核心原理与数据结构.md)
