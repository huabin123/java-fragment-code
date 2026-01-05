# 第一章：ConcurrentHashMap深入 - 高并发Map的设计艺术

> **学习目标**：深入理解ConcurrentHashMap的设计思想、实现原理和使用技巧

---

## 一、为什么需要ConcurrentHashMap？

### 1.1 HashMap的线程安全问题

```java
// 问题：HashMap在多线程下不安全
Map<String, Integer> map = new HashMap<>();

// 线程1
map.put("key1", 1);

// 线程2
map.put("key2", 2);

// 可能导致：
// 1. 数据丢失
// 2. 死循环（JDK 7的resize）
// 3. 数据覆盖
```

**HashMap的问题**：

```
1. put操作不是原子的：
   - 计算hash
   - 找到bucket
   - 插入节点
   - 可能触发扩容
   
2. resize导致死循环（JDK 7）：
   - 多线程同时扩容
   - 链表形成环
   - get操作死循环

3. size()不准确：
   - 多线程修改
   - 计数不同步
```

### 1.2 Hashtable的性能问题

```java
// Hashtable：线程安全但性能差
Map<String, Integer> map = new Hashtable<>();

public synchronized V put(K key, V value) {
    // 整个方法加锁
}

public synchronized V get(Object key) {
    // 读操作也加锁
}

// 问题：
// ❌ 锁粒度太大（整个对象）
// ❌ 读写互斥（读也要加锁）
// ❌ 性能差（高并发下）
```

### 1.3 Collections.synchronizedMap的问题

```java
// synchronizedMap：包装HashMap
Map<String, Integer> map = Collections.synchronizedMap(new HashMap<>());

// 实现：
public V put(K key, V value) {
    synchronized (mutex) {  // 对象锁
        return m.put(key, value);
    }
}

// 问题：
// ❌ 锁粒度大
// ❌ 性能差
// ❌ 迭代需要手动加锁
```

### 1.4 ConcurrentHashMap的解决方案

```java
// ConcurrentHashMap：高性能并发Map
Map<String, Integer> map = new ConcurrentHashMap<>();

// 优势：
// ✅ 细粒度锁（JDK 7：分段锁，JDK 8：CAS + synchronized）
// ✅ 读不加锁（volatile）
// ✅ 高并发性能好
// ✅ 弱一致性迭代器
```

---

## 二、JDK 7 vs JDK 8的实现

### 2.1 JDK 7：分段锁（Segment）

```java
// JDK 7的结构
public class ConcurrentHashMap<K,V> extends AbstractMap<K,V> {
    
    // Segment数组（默认16个）
    final Segment<K,V>[] segments;
    
    // Segment继承ReentrantLock
    static final class Segment<K,V> extends ReentrantLock {
        // 每个Segment是一个小HashMap
        transient volatile HashEntry<K,V>[] table;
        transient int count;
        transient int modCount;
        transient int threshold;
    }
    
    // HashEntry节点
    static final class HashEntry<K,V> {
        final int hash;
        final K key;
        volatile V value;
        volatile HashEntry<K,V> next;
    }
}
```

**分段锁的原理**：

```
ConcurrentHashMap
    ↓
[Segment0] [Segment1] ... [Segment15]
    ↓          ↓              ↓
  [table]    [table]        [table]
    ↓          ↓              ↓
  链表       链表            链表

特点：
1. 默认16个Segment
2. 每个Segment独立加锁
3. 最多支持16个线程并发写
4. 读操作不加锁（volatile）
```

**put操作流程**：

```
1. 计算hash值
2. 定位Segment（hash >>> segmentShift）
3. 获取Segment的锁
4. 在Segment中put
5. 释放锁

并发度：
- 最多16个线程同时写
- 读操作无限制
```

### 2.2 JDK 8：CAS + synchronized

```java
// JDK 8的结构
public class ConcurrentHashMap<K,V> extends AbstractMap<K,V> {
    
    // Node数组
    transient volatile Node<K,V>[] table;
    
    // 节点
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        volatile V value;
        volatile Node<K,V> next;
    }
    
    // 红黑树节点
    static final class TreeNode<K,V> extends Node<K,V> {
        TreeNode<K,V> parent;
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        TreeNode<K,V> prev;
        boolean red;
    }
}
```

**JDK 8的改进**：

```
1. 取消Segment：
   - 直接用Node数组
   - 锁粒度更细（锁单个bucket）

2. 链表转红黑树：
   - 链表长度 >= 8 转红黑树
   - 红黑树节点 <= 6 转链表
   - 提升查询性能

3. CAS + synchronized：
   - 首次put用CAS
   - 冲突时用synchronized锁bucket
   - 比ReentrantLock轻量

4. 并发度提升：
   - 理论上支持table.length个线程并发写
```

**对比表**：

| 特性 | JDK 7 | JDK 8 |
|------|-------|-------|
| 数据结构 | Segment数组 + HashEntry数组 | Node数组 |
| 锁机制 | 分段锁（ReentrantLock） | CAS + synchronized |
| 并发度 | Segment数量（默认16） | table.length |
| 链表优化 | 无 | 链表转红黑树 |
| 内存占用 | 大（Segment开销） | 小 |

---

## 三、JDK 8 ConcurrentHashMap源码分析

### 3.1 核心字段

```java
public class ConcurrentHashMap<K,V> {
    
    // Node数组
    transient volatile Node<K,V>[] table;
    
    // 扩容时的新数组
    private transient volatile Node<K,V>[] nextTable;
    
    // 基础计数
    private transient volatile long baseCount;
    
    // 计数单元数组
    private transient volatile CounterCell[] counterCells;
    
    // 控制标识符
    // -1：正在初始化
    // -N：有N-1个线程正在扩容
    // 0：未初始化
    // >0：下次扩容的阈值
    private transient volatile int sizeCtl;
    
    // 常量
    static final int TREEIFY_THRESHOLD = 8;   // 树化阈值
    static final int UNTREEIFY_THRESHOLD = 6; // 反树化阈值
    static final int MIN_TREEIFY_CAPACITY = 64; // 最小树化容量
}
```

### 3.2 put操作详解

```java
public V put(K key, V value) {
    return putVal(key, value, false);
}

final V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    
    // 1. 计算hash
    int hash = spread(key.hashCode());
    int binCount = 0;
    
    for (Node<K,V>[] tab = table;;) {
        Node<K,V> f; int n, i, fh;
        
        // 2. 初始化table
        if (tab == null || (n = tab.length) == 0)
            tab = initTable();
        
        // 3. bucket为空，CAS插入
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value, null)))
                break;  // CAS成功，退出
        }
        
        // 4. 正在扩容，帮助扩容
        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);
        
        // 5. bucket不为空，synchronized锁住bucket
        else {
            V oldVal = null;
            synchronized (f) {  // 锁住首节点
                if (tabAt(tab, i) == f) {
                    // 5.1 链表
                    if (fh >= 0) {
                        binCount = 1;
                        for (Node<K,V> e = f;; ++binCount) {
                            K ek;
                            if (e.hash == hash &&
                                ((ek = e.key) == key ||
                                 (ek != null && key.equals(ek)))) {
                                oldVal = e.val;
                                if (!onlyIfAbsent)
                                    e.val = value;
                                break;
                            }
                            Node<K,V> pred = e;
                            if ((e = e.next) == null) {
                                pred.next = new Node<K,V>(hash, key, value, null);
                                break;
                            }
                        }
                    }
                    // 5.2 红黑树
                    else if (f instanceof TreeBin) {
                        Node<K,V> p;
                        binCount = 2;
                        if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key, value)) != null) {
                            oldVal = p.val;
                            if (!onlyIfAbsent)
                                p.val = value;
                        }
                    }
                }
            }
            
            // 6. 检查是否需要树化
            if (binCount != 0) {
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);
                if (oldVal != null)
                    return oldVal;
                break;
            }
        }
    }
    
    // 7. 增加计数
    addCount(1L, binCount);
    return null;
}
```

**put流程图**：

```
开始
  ↓
计算hash
  ↓
table为空？
├─ 是 → 初始化table
└─ 否 ↓
bucket为空？
├─ 是 → CAS插入 → 成功？
│                 ├─ 是 → 增加计数 → 结束
│                 └─ 否 → 重试
└─ 否 ↓
正在扩容？
├─ 是 → 帮助扩容 → 重试
└─ 否 ↓
synchronized锁bucket
  ↓
链表还是红黑树？
├─ 链表 → 遍历链表 → 插入/更新
└─ 红黑树 → 插入红黑树
  ↓
链表长度 >= 8？
├─ 是 → 树化
└─ 否 ↓
增加计数
  ↓
检查是否扩容
  ↓
结束
```

### 3.3 get操作详解

```java
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> e, p; int n, eh; K ek;
    
    // 1. 计算hash
    int h = spread(key.hashCode());
    
    // 2. table不为空且bucket不为空
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (e = tabAt(tab, (n - 1) & h)) != null) {
        
        // 3. 首节点就是目标
        if ((eh = e.hash) == h) {
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val;
        }
        // 4. hash < 0，说明是红黑树或正在扩容
        else if (eh < 0)
            return (p = e.find(h, key)) != null ? p.val : null;
        
        // 5. 遍历链表
        while ((e = e.next) != null) {
            if (e.hash == h &&
                ((ek = e.key) == key || (ek != null && key.equals(ek))))
                return e.val;
        }
    }
    return null;
}
```

**get的特点**：

```
1. 不加锁：
   - volatile保证可见性
   - 读操作无锁

2. 性能好：
   - O(1)：直接命中
   - O(log n)：红黑树
   - O(n)：链表

3. 弱一致性：
   - 可能读到旧值
   - 不保证最新值
```

### 3.4 扩容机制

```java
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    
    // 1. 计算每个线程处理的bucket数量
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
        stride = MIN_TRANSFER_STRIDE;
    
    // 2. 初始化新数组
    if (nextTab == null) {
        try {
            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
            nextTab = nt;
        } catch (Throwable ex) {
            sizeCtl = Integer.MAX_VALUE;
            return;
        }
        nextTable = nextTab;
        transferIndex = n;
    }
    
    int nextn = nextTab.length;
    ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
    
    // 3. 多线程协作扩容
    boolean advance = true;
    boolean finishing = false;
    
    for (int i = 0, bound = 0;;) {
        // 分配任务
        // ...
        
        // 处理bucket
        synchronized (f) {
            // 迁移数据
            // ...
        }
    }
}
```

**扩容特点**：

```
1. 多线程协作：
   - 每个线程处理一部分bucket
   - 提升扩容速度

2. 渐进式扩容：
   - 不是一次性完成
   - 边扩容边服务

3. ForwardingNode：
   - 标记已迁移的bucket
   - hash = MOVED (-1)
   - 指向新数组
```

---

## 四、size()的实现

### 4.1 为什么size()很难实现？

```java
// 问题：如何在不加锁的情况下统计size？

// 方案1：遍历累加（不准确）
public int size() {
    long n = sumCount();
    return ((n < 0L) ? 0 :
            (n > (long)Integer.MAX_VALUE) ? Integer.MAX_VALUE :
            (int)n);
}

// 问题：
// - 遍历过程中可能有新增/删除
// - 结果不准确
```

### 4.2 LongAdder思想

```java
// 使用类似LongAdder的分段计数
private transient volatile long baseCount;
private transient volatile CounterCell[] counterCells;

@sun.misc.Contended
static final class CounterCell {
    volatile long value;
    CounterCell(long x) { value = x; }
}

// 增加计数
private final void addCount(long x, int check) {
    CounterCell[] as; long b, s;
    
    // 1. 尝试CAS更新baseCount
    if ((as = counterCells) != null ||
        !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
        
        // 2. 失败则更新CounterCell
        CounterCell a; long v; int m;
        boolean uncontended = true;
        if (as == null || (m = as.length - 1) < 0 ||
            (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
            !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
            fullAddCount(x, uncontended);
            return;
        }
    }
    // ...
}

// 统计总数
final long sumCount() {
    CounterCell[] as = counterCells; CounterCell a;
    long sum = baseCount;
    if (as != null) {
        for (int i = 0; i < as.length; ++i) {
            if ((a = as[i]) != null)
                sum += a.value;
        }
    }
    return sum;
}
```

**分段计数的优势**：

```
1. 减少竞争：
   - 多个CounterCell
   - 分散更新

2. 性能好：
   - 高并发下性能稳定
   - 类似LongAdder

3. 弱一致性：
   - size()返回近似值
   - 不保证精确
```

---

## 五、实际使用技巧

### 5.1 选择合适的初始容量

```java
// ❌ 不好：默认容量16，频繁扩容
Map<String, Integer> map = new ConcurrentHashMap<>();

// ✅ 好的：预估容量，减少扩容
Map<String, Integer> map = new ConcurrentHashMap<>(1000);

// 计算公式：
// initialCapacity = (预期元素数量 / 0.75) + 1
```

### 5.2 避免使用size()判断是否为空

```java
// ❌ 不好：size()性能差
if (map.size() == 0) {
    // ...
}

// ✅ 好的：使用isEmpty()
if (map.isEmpty()) {
    // ...
}
```

### 5.3 使用compute系列方法

```java
// ❌ 不好：复合操作不是原子的
Integer count = map.get(key);
if (count == null) {
    map.put(key, 1);
} else {
    map.put(key, count + 1);
}

// ✅ 好的：使用compute
map.compute(key, (k, v) -> v == null ? 1 : v + 1);

// 或者使用merge
map.merge(key, 1, Integer::sum);
```

### 5.4 迭代时的注意事项

```java
// 弱一致性迭代器
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    // 可能看不到最新的修改
    // 不会抛出ConcurrentModificationException
}

// 如果需要强一致性，手动加锁
synchronized (map) {
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
        // ...
    }
}
```

---

## 六、常见陷阱

### 6.1 不支持null

```java
// ❌ 错误：不支持null key和null value
map.put(null, 1);  // NullPointerException
map.put("key", null);  // NullPointerException

// 原因：
// - null无法区分"不存在"和"值为null"
// - get(key)返回null表示key不存在
```

### 6.2 size()不精确

```java
// size()返回的是近似值
int size = map.size();  // 可能不准确

// 如果需要精确计数，使用AtomicInteger
AtomicInteger counter = new AtomicInteger(0);
map.put(key, value);
counter.incrementAndGet();
```

### 6.3 putAll不是原子的

```java
// putAll不是原子操作
map.putAll(otherMap);  // 不是原子的

// 如果需要原子性，手动加锁
synchronized (map) {
    map.putAll(otherMap);
}
```

---

## 七、总结

### 7.1 核心要点

1. **JDK 7**：分段锁（Segment），并发度16
2. **JDK 8**：CAS + synchronized，并发度更高
3. **数据结构**：数组 + 链表 + 红黑树
4. **读操作**：无锁，volatile保证可见性
5. **写操作**：CAS + synchronized锁bucket
6. **扩容**：多线程协作，渐进式扩容
7. **计数**：分段计数，类似LongAdder

### 7.2 性能特点

```
优势：
✅ 高并发性能好
✅ 读操作无锁
✅ 细粒度锁
✅ 支持并发扩容

劣势：
❌ 内存占用大
❌ 弱一致性
❌ size()不精确
```

### 7.3 思考题

1. **为什么JDK 8要取消Segment？**
2. **ConcurrentHashMap如何保证线程安全？**
3. **为什么链表长度>=8才转红黑树？**
4. **size()为什么不精确？**

---

**下一章预告**：我们将学习CopyOnWrite容器的写时复制机制。

---

**参考资料**：
- 《Java并发编程实战》第5章
- JDK源码：`java.util.concurrent.ConcurrentHashMap`
- Doug Lea的论文
