# 第二章：LinkedHashMap 核心原理与数据结构

## 2.1 继承关系的设计意图

```
HashMap<K,V>
    └── LinkedHashMap<K,V>
```

LinkedHashMap 选择**继承 HashMap**而不是组合，是一个有深意的设计决定：

1. **复用所有 HashMap 逻辑**：hash 计算、桶定位、扩容、红黑树转化——所有 O(1) 的核心逻辑一行不改
2. **只增加顺序维护开销**：在节点上加两个指针（`before`/`after`），在 HashMap 预留的钩子方法里维护链表
3. **零侵入扩展**：HashMap 中有 `afterNodeAccess`、`afterNodeInsertion`、`afterNodeRemoval` 三个空方法，专门等着 LinkedHashMap 来覆盖

这是**模板方法模式**的经典应用：父类定义算法骨架，子类通过覆盖钩子实现差异化行为。

---

## 2.2 Entry 节点：在 Node 基础上增加双向指针

```java
// HashMap.Node：单向链表节点（用于桶内链表）
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;   // 桶内链表：下一个节点
}

// LinkedHashMap.Entry：继承 Node，增加双向链表指针
static class Entry<K,V> extends HashMap.Node<K,V> {
    Entry<K,V> before;  // 全局顺序链表：前一个节点
    Entry<K,V> after;   // 全局顺序链表：后一个节点

    Entry(int hash, K key, V value, Node<K,V> next) {
        super(hash, key, value, next);
    }
}
```

每个 Entry 节点同时参与两个数据结构：
- **HashMap 桶内链表**：通过 `next` 指针串联（处理 hash 冲突）
- **LinkedHashMap 全局双向链表**：通过 `before`/`after` 指针串联（维护遍历顺序）

```
LinkedHashMap 内存结构：

table 数组（桶）:            全局双向链表（顺序）:
[0] → Entry(A)              head → [A] ↔ [B] ↔ [C] ↔ [D] → tail
[1] → Entry(B) → Entry(D)
[2] → Entry(C)

Entry(A): hash=0, next=null, before=head(dummy), after=Entry(B)
Entry(B): hash=1, next=Entry(D), before=Entry(A), after=Entry(C)
Entry(D): hash=1, next=null, before=Entry(C), after=Entry(B)↑（被链表中的位置覆盖）
```

---

## 2.3 LinkedHashMap 的核心字段

```java
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {

    // 双向链表的头节点（最老的元素，或最久未访问的元素）
    transient LinkedHashMap.Entry<K,V> head;

    // 双向链表的尾节点（最新插入的，或最近访问的元素）
    transient LinkedHashMap.Entry<K,V> tail;

    // 决定顺序维护模式：
    // false（默认）：插入顺序（insertion-order）
    // true：访问顺序（access-order，用于 LRU）
    final boolean accessOrder;
}
```

`head` 始终指向最老/最久未访问的节点，`tail` 始终指向最新/最近访问的节点。LRU 驱逐时，删除 `head` 对应的节点（`removeEldestEntry` 返回的就是 `head` 指向的节点）。

---

## 2.4 三个钩子方法的具体实现

### afterNodeAccess：访问后移到链表尾部

```java
void afterNodeAccess(Node<K,V> e) {
    LinkedHashMap.Entry<K,V> last;
    // 只有 accessOrder=true 且 e 不是尾节点时才需要移动
    if (accessOrder && (last = tail) != e) {
        LinkedHashMap.Entry<K,V> p = (LinkedHashMap.Entry<K,V>)e;
        LinkedHashMap.Entry<K,V> b = p.before, a = p.after;

        // 从链表中摘出 p
        p.after = null;
        if (b == null) head = a; else b.after = a;
        if (a != null) a.before = b; else last = b;

        // 追加到链表尾部
        if (last == null) head = p;
        else { p.before = last; last.after = p; }
        tail = p;
        ++modCount;
    }
}
```

这个方法在 `HashMap.get()` 结束后被调用（若 `accessOrder=true`）。正是这个机制使得 `get("key1")` 后，`key1` 的节点自动移到链表末尾，成为"最近访问"。

`LinkedHashMapBasicDemo.java → accessOrderMode()` 演示了这个效果：初始顺序是 A-B-C，`get("A")` 后变为 B-C-A，`get("B")` 后变为 C-A-B。

### afterNodeInsertion：插入后检查是否驱逐最老节点

```java
void afterNodeInsertion(boolean evict) {
    LinkedHashMap.Entry<K,V> first;
    // 条件：evict=true（非初始化状态）且 head 不为空且 removeEldestEntry 返回 true
    if (evict && (first = head) != null && removeEldestEntry(first)) {
        K key = first.key;
        removeNode(hash(key), key, null, false, true);  // 删除链表头部（最老的节点）
    }
}

// 子类可覆盖此方法决定是否驱逐
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return false;  // 默认不驱逐，LinkedHashMap 本身容量无限制
}
```

这就是实现 LRU 的核心机制：覆盖 `removeEldestEntry`，返回 `size() > maxCapacity`，即可让 LinkedHashMap 在每次 `put` 后自动驱逐最老的节点。

### afterNodeRemoval：删除后从双向链表中摘出

```java
void afterNodeRemoval(Node<K,V> e) {
    LinkedHashMap.Entry<K,V> p = (LinkedHashMap.Entry<K,V>)e;
    LinkedHashMap.Entry<K,V> b = p.before, a = p.after;
    p.before = p.after = null;  // help GC
    if (b == null) head = a; else b.after = a;
    if (a == null) tail = b; else a.before = b;
}
```

---

## 2.5 插入顺序的实现细节

在插入顺序模式（`accessOrder=false`）下，**`get` 操作不触发 `afterNodeAccess`**（因为 `accessOrder=false`）。只有 `put` 新 key 时才会把新节点追加到链表尾部。

**更新已有 key 不改变顺序**：`put("A", newValue)` 找到已存在的节点后，调用 `afterNodeAccess(e)`，但 `accessOrder=false`，所以 `afterNodeAccess` 直接返回，顺序不变。

---

## 2.6 内存开销

每个 `LinkedHashMap.Entry` 比 `HashMap.Node` 多两个引用（`before`/`after`），各占 4 字节（压缩指针）：

- `HashMap.Node`：约 32 字节
- `LinkedHashMap.Entry`：约 40 字节（多 8 字节）

对于 100 万条记录，LinkedHashMap 比 HashMap 多约 8MB 内存。这是维护顺序的代价。

---

## 2.7 本章总结

- **Entry 双重身份**：同时是 HashMap 桶内链表节点（`next`）和全局顺序链表节点（`before`/`after`）
- **`head`/`tail`**：始终指向最老/最新节点，是 LRU 驱逐和顺序遍历的入口
- **`accessOrder`**：false → 插入顺序；true → 访问顺序（每次 get/put 触发 `afterNodeAccess` 移位）
- **三个钩子**：`afterNodeAccess`（移到尾部）、`afterNodeInsertion`（检查驱逐）、`afterNodeRemoval`（从链表摘出）
- **内存代价**：每节点额外 8 字节（两个指针）

> **本章对应演示代码**：`LinkedHashMapBasicDemo.java`（两种模式的顺序变化）、`LinkedHashMapLRUDemo.java`（accessOrder=true 的 LRU 机制）

**继续阅读**：[03_LinkedHashMap源码深度剖析.md](./03_LinkedHashMap源码深度剖析.md)
