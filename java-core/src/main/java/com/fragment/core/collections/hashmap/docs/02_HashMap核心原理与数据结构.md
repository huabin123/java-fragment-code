# 第二章：HashMap 核心原理与数据结构

## 2.1 为什么是"数组 + 链表 + 红黑树"这三种结构的组合？

理解这个问题，先从每种结构的角色分工出发：

- **数组**：提供 O(1) 随机寻址能力。通过 `hash & (n-1)` 把 key 的 hash 值映射到下标，直接访问对应位置。
- **链表**：处理 Hash 冲突。多个 key 映射到同一下标时，以链表形式串联存放在同一个"桶"里。
- **红黑树**：当某个桶的链表过长（≥8）时，升级为红黑树，把最坏查找从 O(n) 降到 O(log n)。

三者各司其职，没有一种可以单独胜任：数组本身无法处理冲突，链表查找太慢，红黑树在冲突少时又显得过于复杂。**这个三层兜底的设计，是在空间效率、时间效率和实现复杂度之间取得的最优折中**。

```
table 数组（每格是一个"桶"）：
┌─────┬─────┬─────┬─────┐
│  0  │  1  │  2  │  3  │ ...
└──┬──┴─────┴──┬──┴─────┘
   │           │
   ↓           ↓ （冲突少：链表）
  Node        Node → Node → null
   ↓ （冲突多：红黑树）
 TreeNode
  ├── TreeNode
  └── TreeNode
```

---

## 2.2 核心字段的设计意图

```java
public class HashMap<K,V> {
    transient Node<K,V>[] table;      // 桶数组，懒初始化（第一次 put 时才分配）
    transient int size;               // 当前元素数量
    transient int modCount;           // 结构性修改次数，支持 fail-fast 迭代器
    int threshold;                    // 扩容阈值 = capacity × loadFactor
    final float loadFactor;           // 负载因子，默认 0.75

    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;  // 16，位运算比字面量 16 更直观地说明"必须是 2 的幂"
    static final int TREEIFY_THRESHOLD = 8;               // 链表 → 红黑树
    static final int UNTREEIFY_THRESHOLD = 6;             // 红黑树 → 链表（与 8 有间隔，防止频繁互转）
    static final int MIN_TREEIFY_CAPACITY = 64;           // 转树的前提：数组容量至少 64
}
```

**`table` 为何用 `transient`？**

`table` 数组的布局（每个元素在哪个桶）依赖于当前容量 `n`。序列化后反序列化时 `n` 可能不同（不同 JVM 版本、不同负载因子），导致元素落入错误的桶。HashMap 因此通过 `writeObject`/`readObject` 自定义序列化：只写出所有 key-value 对，反序列化时重新 `put` 每个元素，让它们按新容量重新落桶。

**`modCount` 的作用：fail-fast**

```java
// HashMapBasicDemo.java → iterationMethods() 中的 Iterator 遍历
var iterator = map.entrySet().iterator();
while (iterator.hasNext()) {
    Map.Entry<String, Integer> entry = iterator.next();
    // 如果此时另一个线程 put/remove，modCount 改变
    // iterator.next() 会检测到 modCount 不一致，立刻抛出 ConcurrentModificationException
}
```

---

## 2.3 Node 与 TreeNode 的内存布局

### 普通链表节点 Node

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;    // 缓存 hash 值，避免 get/resize 时重复调用 hashCode()
    final K key;       // key 一旦存入不可变（final），防止修改 key 后找不到节点
    V value;
    Node<K,V> next;    // 单向链表指针
}
```

**为什么 `key` 是 `final`？**  
如果允许修改 key，修改后 hash 值变化，但节点仍然在旧 hash 对应的桶里，导致 `get` 找不到它。`final` 强制约束，保证 key 的 hash 与存放位置始终一致。

**为什么缓存 `hash`？**  
`resize()` 时需要遍历所有节点重新分桶，如果每次都调 `key.hashCode()`，对于复杂对象（如长字符串）代价很高。缓存后直接读字段，O(1)。

### 红黑树节点 TreeNode

```java
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
    TreeNode<K,V> parent;  // 父节点
    TreeNode<K,V> left;
    TreeNode<K,V> right;
    TreeNode<K,V> prev;    // 退化回链表时恢复顺序用的双向指针
    boolean red;
}
```

`TreeNode` 继承自 `LinkedHashMap.Entry`（它又继承自 `Node`），所以 TreeNode 同时拥有链表指针和树指针，在树化/退树时可以直接原地转换，不需要重建节点。

---

## 2.4 负载因子 0.75：数学与工程的折中

**负载因子 = size / capacity**，控制何时扩容。

| 负载因子 | 平均链表长度 | 空间利用率 | 扩容频率 |
|---------|-----------|-----------|---------|
| 0.5 | 短 | 50%（浪费） | 频繁 |
| **0.75** | **中** | **75%** | **适中** |
| 1.0 | 长（冲突多） | 100% | 少 |

0.75 的理论依据来自**泊松分布**：在负载因子为 0.75 时，单个桶中出现 k 个元素的概率约为 `e^(-0.5) * 0.5^k / k!`。链表长度达到 8 的概率约为 0.00000006——**阈值 8 就是从这里来的，不是拍脑袋定的**。

```java
// HashMapResizeDemo.java → resizeTrigger() 演示了扩容触发时机
// 初始容量 4，threshold = 4 × 0.75 = 3
// 放入第 4 个元素时触发扩容（4 > 3）
Map<Integer, String> map = new HashMap<>(4);
```

---

## 2.5 为什么容量必须是 2 的幂次方？

这是整个 HashMap 设计的基础约束，它支撑了两个关键优化。

### 优化一：位运算取代取模

定位桶的公式：`index = hash & (capacity - 1)`

只有当 `capacity` 是 2 的幂时，`capacity - 1` 的二进制全为 1（如 16-1=15=`0b01111`），`&` 运算的效果才等价于 `% capacity`。位运算比取模快约 5 倍。

### 优化二：扩容时 O(1) 确定新位置

容量从 `n` 扩容到 `2n` 时，新的寻址公式多了一位：`hash & (2n-1)`。

```
旧掩码 (n=16):  0000 1111
新掩码 (n=32):  0001 1111
                    ↑ 新增这一位
```

新增的这一位对应 `hash` 的第 5 位（从右数）：
- 若第 5 位为 0：新 index = 旧 index（不移动）
- 若第 5 位为 1：新 index = 旧 index + 旧容量（右移 n 位）

判断方法：`(hash & oldCap) == 0` → 不移动，否则移动。

```java
// JDK 1.8 resize() 源码核心逻辑
if ((e.hash & oldCap) == 0) {
    // 链接到低位链表（位置不变）
    if (loTail == null) loHead = e;
    else loTail.next = e;
    loTail = e;
} else {
    // 链接到高位链表（位置 +oldCap）
    if (hiTail == null) hiHead = e;
    else hiTail.next = e;
    hiTail = e;
}
```

`HashMapResizeDemo.java` 中用反射读取 `table` 字段，直观展示了扩容前后容量的变化。

### tableSizeFor：任意初始容量→最近的 2 的幂

```java
static final int tableSizeFor(int cap) {
    int n = cap - 1;      // 先减 1，防止 cap 本身是 2 的幂时翻倍
    n |= n >>> 1;         // 把最高位的 1 "蔓延" 到右侧每一位
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
// tableSizeFor(10) → 16，tableSizeFor(17) → 32
```

原理：5 次右移或运算后，把最高位的 1 下方的所有位都置为 1（如 `0b1001` → `0b1111`），再加 1 就得到下一个 2 的幂。

---

## 2.6 链表 → 红黑树的双重条件

树化需要同时满足：
1. **链表长度 ≥ 8**
2. **数组容量 ≥ 64**

条件 2 常被忽视。原因：数组容量小时，冲突多是因为桶数太少，扩容可以解决问题，没必要树化（树化有内存开销，每个 TreeNode 约 48 字节，比 Node 的 32 字节多 50%）。只有数组够大（已经扩容过）、但某个桶的链表仍然很长，才说明 key 的 hash 分布本身有问题，这时才值得树化。

```java
final void treeifyBin(Node<K,V>[] tab, int hash) {
    int n, index;
    if ((n = tab.length) < MIN_TREEIFY_CAPACITY)  // 容量 < 64？优先扩容
        resize();
    else if (...)
        // 真正树化
}
```

这对应 `HashMapCollisionDemo.java → performanceImpact()` 中 `BadHashKey` 的场景：所有 key 的 hashCode 都是 1，一个桶积累了大量节点，JDK 1.8 会将其转为红黑树，保持 O(log n) 性能。

---

## 2.7 本章总结

- **三层结构**：数组寻址 + 链表兜冲突 + 红黑树优化长链表，各层有明确的职责边界
- **`key` 设计为 `final`**：保证 hash 值与存储位置的一致性
- **负载因子 0.75**：来自泊松分布，平衡空间与时间
- **容量必须是 2 的幂**：支撑位运算取模 + 扩容 O(1) 定位两个核心优化
- **树化双重条件**：链表长度 ≥ 8 且容量 ≥ 64，避免在桶数不足时浪费内存树化

> **本章对应演示代码**：`HashMapCollisionDemo.java`（冲突与树化性能）、`HashMapResizeDemo.java`（扩容触发与容量变化）

**继续阅读**：[03_HashMap源码深度剖析.md](./03_HashMap源码深度剖析.md)
