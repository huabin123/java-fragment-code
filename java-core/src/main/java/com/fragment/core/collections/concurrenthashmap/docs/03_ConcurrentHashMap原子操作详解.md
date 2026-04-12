# 第三章：ConcurrentHashMap 原子操作详解

## 3.1 为什么要用原子操作方法？

```java
// ❌ 错误：get + put 两步不是原子的
if (!map.containsKey("key")) {          // 步骤1：检查
    map.put("key", computeValue());     // 步骤2：写入
    // 两步之间另一个线程可能已经 put 了同一个 key！
}

// ✅ 正确：用 putIfAbsent（原子的 check-then-act）
map.putIfAbsent("key", computeValue());
// 但注意：computeValue() 在 put 之前就被调用了（即使 key 存在也调用）

// ✅ 更好：computeIfAbsent（只有 key 不存在时才调用计算函数）
map.computeIfAbsent("key", k -> computeValue());
```

---

## 3.2 六个原子操作方法速查

`ConcurrentHashMapBasicDemo.java → demonstrateAtomicOps()` 演示了所有原子方法：

| 方法 | 语义 | 常见用途 |
|------|------|---------|
| `putIfAbsent(k, v)` | key 不存在时 put，返回旧值（null 表示插入成功）| 注册、去重 |
| `computeIfAbsent(k, fn)` | key 不存在时调用 fn 计算并 put | 懒初始化嵌套结构、缓存 |
| `computeIfPresent(k, fn)` | key 存在时用 fn 更新，返回新值 | 更新存在的值 |
| `compute(k, fn)` | 无论 key 是否存在都调用 fn，fn 返回 null 则删除 | 通用原子更新 |
| `merge(k, v, fn)` | key 不存在时 put v；存在时用 fn(oldVal, v) 合并 | 计数、聚合 |
| `replace(k, old, new)` | CAS 语义：期望值匹配才替换，返回是否成功 | 乐观锁更新 |

---

## 3.3 computeIfAbsent：初始化嵌套结构的标准写法

```java
// 场景：按城市分组存储用户列表（并发安全）
ConcurrentHashMap<String, ConcurrentHashMap<String, User>> cityUsers = new ConcurrentHashMap<>();

// ✅ 标准写法：computeIfAbsent 保证每个 city 只创建一次内部 Map
cityUsers.computeIfAbsent("北京", k -> new ConcurrentHashMap<>())
         .put(user.getId(), user);

// 等价的 if-else 写法（有竞争问题）：
// if (!cityUsers.containsKey("北京")) {
//     cityUsers.put("北京", new ConcurrentHashMap<>());  // 可能创建多次！
// }
// cityUsers.get("北京").put(user.getId(), user);

// OnlineUserRegistry.java 中的 computeIfPresent 更新心跳：
sessions.computeIfPresent(userId, (id, session) -> {
    session.updateLastActive();
    return session;  // 返回原对象（原子地保证 key 存在才更新）
});
```

---

## 3.4 merge：最适合计数和聚合

```java
// ConcurrentHashMapPatternDemo.java → demonstrateCounting()

// 词频统计：merge 是最简洁的原子计数方式
ConcurrentHashMap<String, Integer> freq = new ConcurrentHashMap<>();
for (String word : words) {
    freq.merge(word, 1, Integer::sum);
    // 等价于：
    // if (freq.containsKey(word)) freq.put(word, freq.get(word) + 1);
    // else freq.put(word, 1);
    // 但 merge 是原子的，上面的代码不是！
}

// 高并发下，merge 比 LongAdder 慢（merge 每次 CAS 可能需要重试）
// 高 QPS 计数推荐 computeIfAbsent + LongAdder：
ConcurrentHashMap<String, LongAdder> fastFreq = new ConcurrentHashMap<>();
for (String word : words) {
    fastFreq.computeIfAbsent(word, k -> new LongAdder()).increment();
    // LongAdder.increment() 内部分散竞争，几乎无冲突
}
```

---

## 3.5 replace：CAS 语义的乐观锁

```java
// ConcurrentHashMapBasicDemo.java → demonstrateConcurrentCount()

// replace(key, expectedOldValue, newValue)
// 只有 value 等于 expectedOldValue 时才替换，否则返回 false
// 实现无锁的乐观更新：

ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("score", 100);

// 无锁 +10 操作（CAS 循环）
boolean updated = false;
while (!updated) {
    Integer current = map.get("score");
    if (current == null) break;
    updated = map.replace("score", current, current + 10);
    // 如果其他线程在读和写之间修改了 score，replace 失败，重试
}
System.out.println("score: " + map.get("score"));  // 110

// 实际上 compute 更简洁（内部也是 CAS 循环）：
map.compute("score", (k, v) -> v == null ? 10 : v + 10);
```

---

## 3.6 本章总结

- **根本原则**：所有复合操作（check-then-act）都要用原子方法，不能用 get + put 组合
- **putIfAbsent**：注册/去重场景；注意计算函数在 put 前就被调用
- **computeIfAbsent**：懒初始化嵌套结构和缓存的标准写法
- **merge**：计数/聚合的简洁写法；高 QPS 改用 `computeIfAbsent + LongAdder`
- **replace(k,old,new)**：CAS 语义，适合乐观锁更新；`compute` 更简洁

> **本章对应演示代码**：`ConcurrentHashMapBasicDemo.java`（所有原子操作）、`ConcurrentHashMapPatternDemo.java`（计数、懒加载、去重四种模式）

**继续阅读**：[04_ConcurrentHashMap vs 其他并发Map.md](./04_ConcurrentHashMap_vs_其他并发Map.md)
