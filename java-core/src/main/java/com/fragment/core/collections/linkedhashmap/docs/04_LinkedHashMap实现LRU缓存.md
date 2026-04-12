# 第四章：LinkedHashMap 实现 LRU 缓存

## 4.1 LRU 算法的本质

**LRU（Least Recently Used，最近最少使用）**是一种缓存淘汰策略：当缓存满时，驱逐最久未被访问的元素。

核心假设：**最近被访问过的数据，未来大概率还会被访问**（时间局部性原理）。

LRU 需要支持的操作：
- `get(key)`：O(1) 查找，同时将元素标记为"最近使用"
- `put(key, value)`：O(1) 插入，若满则驱逐最久未使用的元素

**为什么 LinkedHashMap 天然适合实现 LRU？**

LRU 需要一个数据结构同时满足：
- O(1) 按 key 查找：**HashMap**
- O(1) 将访问过的元素移到"最新"位置：**双向链表**（找到节点后，修改前后指针）
- O(1) 驱逐"最旧"元素：**双向链表头部直接删除**

LinkedHashMap 的 `accessOrder=true` 模式完全满足这三个条件。

---

## 4.2 最简 LRU 实现：3 行代码

```java
// LinkedHashMapLRUDemo.java → simpleLRUCache()
int maxCapacity = 3;
Map<String, String> lruCache = new LinkedHashMap<String, String>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > maxCapacity;  // 唯一需要写的逻辑
    }
};
```

**运行过程演示**（`LinkedHashMapLRUDemo.java` 的实际输出）：

```
put key1 → 链表：[key1]
put key2 → 链表：[key1, key2]
put key3 → 链表：[key1, key2, key3]

get key1  → key1 被访问，移到尾部 → 链表：[key2, key3, key1]

put key4  → 插入 key4，size=4 > 3，驱逐 head（key2）
           → 链表：[key3, key1, key4]

get key3  → key3 被访问，移到尾部 → 链表：[key1, key4, key3]

put key5  → 插入 key5，size=4 > 3，驱逐 head（key1）
           → 链表：[key4, key3, key5]
```

---

## 4.3 生产级 LRU 缓存：DatabaseQueryCache 分析

`DatabaseQueryCache.java` 在上述基础上增加了：
- **命中率统计**（`hitCount`/`missCount`/`evictionCount`）
- **缓存失效接口**（`invalidate(sql)` 清除特定 SQL 的缓存）
- **完整的查询包装**（`query()` 方法自动处理缓存命中/未命中）

```java
// DatabaseQueryCache.java 的核心模式
public QueryResult query(String sql, Object... params) {
    String cacheKey = generateCacheKey(sql, params);  // "SELECT...WHERE id=?:1"

    // 1. 查缓存（get 同时更新 LRU 顺序）
    QueryResult result = cache.get(cacheKey);
    if (result != null) {
        hitCount++;
        return result;  // 缓存命中，直接返回
    }

    // 2. 缓存未命中，执行真实查询
    missCount++;
    result = executeQuery(sql, params);  // 模拟 100ms 数据库查询

    // 3. 结果放入缓存（put 后触发 removeEldestEntry 检查）
    cache.put(cacheKey, result);
    return result;
}
```

**测试场景分析（`DatabaseQueryCache.main()`）**：
1. 查 id=1,2,3（miss×3，缓存满：[1,2,3]）
2. 查 id=1,2（hit×2，缓存更新：[3,1,2]）
3. 查 id=4（miss，缓存满，驱逐 head=3：[1,2,4]）
4. 查 id=3（miss，3 已被驱逐，缓存更新：[2,4,3]）

最终命中率：2/(2+4) ≈ 33%，这反映了测试中的访问模式。

---

## 4.4 LRU 的线程安全问题

`DatabaseQueryCache.java` 的注释明确指出：**线程不安全**。

在 `accessOrder=true` 模式下，`get` 操作会修改双向链表（移动节点），这比 HashMap 更危险——不仅是写操作，**读操作（get）也会修改数据结构**，导致即使是"只读"并发也会引发问题。

### 方案一：外部 synchronized（简单，但粗粒度）

```java
private final Map<String, QueryResult> cache;
private final Object lock = new Object();

public QueryResult query(String sql, Object... params) {
    String cacheKey = generateCacheKey(sql, params);
    synchronized (lock) {
        QueryResult result = cache.get(cacheKey);  // get 也需要锁！
        if (result != null) return result;
    }
    // 注意：查询数据库在锁外执行（避免持锁期间长时间 IO）
    QueryResult result = executeQuery(sql, params);
    synchronized (lock) {
        cache.put(cacheKey, result);
    }
    return result;
}
```

**缺点**：可能出现缓存击穿（多个线程同时发现 cache miss，同时执行数据库查询）。

### 方案二：Collections.synchronizedMap（等价于方案一，更简洁但有陷阱）

```java
Map<String, QueryResult> cache = Collections.synchronizedMap(
    new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(...) { return size() > maxSize; }
    }
);
```

**陷阱**：遍历时需要手动加锁，否则会 CME。

### 方案三：Caffeine（生产环境推荐）

对于真正的生产级高并发 LRU 缓存，推荐使用 [Caffeine](https://github.com/ben-manes/caffeine)：

```java
Cache<String, QueryResult> cache = Caffeine.newBuilder()
    .maximumSize(maxSize)
    .build();

QueryResult result = cache.get(cacheKey, key -> executeQuery(sql, params));
```

Caffeine 使用 Window TinyLFU 算法（比纯 LRU 命中率更高），内部用无锁结构实现高并发，是 Spring Cache、Guava Cache 的现代替代品。

---

## 4.5 LRU vs 其他缓存淘汰策略

| 策略 | 全称 | 驱逐规则 | 适用场景 |
|------|------|---------|---------|
| LRU | Least Recently Used | 最久未访问 | 时间局部性强的场景（大多数业务缓存）|
| LFU | Least Frequently Used | 访问频率最低 | 热点数据稳定的场景（部分 key 永远是热点）|
| FIFO | First In First Out | 最早插入 | 时间序相关的场景（消息、日志）|
| Random | 随机 | 随机驱逐 | 简单场景，不需要精确控制 |

LinkedHashMap 只直接支持 LRU（通过 `accessOrder=true`）。LFU 需要额外维护频次计数，LinkedHashMap 无法直接实现。

---

## 4.6 本章总结

- **LRU 三要素**：O(1) 查找（HashMap）+ O(1) 移位（双向链表）+ O(1) 驱逐头部（链表头）
- **最简实现**：`accessOrder=true` + 覆盖 `removeEldestEntry`，3 行代码
- **get 也修改结构**：`accessOrder=true` 下，读操作也修改链表，多线程下需要对 get 加锁
- **生产推荐**：高并发场景用 Caffeine；单线程/低并发用 LinkedHashMap LRU
- **命中率**：通过 `hitCount`/`missCount` 统计，是评估缓存效果的核心指标

> **本章对应演示代码**：`LinkedHashMapLRUDemo.java`（LRU 工作流程可视化）、`DatabaseQueryCache.java`（生产级 LRU 缓存实现）

**继续阅读**：[05_LinkedHashMap最佳实践与性能分析.md](./05_LinkedHashMap最佳实践与性能分析.md)
