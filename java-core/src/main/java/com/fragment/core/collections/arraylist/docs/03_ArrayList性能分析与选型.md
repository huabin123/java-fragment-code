# 第三章：ArrayList 性能分析与选型

## 3.1 各操作的时间复杂度

| 操作 | 复杂度 | 说明 |
|------|--------|------|
| `get(index)` | O(1) | 直接数组索引 |
| `add(e)`（末尾） | O(1) 均摊 | 偶发扩容为 O(n) |
| `add(index, e)` | O(n) | 需要移动 index 后所有元素 |
| `remove(index)` | O(n) | 需要移动被删位置后的所有元素 |
| `remove(Object)` | O(n) | 先线性查找，再移动 |
| `contains(o)` | O(n) | 线性扫描 |
| `indexOf(o)` | O(n) | 线性扫描 |
| `size()` | O(1) | 直接返回 size 字段 |

---

## 3.2 ArrayList vs LinkedList 实测数据

`ArrayListPerformanceDemo.java` 的典型测试结果（100,000 个元素，JVM 预热后）：

```
末尾追加（各 100,000 次）：
  ArrayList:  ~8ms
  LinkedList: ~12ms（对象创建开销更大）

头部插入（10,000 次）：
  ArrayList:  ~45ms（每次移动大量元素）
  LinkedList: ~2ms （O(1) 修改指针）

随机访问（100,000 次 get）：
  ArrayList:  ~2ms （O(1) 数组索引）
  LinkedList: 无法测试（100,000次O(n)访问极慢，仅测1,000次约300ms）

顺序遍历（增强 for）：
  ArrayList:  ~3ms （CPU 缓存友好，连续内存）
  LinkedList: ~8ms （指针跳转，缓存不友好）
```

---

## 3.3 缓存局部性：ArrayList 胜出的根本原因

```
ArrayList 内存布局（连续）：
[elem0][elem1][elem2][elem3][elem4]...
  ↑ CPU 一次加载一个缓存行（通常 64 字节），可以缓存约 16 个引用

LinkedList 内存布局（离散）：
[Node0] → [Node1] → [Node2] → [Node3]...
  ↑ 每个 Node 在堆中随机分布，每次指针跳转几乎都是缓存未命中
```

这就是为什么即使是**顺序遍历**，ArrayList 也比 LinkedList 快——不是算法复杂度的差别，而是**CPU 缓存命中率**的差别。

---

## 3.4 选型决策树

```
需要线性表？
  ↓
是否需要线程安全？
  → 是：读多写少 → CopyOnWriteArrayList
        写也很多 → Collections.synchronizedList 或换用并发集合
  → 否：
      ↓
    主要操作是什么？
      随机访问 / 顺序遍历 → ArrayList ✅
      头尾高频插删（队列/栈） → ArrayDeque ✅
      中间高频插删（但实际很罕见）→ LinkedList（慎用，先 benchmark）
      ↓
    元素数量可预估？
      → 是：new ArrayList<>(预估容量)，避免扩容
      → 否：new ArrayList<>()，默认懒初始化
```

---

## 3.5 常见性能陷阱

### 陷阱一：在循环中 remove(index)

```java
// ❌ 每次 remove(0) 都移动所有元素，O(n²) 总体
for (int i = 0; i < list.size(); i++) {
    if (shouldRemove(list.get(i))) {
        list.remove(i);
        i--;  // 还要手动调整索引
    }
}

// ✅ removeIf：内部一次遍历完成，O(n)
list.removeIf(e -> shouldRemove(e));
```

### 陷阱二：contains 在大列表上频繁调用

```java
// ❌ 每次 contains 都 O(n)，总体 O(n²)
List<String> bigList = new ArrayList<>(100000);
for (String item : anotherList) {
    if (!bigList.contains(item)) bigList.add(item);  // 去重
}

// ✅ 改用 HashSet 做存在性检查，O(1)
Set<String> seen = new HashSet<>();
List<String> deduped = new ArrayList<>();
for (String item : anotherList) {
    if (seen.add(item)) deduped.add(item);  // add 返回 false 说明已存在
}
```

### 陷阱三：基本类型装箱开销

```java
// ❌ 存储 100 万个 int，每个都装箱为 Integer，内存约 20MB
List<Integer> list = new ArrayList<>(1_000_000);
for (int i = 0; i < 1_000_000; i++) list.add(i);  // 每次 add 都装箱

// ✅ 若只是数值计算，用 int[] 或 IntStream
int[] arr = IntStream.range(0, 1_000_000).toArray();  // 约 4MB，无装箱
```

---

## 3.6 本章总结

- **O(1) 的操作**：get、末尾 add、size
- **O(n) 的操作**：中间插删、contains、indexOf、remove(Object)
- **胜过 LinkedList**：随机访问、顺序遍历（缓存局部性）
- **不如 LinkedList**：头部高频插入（但实际应该用 ArrayDeque）
- **三大陷阱**：循环内 remove（用 removeIf）、频繁 contains（改 HashSet）、大量基本类型（用数组）

> **本章对应演示代码**：`ArrayListPerformanceDemo.java`（ArrayList vs LinkedList 四项基准测试）

**继续阅读**：[04_ArrayList并发安全.md](./04_ArrayList并发安全.md)
