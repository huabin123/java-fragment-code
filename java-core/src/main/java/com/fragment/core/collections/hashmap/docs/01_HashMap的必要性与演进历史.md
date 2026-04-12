# 第一章：HashMap 的必要性与演进历史

## 1.1 核心问题：O(1) 查找是如何实现的？

在系统设计中，「通过某个标识符快速找到对应的数据」是最基础也最高频的需求。用户登录时根据 userId 找 session、缓存系统根据 key 找 value、数据库索引根据列值找行……这些场景有一个共同要求：**查找必须足够快**。

衡量数据结构查找效率，我们先看其他结构的上限：

- **数组按值查找**：必须遍历，O(n)
- **排序数组二分查找**：O(log n)，但要求有序，插入/删除代价高
- **二叉搜索树**：平均 O(log n)，但最坏退化为链表 O(n)
- **HashMap**：平均 **O(1)**

HashMap 实现 O(1) 的核心思想只有一句话：**用 Hash 函数把 key 映射到数组下标，直接寻址，跳过比较**。

```
key: "userId_888"
  → hashCode() → 1234567890
  → hash 扰动  → 1234560010  （消除 hashCode 低位集中的问题）
  → & (n-1)    → index: 42
  → table[42]  ← 直接取值，O(1)
```

这个流程在 `HashMapBasicDemo.java → basicOperations()` 中有完整演示。但要理解为什么它快，需要先知道为什么其他结构做不到。

## 1.2 Hash 冲突：O(1) 的代价与解决

Hash 函数不是完美的映射——不同的 key 可能得到相同的下标，这叫 **Hash 冲突**。

```java
// HashMapCollisionDemo.java → hashCollision()
// 演示了整数 key 0、16、32 在容量 16 的 HashMap 中会落入同一个桶
// hash(0) & 15 = 0，hash(16) & 15 = 0，hash(32) & 15 = 0
map.put(0, "value0");
map.put(16, "value16");
map.put(32, "value32");
```

Java 使用**链地址法**解决冲突：每个数组槽位（桶）维护一个链表，冲突的元素追加到链表尾部。但链表长了之后查找退化为 O(n)。

JDK 1.8 引入了一个关键优化：**链表超过 8 个节点时，自动升级为红黑树**，查找从 O(n) 恢复到 O(log n)。

```java
// HashMapCollisionDemo.java → performanceImpact()
// BadHashKey 的 hashCode() 永远返回 1，所有 key 堆入同一个桶
// 演示了冲突对性能的实际影响
```

> 关键结论：只要 `hashCode()` 分布均匀，冲突就少；冲突少，链表就短；链表短，性能就接近 O(1)。

---

## 1.3 JDK 1.7 → 1.8 的演进：为什么要改？

理解演进历史的关键不是记住改了什么，而是理解**原来的设计为什么会出问题**。

### 头插法 → 尾插法：修复多线程死循环

JDK 1.7 扩容时，链表节点的重新链接使用**头插法**（新节点插到链表头部），导致扩容后链表顺序与原来相反。

在多线程并发扩容时，两个线程都在对同一个桶的链表做头插法，最终可能形成**循环引用**：节点 A 的 next 指向节点 B，同时节点 B 的 next 又指向节点 A。之后调用 `get()` 遍历这个桶时，进入死循环，CPU 飙满 100%。

JDK 1.8 改为**尾插法**，扩容时保持链表原有顺序，消除了循环引用的根因（但 HashMap 本身仍然不是线程安全的，并发问题见第四章）。

### Hash 扰动函数：减少低容量时的碰撞

HashMap 定位桶的公式是 `index = hash & (n-1)`，其中 n 是数组容量（通常在 16~256 之间）。这意味着参与计算的只有 hash 值的**低位**，高位完全被忽略。

如果两个对象的 `hashCode()` 只有高位不同、低位相同，它们就会进入同一个桶——即使 hash 值完全不同。

JDK 1.8 的扰动函数通过一次异或操作，把高 16 位的信息混入低 16 位：

```java
// JDK 1.8 源码
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
// h >>> 16：右移16位，把高16位搬到低16位
// ^ 异或：让高位信息也参与最终的桶定位
```

### 扩容位置判断：O(n) → O(1)

JDK 1.7 扩容时，每个元素都要重新执行 `hash & (newCapacity - 1)` 来确定新位置。

JDK 1.8 利用了容量是 2 的幂次方的特性，发现了一个规律：**扩容后每个元素的新位置，要么等于旧位置，要么等于旧位置 + 旧容量**，只取决于 hash 值的某一位是 0 还是 1：

```
旧容量 16 = 0001 0000（二进制）
旧掩码    = 0000 1111（n-1）
新掩码    = 0001 1111（2n-1）

如果 hash & 旧容量 == 0：新index = 旧index（高位是0，与旧结果相同）
如果 hash & 旧容量 != 0：新index = 旧index + 旧容量（高位是1，多了一个旧容量的偏移）
```

这一判断是 O(1) 的位运算，避免了重新计算 hash。

---

## 1.4 null key 的特殊处理

HashMap 是 Java 中少数**允许 null key** 的 Map 实现。

```java
// HashMapBasicDemo.java → nullKeyAndValue()
map.put(null, "null-key-value");
String value = map.get(null);  // 正常工作
```

null key 的 hash 值被硬编码为 0，始终存放在 `table[0]` 位置。`HashTable` 和 `ConcurrentHashMap` 不支持 null key，因为它们需要用 null 来表达"不存在"的语义，而 HashMap 通过 `containsKey()` 区分"key 不存在"和"key 存在但 value 为 null"：

```java
// HashMapBasicDemo.java → nullKeyAndValue()
// get 返回 null 有两种情况，需要用 containsKey 区分
if (map.containsKey(key)) {
    System.out.println(key + "存在，value为: " + map.get(key));  // value 可能是 null
} else {
    System.out.println(key + "不存在");
}
```

---

## 1.5 各 Map 实现的选型逻辑

选 Map 时，先问三个问题：

```
1. 需要线程安全？
   是 → ConcurrentHashMap（不是 Hashtable，已过时）
   否 → 继续

2. 需要按 key 排序？
   是 → TreeMap（红黑树，O(log n) 操作，key 必须 Comparable 或提供 Comparator）
   否 → 继续

3. 需要保持插入顺序 或 实现 LRU？
   是 → LinkedHashMap
   否 → HashMap（默认选择，性能最优）
```

| 实现 | 结构 | 有序 | 线程安全 | null key | get/put |
|------|------|------|---------|---------|---------|
| HashMap | 数组+链表+红黑树 | 无序 | 否 | ✅ | O(1) |
| LinkedHashMap | HashMap+双向链表 | 插入/访问顺序 | 否 | ✅ | O(1) |
| TreeMap | 红黑树 | key 升序 | 否 | ❌ | O(log n) |
| Hashtable | 数组+链表 | 无序 | 是（粗锁） | ❌ | O(1) |
| ConcurrentHashMap | 数组+链表+红黑树 | 无序 | 是（细锁/CAS） | ❌ | O(1) |

**注意**：`HashMapThreadSafetyDemo.java` 中有四种线程安全方案的实际性能对比，结论是 `ConcurrentHashMap` 在并发场景下性能接近 `HashMap`，远优于 `Hashtable` 和 `synchronizedMap`。

---

## 1.6 本章总结

- **O(1) 查找的代价**：Hash 冲突是无法完全消除的，JDK 1.8 通过链表→红黑树兜底，保证最坏情况 O(log n)
- **扰动函数**：一次 `^ (h >>> 16)` 让高位参与寻址，减少低容量时的碰撞
- **头插→尾插**：修复了多线程扩容时的死循环根因
- **扩容位置优化**：`hash & oldCap` 的 0/1 判断，O(1) 确定新位置，无需重算 hash
- **null key**：固定存 `table[0]`，是 HashMap 独有的能力

> **本章对应演示代码**：`HashMapBasicDemo.java`（基础操作与 null key）、`HashMapCollisionDemo.java`（冲突产生与性能影响）

**继续阅读**：[02_HashMap核心原理与数据结构.md](./02_HashMap核心原理与数据结构.md)
