# 第五章：HashMap 最佳实践与性能优化

## 5.1 初始容量：最常被忽视的性能点

扩容（`resize()`）是 HashMap 中最重的操作：分配 2 倍新数组 + 遍历所有节点重新分桶，O(n) 时间复杂度。如果能在构造时预估容量，彻底避免扩容，是最简单的性能优化。

```java
// HashMapResizeDemo.java → resizePerformance()
// 插入 10 万条数据的性能对比（实测）：
// 使用默认容量（频繁扩容 16→32→...→131072）：约 2x ms
// 预设足够容量（不扩容）：约 1x ms
Map<Integer, String> noResize = new HashMap<>(150000);  // 10万 / 0.75 + 1 ≈ 133334，向上取 2 的幂 = 262144
```

### 正确计算初始容量

```java
// 公式：initialCapacity = expectedSize / loadFactor + 1
int expectedSize = 1000;
int initialCapacity = (int) (expectedSize / 0.75f + 1);  // = 1335
// HashMap 内部会用 tableSizeFor(1335) 向上取到 2048
// threshold = 2048 × 0.75 = 1536 > 1000，全程无扩容 ✅

// 错误写法：
new HashMap<>(1000);
// tableSizeFor(1000) = 1024，threshold = 1024 × 0.75 = 768
// 放入 769 个元素时就扩容 ❌
```

**Guava 的便捷方法**：`Maps.newHashMapWithExpectedSize(n)` 内部封装了这个公式。

---

## 5.2 hashCode 设计：决定 HashMap 性能上限

HashMap 的 O(1) 性能依赖于 hash 分布均匀。**hashCode 分布越均匀，碰撞越少，性能越好**。

### 正确实现 hashCode

```java
// 反面教材：HashMapCollisionDemo.java → BadHashKey
@Override
public int hashCode() {
    return 1;  // 所有对象同一个桶，链表/树退化，O(n) 或 O(log n)
}

// 正确示范：使用所有字段、质数乘法
@Override
public int hashCode() {
    return Objects.hash(field1, field2, field3);  // JDK 内置，基于 31 的多项式
}

// 或者手写（性能稍好）：
@Override
public int hashCode() {
    int result = field1.hashCode();
    result = 31 * result + field2.hashCode();
    result = 31 * result + field3;
    return result;
}
```

**为什么选 31？**  
- 是奇质数，减少 hash 碰撞
- `31 * x == (x << 5) - x`，JVM 可以优化为移位操作
- String 的 hashCode 也使用 31

### 用作 Map key 的类型必须同时重写 hashCode 和 equals

```java
// 这是最常见的 HashMap 陷阱之一
class User {
    String name; int age;
    // 只重写了 equals，没有重写 hashCode
    @Override
    public boolean equals(Object o) { ... }
    // hashCode 使用默认的 Object.hashCode()（基于对象地址）
}

Map<User, String> map = new HashMap<>();
User u1 = new User("张三", 20);
map.put(u1, "员工");

User u2 = new User("张三", 20);  // equals(u1) == true
map.get(u2);  // 返回 null！因为 u1.hashCode() ≠ u2.hashCode()，找到了不同的桶
```

**HashMapCollisionDemo.java → customObjectCollision()** 演示了 `Person` 类只依赖 `age` 计算 hashCode 时产生的冲突。

---

## 5.3 遍历方式的正确选择

```java
// HashMapBasicDemo.java → iterationMethods() 演示了 5 种遍历方式

// ✅ 推荐：entrySet + forEach（JDK 8）
map.forEach((key, value) -> System.out.println(key + " -> " + value));

// ✅ 同等性能：entrySet 迭代器（需要遍历中删除元素时必须用这个）
Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
while (it.hasNext()) {
    Map.Entry<String, Integer> entry = it.next();
    if (needRemove(entry)) it.remove();  // 安全删除，不会 ConcurrentModificationException
}

// ❌ 避免：keySet + get（两次 hash 查找）
for (String key : map.keySet()) {
    Integer value = map.get(key);  // 多余的 hash 查找
}

// ✅ 仅需 value 时：values()
for (Integer value : map.values()) { ... }
```

---

## 5.4 JDK 8 函数式方法的最佳实践

**`getOrDefault`：替代 null 判断**
```java
// 旧写法
Integer count = map.get(key);
if (count == null) count = 0;

// 新写法
int count = map.getOrDefault(key, 0);
```

**`computeIfAbsent`：分组/多值 Map 的标准写法**
```java
// 按部门分组员工（标准写法）
Map<String, List<Employee>> byDept = new HashMap<>();
employees.forEach(e ->
    byDept.computeIfAbsent(e.getDept(), k -> new ArrayList<>()).add(e)
);
```

**`merge`：计数/累加的最优写法**
```java
// 词频统计
Map<String, Integer> freq = new HashMap<>();
for (String word : words) {
    freq.merge(word, 1, Integer::sum);
    // 等价于：freq.put(word, freq.getOrDefault(word, 0) + 1)
    // 但 merge 只做一次 hash 查找
}
```

**`putIfAbsent` vs `computeIfAbsent` 的区别**
```java
// putIfAbsent：value 表达式在调用前就已经计算（即使 key 存在也会计算！）
map.putIfAbsent(key, new ArrayList<>());  // 即使 key 存在，也会创建 ArrayList

// computeIfAbsent：只在 key 不存在时才计算（惰性求值）
map.computeIfAbsent(key, k -> new ArrayList<>());  // key 存在时不创建对象
```

---

## 5.5 实战项目中的使用模式

### 模式一：缓存（UserSessionManager 的核心逻辑）

```java
// UserSessionManager.java 中的会话管理
// 核心是 ConcurrentHashMap 的原子方法替代 if + put 组合
sessions.put(sessionId, session);          // 创建
Session s = sessions.get(sessionId);       // 查询
sessions.remove(sessionId);               // 删除
```

**关键设计**：后台线程定期清理过期会话（`cleanupExpiredSessions()`），避免 OOM。

### 模式二：频率统计

```java
// 统计 API 接口的调用次数
Map<String, AtomicInteger> apiCallCount = new ConcurrentHashMap<>();
apiCallCount.computeIfAbsent(apiPath, k -> new AtomicInteger(0)).incrementAndGet();
// AtomicInteger 保证计数本身的原子性，computeIfAbsent 保证初始化的原子性
```

### 模式三：索引构建

```java
// 批量查询时，先把 DB 结果转成 Map，避免 N+1 查询
List<User> users = userDao.queryByIds(ids);
Map<Long, User> userIndex = new HashMap<>(users.size() * 2);  // 预设容量
users.forEach(u -> userIndex.put(u.getId(), u));

// 后续关联查询 O(1)，而不是 O(n)
orders.forEach(o -> o.setUser(userIndex.get(o.getUserId())));
```

---

## 5.6 常见陷阱总结

| 陷阱 | 问题 | 正确做法 |
|------|------|---------|
| 未设初始容量 | 频繁扩容 | `(int)(size / 0.75 + 1)` |
| 只重写 equals 未重写 hashCode | get 返回 null | 两者必须同时重写 |
| 用可变对象做 key | key 变化后找不到节点 | key 必须是不可变对象 |
| keySet + get 遍历 | 两次 hash 查找 | 用 entrySet 或 forEach |
| 遍历时直接 remove | ConcurrentModificationException | 用 Iterator.remove() |
| 多线程用 HashMap | 数据丢失 | 用 ConcurrentHashMap |
| putIfAbsent 传入复杂对象 | 对象总是被创建 | 用 computeIfAbsent |

---

## 5.7 本章总结

- **初始容量**：`(int)(expectedSize / 0.75 + 1)` 是正确公式，预设容量避免扩容
- **hashCode 质量**：直接决定碰撞率，用 `Objects.hash()` 或基于 31 的多项式
- **equals + hashCode 必须同时重写**：HashMap 的基础契约
- **遍历用 entrySet/forEach**：避免 keySet + get 的双重查找
- **函数式方法**：`merge`/`computeIfAbsent` 比手动 get+put 更简洁且减少查找次数
- **多线程**：ConcurrentHashMap；单线程：HashMap + 合理初始容量

> **本章对应演示代码**：`HashMapBasicDemo.java`（遍历方式与函数式方法）、`HashMapResizeDemo.java`（初始容量与扩容性能）、`UserSessionManager.java`（实战缓存模式）

**返回目录**：[README.md](../../README.md)
