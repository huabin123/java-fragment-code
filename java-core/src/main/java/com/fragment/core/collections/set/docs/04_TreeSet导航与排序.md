# 第四章：TreeSet 导航与排序

## 4.1 TreeSet 的导航方法

TreeSet 实现了 `NavigableSet` 接口，提供与 TreeMap 对应的导航能力：

`SetInternalsDemo.java → demonstrateTreeSetComparator()` 演示了所有导航方法：

```java
TreeSet<Integer> nums = new TreeSet<>(Arrays.asList(1, 3, 5, 7, 9));

// 单个元素导航
nums.floor(4);    // 3（≤ 4 的最大元素）
nums.ceiling(4);  // 5（≥ 4 的最小元素）
nums.lower(5);    // 3（< 5 的最大元素，严格小于）
nums.higher(5);   // 7（> 5 的最小元素，严格大于）
nums.first();     // 1（最小元素）
nums.last();      // 9（最大元素）

// 取出并删除
nums.pollFirst();  // 返回 1 并删除
nums.pollLast();   // 返回 9 并删除
```

---

## 4.2 三种范围视图

```java
TreeSet<Integer> nums = new TreeSet<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

// headSet(toElement, inclusive)：小于等于 toElement 的子集
nums.headSet(5);         // [1, 2, 3, 4]（不含 5，默认 exclusive）
nums.headSet(5, true);   // [1, 2, 3, 4, 5]（含 5）

// tailSet(fromElement, inclusive)：大于等于 fromElement 的子集
nums.tailSet(8);         // [8, 9, 10]（含 8，默认 inclusive）
nums.tailSet(8, false);  // [9, 10]（不含 8）

// subSet(from, fromInclusive, to, toInclusive)
nums.subSet(3, 7);                    // [3, 4, 5, 6]（含 3，不含 7）
nums.subSet(3, true, 7, true);        // [3, 4, 5, 6, 7]
nums.subSet(3, false, 7, false);      // [4, 5, 6]

// ⚠️ 范围视图是原 Set 的视图，修改会影响原 Set
nums.subSet(1, 4).clear();  // 删除 1,2,3
System.out.println(nums);   // [4, 5, 6, 7, 8, 9, 10]
```

---

## 4.3 自定义排序的常用场景

```java
// 场景1：忽略大小写的有序 Set
TreeSet<String> caseInsensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
caseInsensitive.addAll(Arrays.asList("Banana", "apple", "Cherry", "date"));
System.out.println(caseInsensitive);  // [apple, Banana, Cherry, date]
// ⚠️ CASE_INSENSITIVE_ORDER 认为 "Apple" == "apple"，只保留一个

// 场景2：按长度排序的有序 Set（长度相同按字母）
TreeSet<String> byLength = new TreeSet<>(
    Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())
);
byLength.addAll(Arrays.asList("banana", "fig", "cherry", "apple", "kiwi"));
System.out.println(byLength);  // [fig, kiwi, apple, banana, cherry]

// 场景3：倒序 Set
TreeSet<Integer> descSet = new TreeSet<>(Comparator.reverseOrder());
descSet.addAll(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6));
System.out.println(descSet);  // [9, 6, 5, 4, 3, 2, 1]
```

---

## 4.4 TreeSet vs TreeMap：几乎等价

```java
// TreeSet 内部：
// private transient NavigableMap<E, Object> m;
// private static final Object PRESENT = new Object();

// add(e) = m.put(e, PRESENT)
// contains(e) = m.containsKey(e)
// remove(e) = m.remove(e)

// 这意味着 TreeSet 的所有性能特征与 TreeMap 完全相同：
// add/remove/contains：O(log n)
// 范围操作（subSet/headSet/tailSet）：O(log n + k)
// 遍历：O(n)，按排序顺序
```

---

## 4.5 本章总结

- **导航方法**：floor（≤）、ceiling（≥）、lower（<）、higher（>）、first、last，全部 O(log n)
- **范围视图**：headSet、tailSet、subSet，支持 inclusive/exclusive 控制边界；视图修改传播到原 Set
- **自定义排序**：通过 Comparator，注意 compare==0 时认为是同一元素（影响去重）
- **底层**：TreeMap，性能特征完全相同，所有操作 O(log n)

> **本章对应演示代码**：`SetInternalsDemo.java`（TreeSet 导航方法）、`TagSystem.java`（TreeSet 排序输出）

**继续阅读**：[05_Set最佳实践.md](./05_Set最佳实践.md)
