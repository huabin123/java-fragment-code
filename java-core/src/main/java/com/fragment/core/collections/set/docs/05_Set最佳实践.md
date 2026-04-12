# 第五章：Set 最佳实践

## 5.1 总是用接口类型声明变量

```java
// ❌ 耦合具体实现
HashSet<String> set = new HashSet<>();

// ✅ 面向接口
Set<String> set = new HashSet<>();
// 需要有序时只改一处：
Set<String> set = new TreeSet<>();
// 需要保持插入顺序：
Set<String> set = new LinkedHashSet<>();
```

## 5.2 自定义类必须正确实现 hashCode 和 equals

```java
// ✅ 用 Objects.hash() 和 IDE 生成
public class User {
    private final String id;
    private final String email;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User u = (User) o;
        return Objects.equals(id, u.id) && Objects.equals(email, u.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);  // 与 equals 一致
    }
}

// ✅ JDK 16+ record 自动生成 hashCode 和 equals
record Point(int x, int y) {}  // 自动正确实现，放心放入 Set
```

## 5.3 集合运算前先拷贝

```java
Set<String> A = new HashSet<>(Arrays.asList("a", "b", "c"));
Set<String> B = new HashSet<>(Arrays.asList("b", "c", "d"));

// ❌ 直接在原集合上操作，破坏 A
A.retainAll(B);  // A 变成了 {b, c}，原来的 A 丢失了

// ✅ 先拷贝
Set<String> intersection = new HashSet<>(A);
intersection.retainAll(B);  // A 不变，intersection = {b, c}
```

## 5.4 Set 元素应使用不可变对象

```java
// ✅ 使用不可变类型：String、Integer、LocalDate、record 等
Set<String> tags = new HashSet<>();
tags.add("java");
tags.add("spring");

// ❌ 可变对象放入 Set 后不要修改参与 hashCode 计算的字段
Set<Point> points = new HashSet<>();
Point p = new Point(1, 1);
points.add(p);
p.x = 99;  // ⚠️ p 在 Set 中"失联"，既删不掉也找不到
```

## 5.5 并发场景使用 ConcurrentHashMap.newKeySet()

```java
// ❌ 非线程安全
Set<String> set = new HashSet<>();

// ❌ 粗粒度锁，并发性差
Set<String> set = Collections.synchronizedSet(new HashSet<>());

// ✅ 细粒度锁，高并发性能好
Set<String> set = ConcurrentHashMap.newKeySet();

// 也可以给已有 ConcurrentHashMap 创建 keySet 视图
ConcurrentHashMap<String, Boolean> map = new ConcurrentHashMap<>();
Set<String> keySetView = map.keySet(Boolean.TRUE);
```

## 5.6 创建不可变 Set

```java
// JDK 9+ Set.of()：真正不可变，任何修改抛 UnsupportedOperationException
Set<String> immutable = Set.of("a", "b", "c");
// ⚠️ Set.of() 不允许重复元素，否则抛 IllegalArgumentException
// ⚠️ Set.of() 不允许 null 元素

// Collections.unmodifiableSet()：包装层，原 Set 修改仍会反映
Set<String> mutable = new HashSet<>(Arrays.asList("a", "b"));
Set<String> wrapped = Collections.unmodifiableSet(mutable);
mutable.add("c");
System.out.println(wrapped);  // [a, b, c]！wrapped 也变了
```

## 5.7 本章总结

**六条核心实践**：
1. **面向接口声明**：`Set<T>` 而非 `HashSet<T>`
2. **正确实现 hashCode/equals**：用 `Objects.hash()`，或用 `record`
3. **集合运算前先拷贝**：`addAll/retainAll/removeAll` 会修改调用者
4. **Set 元素用不可变对象**：修改可变元素的 hashCode 字段导致"失联"
5. **并发用 `ConcurrentHashMap.newKeySet()`**：比 `synchronizedSet` 并发度高
6. **不可变 Set 用 `Set.of()`**（JDK 9+）：比 `unmodifiableSet` 更彻底

> **本章对应演示代码**：`TagSystem.java`（标签去重、交集查询、Jaccard 相似度）、`AccessControlList.java`（RBAC 权限控制）

**返回目录**：[README.md](../README.md)
