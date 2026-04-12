# 第五章：LinkedHashMap 最佳实践与性能分析

## 5.1 两种模式的使用规范

### 插入顺序模式（accessOrder=false，默认）

```java
// ✅ 标准用法：需要维护处理/插入顺序时
Map<String, String> config = new LinkedHashMap<>();
config.put("server.host", "localhost");
config.put("server.port", "8080");
config.put("app.name", "MyApp");
// 遍历顺序：server.host → server.port → app.name（插入顺序）

// ✅ 作为有序结果容器（如 SQL 查询结果按列名有序）
Map<String, Object> row = new LinkedHashMap<>();
row.put("id", 1);
row.put("name", "张三");
row.put("age", 30);
// JSON 序列化后字段顺序固定：{"id":1,"name":"张三","age":30}
```

**ConfigurationManager.java 的核心价值**：`saveToFile` 时配置项按添加顺序写出，配置文件结构清晰、可读性强。

### 访问顺序模式（accessOrder=true）

```java
// ✅ 仅用于 LRU 缓存场景
Map<K, V> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > MAX_SIZE;
    }
};

// ⚠️ 注意：accessOrder=true 时，get 操作会修改结构（modCount++）
// 不要在遍历中夹杂 get 操作：
for (Map.Entry<K, V> entry : lruCache.entrySet()) {
    lruCache.get(entry.getKey());  // ❌ ConcurrentModificationException！
}
```

---

## 5.2 性能特征

### 与 HashMap 的开销对比

| 操作 | HashMap | LinkedHashMap 插入顺序 | LinkedHashMap 访问顺序 |
|------|---------|---------------------|---------------------|
| put（新 key） | O(1) | O(1) + 链表追加 | O(1) + 链表追加 |
| put（更新 key）| O(1) | O(1)（不移动）| O(1) + 链表移位 |
| get | O(1) | O(1)（不修改）| O(1) + 链表移位 |
| remove | O(1) | O(1) + 链表摘出 | O(1) + 链表摘出 |
| 遍历 | O(capacity + n) | O(n) | O(n) |
| 内存 | 基准 | +8 字节/节点 | +8 字节/节点 |

**遍历效率可能优于 HashMap**：当 HashMap 的 loadFactor 很小（大量空桶）时，其迭代器需要跳过所有空桶（O(capacity)），而 LinkedHashMap 始终沿链表 O(n)。

### accessOrder=true 的隐藏开销

每次 `get` 触发 `afterNodeAccess`：

```
modCount++（会使迭代器失效）
从链表中摘出节点（3次指针操作）
追加到链表尾部（3次指针操作）
```

在高并发读场景下，这个开销对性能影响显著——这也是为什么**生产级高并发 LRU 不用 LinkedHashMap**。

---

## 5.3 遍历的正确姿势

```java
// LinkedHashMapBasicDemo.java 中演示了以下遍历方式

// ✅ entrySet + forEach（推荐，简洁）
map.forEach((k, v) -> System.out.println(k + "=" + v));

// ✅ entrySet 迭代器（需要遍历中删除时）
Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
while (it.hasNext()) {
    Map.Entry<String, Integer> entry = it.next();
    if (shouldRemove(entry)) it.remove();  // 安全删除
}

// ✅ 只需 key 时
for (String key : map.keySet()) { ... }

// ✅ 只需 value 时
for (Integer value : map.values()) { ... }

// ❌ accessOrder=true 时，遍历中不能调 get
// ❌ 遍历中直接调 map.remove()（会抛 CME）
```

---

## 5.4 常见陷阱

### 陷阱一：accessOrder=true 下遍历时调 get

```java
Map<String, Integer> lru = new LinkedHashMap<>(16, 0.75f, true);
lru.put("A", 1); lru.put("B", 2);

// ❌ ConcurrentModificationException
for (Map.Entry<String, Integer> entry : lru.entrySet()) {
    lru.get(entry.getKey());  // get 修改了 modCount，迭代器检测到异常
}

// ✅ 如果需要遍历并刷新顺序，先收集 key，再逐一 get
List<String> keys = new ArrayList<>(lru.keySet());
for (String key : keys) {
    lru.get(key);  // 安全，不在遍历 lru 本身
}
```

### 陷阱二：认为 put 更新 key 会改变插入顺序模式下的位置

```java
Map<String, Integer> map = new LinkedHashMap<>();  // 插入顺序模式
map.put("A", 1);
map.put("B", 2);
map.put("A", 99);  // 更新 A 的值

// 遍历：A=99, B=2  ← A 仍然在第一位！
// 错误预期：B=2, A=99（以为 A 被"重新插入"到末尾）
```

### 陷阱三：认为 LinkedHashMap LRU 是线程安全的

```java
// ❌ 多线程下不安全（accessOrder=true 的 get 修改结构）
Map<String, String> lru = new LinkedHashMap<>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<K,V> e) { return size() > 100; }
};

// ✅ 单线程/低并发：外部 synchronized
Map<String, String> syncLru = Collections.synchronizedMap(lru);

// ✅ 高并发：Caffeine
Cache<String, String> caffeineCache = Caffeine.newBuilder().maximumSize(100).build();
```

### 陷阱四：removeEldestEntry 中修改 map

```java
// ❌ 不要在 removeEldestEntry 内部调用 put/remove 等方法
protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    if (size() > MAX_SIZE) {
        remove(eldest.getKey());  // ❌ 会引发 ConcurrentModificationException 或死循环
        return false;
    }
    return false;
}

// ✅ 只需返回 true，LinkedHashMap 内部会自动完成删除
protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > MAX_SIZE;  // 返回 true，让框架删除
}
```

---

## 5.5 选型决策

```
需要 Map 并且...

需要按 key 升序排列？
    是 → TreeMap

需要保持插入/访问顺序？
    是 → LinkedHashMap
         需要 LRU 驱逐？
              是 → LinkedHashMap(accessOrder=true) + removeEldestEntry
                   高并发？
                        是 → Caffeine/Guava Cache
                        否 → LinkedHashMap（外部 synchronized）
              否 → LinkedHashMap(accessOrder=false，默认)
    否 → HashMap（默认选择，性能最优）

需要线程安全的普通 Map？
    → ConcurrentHashMap
```

---

## 5.6 实战场景总结

| 场景 | 推荐用法 | 对应代码 |
|------|---------|---------|
| 配置管理（维护声明顺序）| `new LinkedHashMap<>()` | `ConfigurationManager.java` |
| JSON 字段顺序固定 | `new LinkedHashMap<>()` | 任意插入顺序模式使用 |
| 单线程 LRU 缓存 | `new LinkedHashMap<>(n, 0.75f, true)` + `removeEldestEntry` | `DatabaseQueryCache.java` |
| 高并发 LRU 缓存 | Caffeine | — |
| 有顺序要求的数据库结果集 | `new LinkedHashMap<>()` | 按列顺序存 row 数据 |

---

## 5.7 本章总结

- **性能**：get/put 均 O(1)，比 HashMap 多 8 字节/节点和少量指针操作开销，遍历可能更快（O(n) vs O(capacity+n)）
- **accessOrder=true 的隐藏代价**：get 也修改结构（modCount++），多线程下比普通 HashMap 更危险
- **陷阱**：遍历中调 get（CME）、以为 put 更新会改变顺序（不会）、在 `removeEldestEntry` 内修改 map（危险）
- **线程安全**：单线程用 LinkedHashMap，低并发用 synchronizedMap，高并发用 Caffeine
- **LRU 正确实现**：`accessOrder=true` + `removeEldestEntry` 返回 `size() > MAX`，不要自己在回调中删除

> **本章对应演示代码**：`LinkedHashMapBasicDemo.java`（顺序特性与遍历）、`DatabaseQueryCache.java`（LRU 实战）、`ConfigurationManager.java`（插入顺序实战）

**返回目录**：[README.md](../../README.md)
