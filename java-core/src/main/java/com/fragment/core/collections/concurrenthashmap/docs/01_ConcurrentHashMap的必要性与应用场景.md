# 第一章：ConcurrentHashMap 的必要性与应用场景

## 1.1 HashMap 在并发下的问题

```java
// HashMap 并发写入的三个问题：
// 1. 数据丢失：两个线程同时 put 到同一个桶，互相覆盖
// 2. 死循环（JDK 7）：并发扩容时链表形成环，CPU 100%
// 3. ArrayIndexOutOfBoundsException：扩容过程中读写竞争
HashMap<String, Integer> map = new HashMap<>();
// 10 个线程各 put 1000 个不同 key
// 期望 size=10000，实际可能是 9800+，或直接崩溃
```

---

## 1.2 Hashtable 够用吗？

Hashtable 确实是线程安全的，但用的是最粗暴的方式：**所有方法都加 `synchronized`**，锁的是整个对象。

```java
// Hashtable 的问题：同一时刻只有一个线程能操作（读也不行）
public synchronized V get(Object key) { ... }
public synchronized V put(K key, V value) { ... }

// 16 个线程读写：实际变成串行执行，完全没发挥多核优势
// ConcurrentHashMapInternalsDemo.java 实测：Hashtable 比 ConcurrentHashMap 慢约 5x
```

---

## 1.3 ConcurrentHashMap 的解决思路

**JDK 7**：分段锁（Segment），把整个 table 分为 16 个 Segment，每个 Segment 有独立的锁。不同 Segment 的操作可以并行，提升并发度到 16。

**JDK 8**：彻底重写，**CAS + synchronized 锁单个桶**：
- **读操作**：完全无锁（volatile 保证可见性）
- **写操作**：只锁目标桶的第一个节点（链表头 / 树根）
- 理论并发度 = table 数组长度（默认 16，最大并发写 16 个不同桶）

---

## 1.4 最适合 ConcurrentHashMap 的场景

**场景一：高并发缓存**

```java
// OnlineUserRegistry.java → sessions
ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();
// 多个请求线程同时读写不同用户的 Session，互不影响
```

**场景二：并发计数/统计**

```java
// ConcurrentHashMapPatternDemo.java → demonstrateCounting()
ConcurrentHashMap<String, LongAdder> stats = new ConcurrentHashMap<>();
// 多线程统计词频、PV/UV、接口调用次数
stats.computeIfAbsent(event, k -> new LongAdder()).increment();
```

**场景三：并发去重/注册**

```java
// ConcurrentHashMapPatternDemo.java → demonstrateDeduplication()
// putIfAbsent 原子操作，保证同一 key 只被注册一次
String prev = registry.putIfAbsent(username, "registered");
```

**场景四：高并发限流**

```java
// RequestRateLimiter.java：ConcurrentHashMap + LongAdder 实现无锁限流
// 每个用户的计数器独立，不同用户请求互不干扰
```

---

## 1.5 ConcurrentHashMap 的限制

```java
// ❌ 不允许 null key 和 null value（与 HashMap 不同！）
map.put(null, "value");   // NullPointerException
map.put("key", null);     // NullPointerException

// 原因：null 在并发环境下会产生歧义
// map.get(key) 返回 null，无法区分：key 不存在 vs key 对应的值是 null
// HashMap 可以用 containsKey 区分，但并发场景下这两步之间状态可能变化

// ❌ 复合操作不是原子的（需要用专门的方法）
if (!map.containsKey(key)) {   // ← 这两步之间其他线程可能已经 put
    map.put(key, value);       // ← 不原子！
}
// ✅ 改为：
map.putIfAbsent(key, value);   // 原子

// ❌ size() 是弱一致性的（并发修改期间不精确）
// ✅ 改用 mappingCount() 或用 LongAdder 自己维护精确计数
```

---

## 1.6 本章总结

- **HashMap 并发问题**：数据丢失、JDK 7 死循环、崩溃
- **Hashtable 的缺点**：全局锁，并发退化为串行
- **ConcurrentHashMap 方案**：JDK 8 = CAS + 锁单个桶，读完全无锁
- **禁止 null**：null key/value 在并发下有歧义，统一禁止
- **适合场景**：高并发缓存、计数统计、去重注册、限流

> **本章对应演示代码**：`ConcurrentHashMapBasicDemo.java`（基础操作）、`ConcurrentHashMapInternalsDemo.java`（并发安全验证、性能对比）

**继续阅读**：[02_ConcurrentHashMap内部原理.md](./02_ConcurrentHashMap内部原理.md)
