# 第四章：HashMap 线程安全问题与解决方案

## 4.1 HashMap 为什么不是线程安全的？

HashMap 的所有方法都没有同步措施，这是**有意为之**的设计选择——大部分 Map 使用场景是单线程的，强制加锁会导致不必要的性能损耗。

但在多线程场景下，HashMap 会出现两类问题：

### 问题一：数据丢失（JDK 8）

```
时间线：
T1: 计算桶位 i=5，发现 tab[5] == null
T2: 计算桶位 i=5，发现 tab[5] == null
T1: tab[5] = newNode(key1, val1)
T2: tab[5] = newNode(key2, val2)  ← 覆盖了 T1 的节点！

结果：key1 的数据永远丢失
```

`HashMapThreadSafetyDemo.java → testHashMap()` 演示了这个问题：10 个线程各 put 1000 次，期望 size 是 10000，实际往往小于这个值——丢失的数量每次运行都不同（取决于线程调度）。

### 问题二：死循环（JDK 7，已由 JDK 8 修复）

JDK 7 扩容使用头插法，两个线程并发扩容时会形成循环链表（节点 A→B→A），导致 `get()` 死循环、CPU 100%。JDK 8 改为尾插法后，扩容不再反转链表顺序，消除了这个问题。**但 JDK 8 的 HashMap 仍然不是线程安全的**，数据丢失问题依然存在。

---

## 4.2 四种线程安全方案对比

`HashMapThreadSafetyDemo.java` 的 `performanceComparison()` 对四种方案做了实际性能测试。

### 方案一：Hashtable（已过时，不推荐）

```java
Map<String, Integer> map = new Hashtable<>();
```

实现方式：在**每个方法**上加 `synchronized`，粒度极粗——`get` 和 `put` 互相阻塞，甚至两个不同 key 的 `put` 也互相阻塞。不允许 null key / null value（抛 NPE）。

**现状**：JDK 1.0 时代的设计，已被 `ConcurrentHashMap` 完全取代，不要在新代码中使用。

### 方案二：Collections.synchronizedMap（特殊场景用）

```java
Map<String, Integer> map = Collections.synchronizedMap(new HashMap<>());
```

本质是装饰器模式：用一个包装类，在所有方法上加同步锁（锁的是 `mutex` 对象，默认是 map 自身）。

**陷阱**：遍历时必须手动加锁，否则仍然线程不安全：
```java
// 正确写法
synchronized (map) {
    for (Map.Entry<String, Integer> entry : map.entrySet()) { ... }
}
// 错误写法：entrySet() 和遍历之间没有整体锁保护
for (Map.Entry<String, Integer> entry : map.entrySet()) { ... }
```

适合场景：已有 HashMap 代码需要快速改造成线程安全，且并发量不高。

### 方案三：ConcurrentHashMap（推荐，99% 的并发场景）

```java
Map<String, Integer> map = new ConcurrentHashMap<>();
```

JDK 8 的实现：
- **锁粒度**：只锁单个桶（`synchronized (f)`，f 是桶的第一个节点），不同桶的操作完全并发
- **读操作**：完全无锁（`volatile` 保证可见性）
- **空桶写入**：CAS 操作，完全无锁
- **不允许 null key / null value**：因为 null 在 ConcurrentHashMap 中有"不存在"的语义，无法区分"key 不存在"和"value 为 null"

```java
// ConcurrentHashMap 的 put 核心逻辑（简化）
if (桶为空) {
    CAS 写入，失败则重试  // 无锁
} else {
    synchronized (桶头节点) {
        // 链表/树插入  // 细粒度锁
    }
}
```

`HashMapThreadSafetyDemo.java → performanceComparison()` 的结果通常是：
- ConcurrentHashMap 性能接近 HashMap
- 远优于 Hashtable 和 synchronizedMap（尤其读多写少时）

### 方案四：ThreadLocal（单线程隔离，特殊场景）

```java
ThreadLocal<Map<String, Integer>> threadLocalMap =
    ThreadLocal.withInitial(HashMap::new);
```

每个线程持有独立的 HashMap 实例，彻底消除并发竞争。适合场景：每个请求/任务有独立的 Map，处理完毕后丢弃（如 Spring 的事务上下文）。

---

## 4.3 从 UserSessionManager 看实战选型

`UserSessionManager.java` 是一个典型的多线程 Map 场景：

```java
// UserSessionManager.java
private final Map<String, Session> sessions = new ConcurrentHashMap<>();
```

为什么选 `ConcurrentHashMap` 而不是其他方案？

1. **高并发读多写少**：绝大部分请求是 `validateSession()`（读），少量是 `createSession()`（写）。ConcurrentHashMap 的读完全无锁，性能最优。

2. **后台线程并发清理**：`cleanupExpiredSessions()` 在后台线程中执行 `sessions.remove()`，与业务线程的 `put/get` 并发。Hashtable 的粗锁会成为瓶颈。

3. **原子操作需求**：`sessions.putIfAbsent()` 是原子的，防止两个线程同时创建相同 sessionId 的会话（虽然 UUID 重复概率极低，但防御性编程更安全）。

**注意**：`cleanupExpiredSessions()` 中遍历 + 删除的组合在 `ConcurrentHashMap` 中是安全的（迭代器是弱一致性的，不会抛 `ConcurrentModificationException`），但可能漏掉或重复处理边界元素。

---

## 4.4 常见误区

### 误区一：用 ConcurrentHashMap 替换所有 HashMap

ConcurrentHashMap 有额外开销（volatile 读、CAS 写），单线程场景用它没有意义。**遵循原则**：单线程用 HashMap，多线程用 ConcurrentHashMap。

### 误区二：ConcurrentHashMap 的复合操作不是原子的

```java
// 错误：get + put 不是原子操作，仍然有并发问题
if (!map.containsKey(key)) {
    map.put(key, computeValue());
}

// 正确：使用原子的 computeIfAbsent
map.computeIfAbsent(key, k -> computeValue());
```

### 误区三：遍历 ConcurrentHashMap 时修改

```java
// 安全：ConcurrentHashMap 的迭代器是弱一致性的，不会抛 CME
for (Map.Entry<String, Session> entry : map.entrySet()) {
    if (isExpired(entry.getValue())) {
        map.remove(entry.getKey());  // 安全，但可能漏掉并发新增的 key
    }
}
```

---

## 4.5 本章总结

- **数据丢失根因**：`tab[i] == null` 的判断和 `tab[i] = newNode(...)` 的写入之间没有原子保证
- **JDK 7 死循环**：已由 JDK 8 的尾插法修复，但 HashMap 依然不是线程安全的
- **四种方案**：Hashtable（过时）、synchronizedMap（低并发改造用）、ConcurrentHashMap（推荐）、ThreadLocal（请求隔离用）
- **ConcurrentHashMap 优势**：读无锁 + 写细粒度锁，不允许 null key/value
- **复合操作**：用 `computeIfAbsent`/`putIfAbsent` 等原子方法，不要用 containsKey + put 组合

> **本章对应演示代码**：`HashMapThreadSafetyDemo.java`（四种方案的线程安全验证与性能对比）、`UserSessionManager.java`（ConcurrentHashMap 实战）

**继续阅读**：[05_HashMap最佳实践与性能优化.md](./05_HashMap最佳实践与性能优化.md)
