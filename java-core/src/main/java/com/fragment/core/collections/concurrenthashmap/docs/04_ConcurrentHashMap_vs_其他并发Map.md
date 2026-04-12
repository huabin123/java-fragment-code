# 第四章：ConcurrentHashMap vs 其他并发 Map

## 4.1 四种并发 Map 对比

| 维度 | ConcurrentHashMap | Hashtable | synchronizedMap | ConcurrentSkipListMap |
|------|------------------|-----------|-----------------|----------------------|
| 锁粒度 | 桶级别（细粒度）| 对象级别（粗粒度）| 对象级别（粗粒度）| 无锁（跳表 CAS）|
| 读操作 | 无锁 | 全局锁 | 全局锁 | 无锁 |
| 写操作 | CAS + 锁单桶 | 全局 synchronized | 全局 synchronized | CAS |
| 有序性 | 无序 | 无序 | 无序 | 有序（TreeMap 的并发版）|
| null key/value | ❌ | ❌ | ✅（取决于底层 Map）| ❌ |
| 性能 | ★★★★★ | ★★ | ★★ | ★★★★ |
| 适合场景 | 通用并发缓存 | 遗留代码 | 低并发 | 需要有序的并发场景 |

---

## 4.2 ConcurrentSkipListMap：有序的并发 Map

```java
// 需要并发安全 + 有序时，用 ConcurrentSkipListMap（不是 TreeMap + 锁）
ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();

// 所有操作 O(log n)，无锁（基于 CAS），适合高并发有序场景
map.put(3, "three");
map.put(1, "one");
map.put(4, "four");

System.out.println(map.firstKey());        // 1
System.out.println(map.floorKey(3));       // 3
System.out.println(map.subMap(1, 4));      // {1=one, 3=three}

// 与 TreeMap 的区别：
// TreeMap 是 NavigableMap（有序），单线程；
// ConcurrentSkipListMap 是 ConcurrentNavigableMap（有序 + 并发安全）
```

---

## 4.3 什么时候不用 ConcurrentHashMap？

```java
// 场景1：单线程/局部变量 → 用 HashMap（更快，无并发开销）
private void processData(List<String> data) {
    Map<String, Integer> local = new HashMap<>();  // 局部变量，不会跨线程共享
    for (String s : data) local.merge(s, 1, Integer::sum);
    return local;
}

// 场景2：需要保持插入顺序 → LinkedHashMap + 手动同步
// ConcurrentHashMap 无法保证顺序

// 场景3：需要有序 + 并发 → ConcurrentSkipListMap
ConcurrentSkipListMap<Long, Event> timeline = new ConcurrentSkipListMap<>();

// 场景4：写极少读极多的 List → CopyOnWriteArrayList（不是 Map，列举作对比）
// 场景5：计数器 → LongAdder（比 ConcurrentHashMap.merge 性能更好）
```

---

## 4.4 性能实测数据

`ConcurrentHashMapInternalsDemo.java → demonstratePerformanceComparison()` 典型结果：

```
16 线程，各 10000 次（75% 读 + 25% 写）：

ConcurrentHashMap:  ~45ms   ← 读无锁，并发写不同桶
Hashtable:          ~220ms  ← 全局锁，读写全串行
synchronizedMap:    ~215ms  ← 与 Hashtable 类似

ConcurrentHashMap 快约 5x
读比例越高，差距越大（读操作完全无锁）
```

---

## 4.5 本章总结

- **首选 ConcurrentHashMap**：通用并发 Map，读无锁，性能最好
- **ConcurrentSkipListMap**：需要有序 + 并发时的选择（TreeMap 的并发版）
- **Hashtable / synchronizedMap**：遗留代码，性能差，避免在新代码中使用
- **单线程场景**：用 HashMap，避免不必要的并发开销
- **纯计数**：直接用 LongAdder，比 ConcurrentHashMap.merge 更高效

> **本章对应演示代码**：`ConcurrentHashMapInternalsDemo.java`（性能对比测试）

**继续阅读**：[05_ConcurrentHashMap最佳实践.md](./05_ConcurrentHashMap最佳实践.md)
