# 第一章：TreeMap 的必要性与应用场景

## 1.1 HashMap 解决不了的问题

HashMap 查找是 O(1) 的，但它无法回答"有序"的问题：

```java
HashMap<Integer, String> map = new HashMap<>();
map.put(3, "C"); map.put(1, "A"); map.put(4, "D"); map.put(2, "B");
System.out.println(map);  // {1=A, 2=B, 3=C, 4=D}（巧合有序，不保证）

// ❌ 无法高效回答：
// 1. 比 3 小的最大 key 是什么？
// 2. [2, 4] 范围内有哪些 key？
// 3. 遍历时按 key 从小到大？

// 以上问题用 HashMap 都需要 O(n) 扫描所有 key
```

TreeMap 基于红黑树，所有 key 有序存储，上面的问题都是 O(log n)。

---

## 1.2 TreeMap 的核心特征

```
底层结构：红黑树（自平衡二叉搜索树）
key 必须：实现 Comparable 或构造时传入 Comparator
所有操作：O(log n)——put/get/remove/floor/ceiling
有序性：遍历 key 始终有序（按 Comparator 或自然顺序）
```

---

## 1.3 最适合 TreeMap 的场景

**场景一：范围查询**

```java
// RangePriceIndex.java → getProductsInRange()
// 查询 1000~5000 元之间的商品
index.subMap(1000.0, true, 5000.0, true).values()
// O(log n) 定位起点 + O(k) 遍历结果，k 是结果数量
```

**场景二：最近值查找（floor/ceiling）**

```java
// ScheduleManager.java → nextSchedule()
// 查询当前时间之后的下一个日程
schedules.higherEntry(now);  // O(log n)

// TreeMapApplicationDemo.java → demonstrateRangeMapping()
// 分数→等级映射
gradeMap.get(gradeMap.floorKey(score));  // O(log n)
```

**场景三：有序遍历**

```java
// TreeMapApplicationDemo.java → demonstrateWordFrequency()
TreeMap<String, Integer> freq = new TreeMap<>();
// 插入词频后，遍历自动按字母顺序输出，无需额外排序
freq.forEach((word, count) -> System.out.println(word + ": " + count));
```

**场景四：时间线/日程管理**

```java
// ScheduleManager.java
TreeMap<LocalDateTime, Schedule> schedules = new TreeMap<>();
// 自动按时间顺序存储，支持范围查询、冲突检测
```

---

## 1.4 不适合 TreeMap 的场景

```java
// ❌ 只需要 key-value 存取，不需要有序 → 用 HashMap（快 3-5 倍）
TreeMap<String, User> userMap = new TreeMap<>();  // 浪费

// ❌ 需要保持插入顺序 → 用 LinkedHashMap
TreeMap<String, Config> config = new TreeMap<>();  // 会改变顺序

// ❌ key 没有自然顺序也不想写 Comparator → 无法使用
// TreeMap 的 key 必须可比较，否则 put 时 ClassCastException
```

---

## 1.5 本章总结

- **TreeMap 存在的理由**：有序、范围查询、floor/ceiling 导航——HashMap 做不到
- **底层**：红黑树，所有操作 O(log n)
- **必要条件**：key 实现 Comparable 或构造时传 Comparator
- **首选场景**：时间线、价格区间、分数映射、词典排序输出

> **本章对应演示代码**：`TreeMapBasicDemo.java`（有序遍历、导航方法、范围视图）

**继续阅读**：[02_TreeMap核心原理与红黑树.md](./02_TreeMap核心原理与红黑树.md)
