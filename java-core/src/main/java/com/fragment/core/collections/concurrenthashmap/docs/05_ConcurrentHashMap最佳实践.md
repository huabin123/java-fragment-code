# 第五章：ConcurrentHashMap 最佳实践

## 5.1 复合操作必须用原子方法

```java
// ❌ 经典错误：get + put 非原子
if (!map.containsKey(key)) {
    map.put(key, value);  // 竞态条件！
}

// ✅ 替换为原子方法
map.putIfAbsent(key, value);                        // 不存在时 put
map.computeIfAbsent(key, k -> new ArrayList<>());   // 不存在时计算
map.merge(key, 1, Integer::sum);                    // 存在时合并
map.compute(key, (k, v) -> v == null ? 1 : v + 1); // 通用原子更新
```

## 5.2 高并发计数用 LongAdder

```java
// ❌ 低效：merge 在高并发下 CAS 竞争频繁
ConcurrentHashMap<String, Integer> counter = new ConcurrentHashMap<>();
counter.merge(event, 1, Integer::sum);

// ✅ 高效：LongAdder 分散竞争，高 QPS 下性能好数倍
ConcurrentHashMap<String, LongAdder> counter = new ConcurrentHashMap<>();
counter.computeIfAbsent(event, k -> new LongAdder()).increment();

// 读取时求和
long count = counter.getOrDefault(event, new LongAdder()).sum();
```

## 5.3 用 ConcurrentHashMap.newKeySet() 代替并发 Set

```java
// ✅ 利用 ConcurrentHashMap 实现并发 Set（JDK 8+）
Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
// 比 Collections.synchronizedSet(new HashSet<>()) 性能更好
// 与 ConcurrentHashMap 同等并发安全级别

// OnlineUserRegistry.java 中的用法：
private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
onlineUsers.add("user001");
onlineUsers.remove("user002");
boolean isOnline = onlineUsers.contains("user003");
```

## 5.4 避免在计算函数中做耗时操作

```java
// ⚠️ computeIfAbsent 期间桶会被锁住（锁住桶头节点）
// 如果计算函数很慢，会阻塞其他线程写入同一个桶
map.computeIfAbsent(key, k -> {
    return slowDbQuery(k);  // ⚠️ 锁住桶期间执行 DB 查询，阻塞其他线程
});

// ✅ 先计算，再 putIfAbsent
Value computed = slowDbQuery(key);           // 先算（此时不持有锁）
map.putIfAbsent(key, computed);              // 再原子写入
// 缺点：key 存在时也会做无用计算
// 更好的方案：用 Guava 的 Cache 或 Caffeine，内置加载机制
```

## 5.5 遍历的一致性

```java
// ConcurrentHashMap 的遍历是弱一致的（weakly consistent）
// 遍历过程中允许其他线程修改，不会抛 ConcurrentModificationException
// 但不保证能看到并发修改后的所有变化

ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
// 在遍历期间另一个线程 put 新 key：可能看到也可能看不到
// 在遍历期间另一个线程 remove key：可能看到也可能已经看过

// ✅ 如果需要一致性快照：先转成普通 Map
Map<String, Integer> snapshot = new HashMap<>(map);
snapshot.forEach((k, v) -> process(k, v));  // 遍历快照，不受并发影响
```

## 5.6 本章总结

**五条核心实践**：
1. **复合操作用原子方法**：`putIfAbsent`、`computeIfAbsent`、`merge`、`compute`
2. **高并发计数用 LongAdder**：比 `merge(Integer::sum)` 性能好数倍
3. **并发 Set 用 `newKeySet()`**：比 `synchronizedSet` 并发度更高
4. **计算函数不做耗时操作**：`compute*` 期间持有桶锁，慢操作会阻塞同桶其他线程
5. **遍历是弱一致的**：不抛 CME，但不保证看到所有并发修改；需快照则先 `new HashMap<>(map)`

> **本章对应演示代码**：`ConcurrentHashMapPatternDemo.java`（计数、分组、缓存、去重）、`OnlineUserRegistry.java`（在线用户注册）、`RequestRateLimiter.java`（无锁限流）

**返回目录**：[README.md](../README.md)
