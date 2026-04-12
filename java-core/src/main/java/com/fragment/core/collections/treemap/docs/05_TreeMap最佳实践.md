# 第五章：TreeMap 最佳实践

## 5.1 Comparator 的正确写法

```java
// ❌ 容易溢出的整数相减写法
TreeMap<Integer, String> map = new TreeMap<>((a, b) -> a - b);
// 当 a = Integer.MIN_VALUE, b = 1 时：溢出，比较结果错误！

// ✅ 用 Integer.compare
TreeMap<Integer, String> map = new TreeMap<>(Integer::compare);

// ✅ 用 Comparator 链式 API（多字段排序）
TreeMap<Student, Integer> byScore = new TreeMap<>(
    Comparator.comparingInt(Student::getScore)
              .reversed()
              .thenComparing(Student::getName)
);

// ✅ 字符串忽略大小写排序
TreeMap<String, Integer> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
```

## 5.2 floorKey 区间映射的标准模式

```java
// 标准模式：区间下限作 key，floorKey 查找所属区间
TreeMap<Integer, String> levelMap = new TreeMap<>();
levelMap.put(0,   "初级");
levelMap.put(100, "中级");
levelMap.put(300, "高级");
levelMap.put(600, "专家");

// 查询
private String getLevel(int score) {
    Integer key = levelMap.floorKey(score);
    return key != null ? levelMap.get(key) : "未知";
}
// getLevel(150) → "中级"（floorKey(150) = 100）
// getLevel(50)  → "初级"（floorKey(50)  = 0）
// getLevel(-1)  → "未知"（floorKey(-1)  = null）
```

## 5.3 subMap 范围视图使用规范

```java
// ✅ 只读使用视图（安全）
NavigableMap<LocalDate, List<Order>> view =
    ordersByDate.subMap(startDate, true, endDate, true);
view.forEach((date, orders) -> process(orders));

// ✅ 批量删除用视图
ordersByDate.subMap(expiredBefore, true, expiredBefore, true).clear();

// ❌ 不要在视图范围外 put（抛 IllegalArgumentException）
view.put(outOfRangeDate, orders);  // ❌

// ⚠️ 需要独立快照时，拷贝一份
NavigableMap<LocalDate, List<Order>> snapshot = new TreeMap<>(view);
```

## 5.4 避免 Comparator 与 equals 不一致

```java
// ⚠️ TreeMap 用 Comparator 判断 key 相等（compare == 0 即认为相同 key）
// 如果 Comparator 与 equals 不一致，会产生反直觉行为

TreeMap<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
map.put("Apple", 1);
map.put("apple", 2);  // ← Comparator 认为 "Apple" == "apple"，覆盖了！
System.out.println(map.size());   // 1，不是 2！
System.out.println(map.get("APPLE"));  // 2

// ✅ 如果需要区分大小写，用默认自然顺序（不传 Comparator）
TreeMap<String, Integer> caseSensitive = new TreeMap<>();
caseSensitive.put("Apple", 1);
caseSensitive.put("apple", 2);
System.out.println(caseSensitive.size());  // 2
```

## 5.5 线程安全

```java
// TreeMap 非线程安全，并发环境使用：
// 方案1：Collections.synchronizedSortedMap（保留有序性和导航方法）
SortedMap<K, V> syncMap = Collections.synchronizedSortedMap(new TreeMap<>());

// 方案2：ConcurrentSkipListMap（无锁并发，有序，替代 TreeMap + 锁）
ConcurrentSkipListMap<K, V> concurrentMap = new ConcurrentSkipListMap<>();
// ConcurrentSkipListMap 是 TreeMap 的并发版本，所有操作 O(log n)，无锁
```

## 5.6 本章总结

**五条核心实践**：
1. **Comparator 用 `Integer.compare`**，不用 `a - b`（溢出风险）
2. **floorKey 区间映射**：区间下限作 key，`floorKey(x)` 找所属区间——标准用法
3. **subMap 视图**：只读遍历或批量删除可用视图；范围外 put 抛异常；需快照则 new TreeMap(view)
4. **Comparator 与 equals 一致**：`compare == 0` 被视为同一 key，大小写不敏感 Comparator 会合并不同大小写的 key
5. **并发场景**：用 `ConcurrentSkipListMap`（优于 `synchronizedSortedMap`）

> **本章对应演示代码**：`RangePriceIndex.java`（区间映射）、`ScheduleManager.java`（时间线 + 冲突检测）

**返回目录**：[README.md](../README.md)
