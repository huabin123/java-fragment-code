# 第二章：ArrayList 核心原理与扩容机制

## 2.1 内部数据结构

```java
// ArrayList 源码（JDK 8 简化版）
public class ArrayList<E> {
    transient Object[] elementData;  // 实际存储数组
    private int size;                // 逻辑大小（已存入的元素个数）

    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    // 无参构造：JDK 8 起延迟初始化，首次 add 时才分配数组
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    // 指定容量构造
    public ArrayList(int initialCapacity) {
        this.elementData = new Object[initialCapacity];
    }
}
```

**size vs capacity**：
- `size`：`list.size()` 返回的值，逻辑上存了多少元素
- `capacity`：`elementData.length`，数组实际分配的长度，始终 ≥ size

---

## 2.2 扩容机制：1.5 倍增长

```java
// add() 触发扩容的完整路径（源码简化）
public boolean add(E e) {
    ensureCapacityInternal(size + 1);  // 检查并扩容
    elementData[size++] = e;
    return true;
}

private void grow(int minCapacity) {
    int oldCapacity = elementData.length;
    int newCapacity = oldCapacity + (oldCapacity >> 1);  // 1.5 倍
    // 如果 1.5 倍还不够（如 addAll 一次加很多），直接用 minCapacity
    if (newCapacity < minCapacity) newCapacity = minCapacity;
    elementData = Arrays.copyOf(elementData, newCapacity);  // 数组拷贝
}
```

**扩容序列**（从默认初始容量 10 开始）：

```
10 → 15 → 22 → 33 → 49 → 73 → 109 → ...
```

`ArrayListInternalsDemo.java → demonstrateExpansion()` 用反射实时观察每次扩容的容量变化。

---

## 2.3 为什么是 1.5 倍而不是 2 倍？

- **2 倍**（如 C++ vector）：扩容更激进，减少扩容次数，但浪费更多内存
- **1.5 倍**：JDK 的选择，在扩容次数（性能）和内存浪费之间取得平衡
- **均摊复杂度**：无论多少次 add，均摊下来每次 add 的时间复杂度是 O(1)

---

## 2.4 JDK 8 的懒初始化优化

```java
// JDK 7：new ArrayList() 直接分配 10 个空间，无论用不用
this.elementData = new Object[10];  // JDK 7

// JDK 8：new ArrayList() 用空数组，首次 add 时才分配 10 个空间
this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;  // JDK 8

// 意义：大量 new ArrayList() 但不使用（如 Spring Bean 初始化中的临时集合）
// JDK 8 节省了这部分无效的内存分配
```

---

## 2.5 trimToSize 与 ensureCapacity

```java
// ArrayListInternalsDemo.java → demonstrateTrimToSize()
ArrayList<Integer> list = new ArrayList<>(100);
for (int i = 0; i < 10; i++) list.add(i);
// size=10, capacity=100，浪费 90 个槽位

list.trimToSize();
// size=10, capacity=10，内存从 400 字节降到 40 字节（Integer 引用 4 字节）
// 适合：初始化完毕后长期只读的大列表

// ArrayListInternalsDemo.java → demonstrateEnsureCapacity()
ArrayList<Integer> list2 = new ArrayList<>();
list2.ensureCapacity(1_000_000);  // 预分配，后续 add 不再触发扩容
// 性能提升：避免约 34 次 Arrays.copyOf 调用（log1.5(1000000) ≈ 34）
```

---

## 2.6 subList 是视图，不是拷贝

```java
// ArrayListBasicDemo.java → demonstrateUtilityMethods()
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
List<String> sub = list.subList(1, 4);  // [B, C, D]

// ⚠️ subList 返回的是原 list 的视图（SubList 内部类）
sub.set(0, "X");  // 修改 sub → 原 list 也变了！
System.out.println(list);  // [A, X, C, D, E]

// ⚠️ 修改原 list 结构（add/remove）后，sub 变为失效状态
list.add("F");
sub.get(0);  // 抛出 ConcurrentModificationException！

// ✅ 需要独立副本时：
List<String> copy = new ArrayList<>(list.subList(1, 4));
```

---

## 2.7 本章总结

- **内部结构**：`Object[] elementData` + `int size`，capacity ≥ size
- **扩容规则**：1.5 倍（`oldCap + (oldCap >> 1)`），触发 `Arrays.copyOf`
- **JDK 8 优化**：懒初始化，new ArrayList() 不立即分配数组
- **trimToSize**：裁剪到 size，节省内存；`ensureCapacity` 预扩容避免多次 copyOf
- **subList 陷阱**：是视图不是拷贝，修改原 list 结构后 subList 失效

> **本章对应演示代码**：`ArrayListInternalsDemo.java`（扩容序列观察、trimToSize、ensureCapacity 性能对比）

**继续阅读**：[03_ArrayList性能分析与选型.md](./03_ArrayList性能分析与选型.md)
