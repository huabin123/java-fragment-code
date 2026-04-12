# 第二章：Set 的工作原理与 hashCode 陷阱

## 2.1 HashSet 的去重原理

HashSet 判断两个元素是否"相同"，分两步：

```
1. hashCode() 相同？
   → 否：直接认为是不同元素（不同桶），放入
   → 是：继续第二步
2. equals() 返回 true？
   → 是：认为是同一元素，不放入（去重）
   → 否：哈希冲突，放入同一桶的链表中
```

```java
// SetInternalsDemo.java 演示了三种情况

// ❌ 只重写 equals，不重写 hashCode
Set<BadPoint> badSet = new HashSet<>();
badSet.add(new BadPoint(1, 1));
badSet.add(new BadPoint(1, 1));  // hashCode 不同（使用内存地址）→ 认为不同元素
System.out.println(badSet.size());  // 2！没有去重

// ✅ 正确重写 hashCode 和 equals
Set<GoodPoint> goodSet = new HashSet<>();
goodSet.add(new GoodPoint(1, 1));
goodSet.add(new GoodPoint(1, 1));  // hashCode 相同 且 equals 为 true → 去重
System.out.println(goodSet.size());  // 1
```

---

## 2.2 hashCode 与 equals 的约定

Java 规范要求：
- **如果 `a.equals(b)` 为 true，则 `a.hashCode() == b.hashCode()` 必须为 true**
- 反之不必须（哈希冲突是允许的）

违反此约定会导致 Set/Map 行为异常：

```java
// ❌ 违反约定：equals 相等但 hashCode 不同
class BrokenPoint {
    int x, y;
    @Override
    public boolean equals(Object o) { ... x == p.x && y == p.y }
    // hashCode 使用默认（内存地址），每个实例不同
}
// 结果：HashSet 无法去重，HashMap 无法通过等值 key 查找
```

---

## 2.3 可变对象作为 Set 元素的陷阱

`SetInternalsDemo.java → demonstrateMutableKeyPitfall()` 演示了这个致命陷阱：

```java
Set<GoodPoint> set = new HashSet<>();
GoodPoint p = new GoodPoint(1, 1);
set.add(p);                          // hashCode = hash(1,1)，放入对应桶

p.x = 99;                            // ⚠️ 修改了参与 hashCode 计算的字段！
// 现在 p.hashCode() = hash(99,1)，指向不同的桶

set.contains(p);                     // false！在新 hashCode 对应的桶里找不到
set.remove(p);                       // false！无法删除
set.size();                          // 还是 1，元素悄悄"丢失"了
```

**根本原因**：对象放入 Set 后，其 `hashCode` 决定了存储位置。修改字段改变了 `hashCode`，但 Set 不会自动重新排列。

**解决方案**：
- Set 的元素使用**不可变对象**（`String`、`Integer`、`LocalDate` 等）
- 自定义类要么不可变，要么 `hashCode` 不基于可变字段

---

## 2.4 TreeSet 的比较规则

TreeSet 用 `Comparator.compare()` 或 `Comparable.compareTo()` 判断元素是否"相同"：

```java
// compare 返回 0 → 认为是同一元素（不放入）
// 即使 equals 返回 false，compare 为 0 也会去重！

TreeSet<String> caseInsensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
caseInsensitive.add("Apple");
caseInsensitive.add("apple");  // compare("Apple","apple") == 0 → 被去重！
System.out.println(caseInsensitive.size());  // 1！

// SetInternalsDemo.java → demonstrateTreeSetComparator()
TreeSet<String> byLength = new TreeSet<>(
    Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())
);
// 长度相同且字母顺序相同才认为是同一元素
```

---

## 2.5 本章总结

- **HashSet 去重两步**：先比 `hashCode`，再比 `equals`；缺一不可
- **必须同时重写**：重写 `equals` 就必须重写 `hashCode`，违反约定则 Set 行为异常
- **可变对象陷阱**：修改放入 Set 的对象中参与 `hashCode` 的字段，元素会"失联"
- **TreeSet 去重**：基于 `Comparator.compare() == 0`，与 `equals` 无关；Comparator 不一致时要特别注意

> **本章对应演示代码**：`SetInternalsDemo.java`（hashCode/equals 三种情况、TreeSet 排序、可变对象陷阱）

**继续阅读**：[03_Set的集合运算与实战应用.md](./03_Set的集合运算与实战应用.md)
