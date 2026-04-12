# 第四章：TreeMap vs HashMap vs LinkedHashMap

## 4.1 三者的本质区别

| 维度 | HashMap | LinkedHashMap | TreeMap |
|------|---------|---------------|---------|
| 底层结构 | 数组 + 链表/红黑树 | HashMap + 双向链表 | 红黑树 |
| key 顺序 | 无序（哈希分布）| 插入顺序（或访问顺序）| 排序顺序（Comparator）|
| put/get | O(1) 均摊 | O(1) 均摊 | O(log n) |
| 范围查询 | ❌ 不支持 | ❌ 不支持 | ✅ 支持 |
| key 要求 | 实现 hashCode/equals | 实现 hashCode/equals | 实现 Comparable 或传 Comparator |
| null key | ✅ 允许一个 | ✅ 允许一个 | ❌ 不允许（无法比较）|
| 内存开销 | 最低 | 中（多一条链表）| 最高（每个节点含 4 个引用）|

---

## 4.2 选型决策树

```
需要 Map？
  ↓
需要按 key 排序 / 范围查询 / floor/ceiling？
  → 是 → TreeMap

需要保持插入顺序 / LRU 缓存？
  → 是 → LinkedHashMap

只需要快速存取，不关心顺序？
  → 否 → HashMap（首选，性能最好）
```

---

## 4.3 null key 的差异

```java
// HashMap：允许 null key（存在 table[0] 位置）
HashMap<String, Integer> hashMap = new HashMap<>();
hashMap.put(null, 1);   // ✅

// LinkedHashMap：继承 HashMap，同样允许 null key
LinkedHashMap<String, Integer> linkedMap = new LinkedHashMap<>();
linkedMap.put(null, 1); // ✅

// TreeMap：null key 会在比较时抛 NullPointerException
TreeMap<String, Integer> treeMap = new TreeMap<>();
treeMap.put(null, 1);   // ❌ NullPointerException: key is null
// 原因：compare(null, firstKey) 调用 null.compareTo(x) → NPE
```

---

## 4.4 性能对比实测

`TreeMapInternalsDemo.java → demonstratePerformanceVsHashMap()` 的典型结果：

```
各 500,000 次 put + get：

HashMap:  ~120ms（O(1) 均摊，哈希直接寻址）
TreeMap:  ~650ms（O(log n)，每次操作约 19 次比较）

差距约 5 倍——当数据量增大，差距更显著：
1,000,000 次操作：HashMap ~240ms，TreeMap ~1500ms（差距 6 倍）
```

**结论**：不需要有序时，HashMap 性能优势明显；需要排序/范围查询时，TreeMap 是唯一选择。

---

## 4.5 典型使用场景汇总

```java
// HashMap：缓存、索引、计数（最常用）
Map<String, User> userCache = new HashMap<>();
Map<String, Integer> wordCount = new HashMap<>();

// LinkedHashMap：需要顺序的缓存（LRU）、保持配置顺序
Map<String, String> config = new LinkedHashMap<>();  // 保持配置文件的书写顺序
Map<K, V> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return size() > MAX_SIZE;
    }
};

// TreeMap：有序索引、范围查询、时间线、价格区间
TreeMap<LocalDateTime, Event> timeline = new TreeMap<>();
TreeMap<Double, String> priceRange = new TreeMap<>();
TreeMap<String, Integer> sortedWordFreq = new TreeMap<>();  // 字母排序输出
```

---

## 4.6 本章总结

- **HashMap**：O(1)，无序，最常用
- **LinkedHashMap**：O(1)，有序（插入/访问顺序），适合 LRU 和需要保持顺序的场景
- **TreeMap**：O(log n)，排序顺序，适合范围查询、导航、有序输出
- **null key**：HashMap/LinkedHashMap 允许；TreeMap 不允许
- **内存**：HashMap < LinkedHashMap < TreeMap（TreeMap 每节点需要左右子和父节点三个指针）

> **本章对应演示代码**：`TreeMapInternalsDemo.java`（性能对比）、`TreeMapApplicationDemo.java`（各场景实战）

**继续阅读**：[05_TreeMap最佳实践.md](./05_TreeMap最佳实践.md)
