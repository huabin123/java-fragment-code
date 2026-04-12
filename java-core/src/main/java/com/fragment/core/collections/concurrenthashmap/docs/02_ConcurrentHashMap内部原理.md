# 第二章：ConcurrentHashMap 内部原理

## 2.1 JDK 8 的数据结构

ConcurrentHashMap 的底层结构与 HashMap 相同：**数组 + 链表 + 红黑树**。

```
table[0]  → null
table[1]  → Node(k1,v1) → Node(k2,v2)    ← 链表（桶内冲突元素）
table[2]  → TreeBin(红黑树根)              ← 链表长度≥8时转为红黑树
table[3]  → null
...
```

关键字段：
```java
transient volatile Node<K,V>[] table;   // volatile 保证可见性
private transient volatile int sizeCtl; // 控制初始化和扩容的状态标志
```

---

## 2.2 读操作：完全无锁

```java
// get() 源码（简化）
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
    int h = spread(key.hashCode());

    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) {  // tabAt 用 Unsafe.getObjectVolatile 读取，保证可见性

        if ((eh = e.hash) == h) {
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val;  // 直接返回，无锁
        }
        // ... 链表/树遍历，全程无锁
    }
    return null;
}
```

Node 的 val 和 next 都是 `volatile`，保证读到最新值：
```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    volatile V val;         // volatile！
    volatile Node<K,V> next; // volatile！
}
```

---

## 2.3 写操作：CAS + 锁单个桶

```java
// put() 核心逻辑（简化）
final V putVal(K key, V value, boolean onlyIfAbsent) {
    for (Node<K,V>[] tab = table;;) {
        Node<K,V> f; int n, i, fh;

        if (tab == null || (n = tab.length) == 0)
            tab = initTable();  // 初始化（CAS 保证只有一个线程初始化）

        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            // 桶为空：CAS 直接写入，无锁
            if (casTabAt(tab, i, null, new Node<>(hash, key, value, null)))
                break;  // 成功则结束
        }

        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);  // 协助扩容

        else {
            // 桶非空：锁住桶的第一个节点
            synchronized (f) {
                // ... 在链表/树上插入或更新
            }
        }
    }
    addCount(1L, binCount);  // 维护 size（LongAdder 机制）
    return null;
}
```

**加锁粒度**：只锁目标桶的头节点，不同桶的写操作完全并行。

---

## 2.4 size() 的实现：分布式计数

ConcurrentHashMap 不用一个单一的 `size` 字段（高并发下 CAS 竞争严重），而是借鉴 `LongAdder` 思想：

```java
// 基础计数 baseCount + CounterCell[] 数组
// 低竞争时直接 CAS 更新 baseCount
// 高竞争时每个线程更新自己的 CounterCell
// size() = baseCount + sum(counterCells)

public int size() {
    long n = sumCount();
    return (n < 0L) ? 0 : (n > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)n;
}

// mappingCount() 返回 long，更精确（size() 对超 int 范围会截断）
public long mappingCount() {
    return sumCount();
}
```

---

## 2.5 扩容：并发迁移

ConcurrentHashMap 扩容时允许**多个线程同时迁移不同桶**（协作扩容）：

```
线程A 迁移 table[0]~table[15]
线程B 迁移 table[16]~table[31]
线程C 迁移 table[32]~table[47]
...（同时进行，互不干扰）
```

已迁移完的桶放一个 `ForwardingNode`（hash=MOVED），其他线程 `put` 时看到 MOVED 会去协助扩容而不是直接写入。

---

## 2.6 本章总结

- **读无锁**：Node.val 和 next 是 volatile，`tabAt` 用 `Unsafe.getObjectVolatile` 读取
- **写 CAS + 锁单桶**：桶为空用 CAS；桶非空 `synchronized(f)` 锁桶头节点
- **size() 弱一致**：分布式计数（baseCount + CounterCell[]），大并发下近似值；精确计数用 LongAdder
- **扩容协作**：多线程同时迁移不同桶，ForwardingNode 标记已迁移桶

> **本章对应演示代码**：`ConcurrentHashMapInternalsDemo.java`（并发安全验证、性能对比、size 弱一致演示）

**继续阅读**：[03_ConcurrentHashMap原子操作详解.md](./03_ConcurrentHashMap原子操作详解.md)
