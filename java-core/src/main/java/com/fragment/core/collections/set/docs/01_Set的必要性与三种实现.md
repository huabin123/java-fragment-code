# 第一章：Set 的必要性与三种实现

## 1.1 Set 解决的核心问题

List 可以存储重复元素，在很多场景下这是问题：

```java
// 用户添加商品到收藏夹，同一商品可能多次添加
List<String> favorites = new ArrayList<>();
favorites.add("iPhone");
favorites.add("MacBook");
favorites.add("iPhone");  // 重复！
System.out.println(favorites.size());  // 3，需要手动判断去重

// ❌ 用 List 去重：O(n) 的 contains 检查
if (!favorites.contains(item)) favorites.add(item);  // O(n)

// ✅ 用 Set：自动去重，O(1) 判断
Set<String> favoriteSet = new HashSet<>();
favoriteSet.add("iPhone");
favoriteSet.add("MacBook");
favoriteSet.add("iPhone");  // 自动忽略
System.out.println(favoriteSet.size());  // 2
```

**Set 的两大核心语义**：
1. **自动去重**：同一元素只保存一份
2. **O(1) 存在性判断**：`contains` 是 O(1)，而 List 是 O(n)

---

## 1.2 三种实现的本质区别

```
HashSet       → 底层是 HashMap（key=元素, value=PRESENT占位对象）
LinkedHashSet → 底层是 LinkedHashMap（在 HashSet 基础上加双向链表维护顺序）
TreeSet       → 底层是 TreeMap（key=元素, 维护排序顺序）
```

`SetBasicDemo.java → demonstrateThreeTypes()` 用同一组数据演示三者的差异：

```java
String[] data = {"banana", "apple", "cherry", "apple", "date", "banana"};

HashSet:       {cherry, banana, date, apple}    // 无序，哈希分布
LinkedHashSet: [banana, apple, cherry, date]    // 插入顺序（apple/banana 第一次出现的顺序）
TreeSet:       [apple, banana, cherry, date]    // 字母升序
```

---

## 1.3 选型决策

| 需求 | 选择 |
|------|------|
| 只需去重 + 快速查找 | `HashSet` |
| 去重 + 保持插入顺序 | `LinkedHashSet` |
| 去重 + 排序 + 范围查询（floor/ceiling/subSet）| `TreeSet` |
| 并发安全 | `ConcurrentHashMap.newKeySet()` 或 `Collections.synchronizedSet()` |

---

## 1.4 性能对比

`SetBasicDemo.java → demonstrateContainsPerformance()` 实测（100 万个元素，10 万次 contains）：

```
List.contains  100,000 次: ~2500ms  （O(n) 线性扫描）
Set.contains   100,000 次: ~5ms     （O(1) 哈希查找）
```

**结论**：需要频繁判断元素是否存在时，Set 比 List 快 **500 倍以上**。

---

## 1.5 本章总结

- **Set 的核心价值**：自动去重 + O(1) 存在性判断
- **HashSet**：最常用，无序，O(1) 操作
- **LinkedHashSet**：保持插入顺序，比 HashSet 略慢（维护链表）
- **TreeSet**：排序，O(log n) 操作，支持范围查询
- **性能差异**：contains 比 List 快数百倍，这是使用 Set 的最主要理由

> **本章对应演示代码**：`SetBasicDemo.java`（三种 Set 对比、集合运算、contains 性能）

**继续阅读**：[02_Set的工作原理与hashCode陷阱.md](./02_Set的工作原理与hashCode陷阱.md)
