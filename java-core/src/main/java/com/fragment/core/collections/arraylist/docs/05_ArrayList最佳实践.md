# 第五章：ArrayList 最佳实践

## 5.1 初始化时指定容量

```java
// ❌ 数量可预估却不指定，触发多次扩容
List<User> users = new ArrayList<>();
for (int i = 0; i < 10000; i++) users.add(loadUser(i));

// ✅ 指定预估容量，0 次扩容
List<User> users = new ArrayList<>(10000);
for (int i = 0; i < 10000; i++) users.add(loadUser(i));

// ✅ 从已有集合构建时，用 Collection 构造函数
List<String> copy = new ArrayList<>(sourceList);

// ✅ 数量未知但有上限时，用上限值
List<String> results = new ArrayList<>(maxResults);
```

## 5.2 用接口类型声明变量

```java
// ❌ 耦合具体实现，未来难以切换
ArrayList<String> list = new ArrayList<>();

// ✅ 面向接口，切换实现只改一处
List<String> list = new ArrayList<>();
// 需要线程安全时：
List<String> list = new CopyOnWriteArrayList<>();
// 需要不可变时：
List<String> list = List.of("A", "B", "C");
```

## 5.3 批量删除用 removeIf

```java
// ❌ 循环内 remove(index)，O(n²)，且索引处理容易出错
for (int i = 0; i < list.size(); i++) {
    if (list.get(i).isExpired()) {
        list.remove(i--);
    }
}

// ✅ removeIf：O(n)，清晰简洁
list.removeIf(item -> item.isExpired());
```

## 5.4 转数组时用 toArray(T[])

```java
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));

// ❌ toArray() 返回 Object[]，需要强转，有 ClassCastException 风险
Object[] arr1 = list.toArray();

// ✅ toArray(T[]) 返回正确类型
String[] arr2 = list.toArray(new String[0]);
// 传 new String[0]（而非 new String[list.size()]）：JVM 可以优化分配
```

## 5.5 不可变列表的正确创建

```java
// JDK 9+ 首选：List.of()（真正不可变，任何修改抛 UnsupportedOperationException）
List<String> immutable = List.of("A", "B", "C");

// Collections.unmodifiableList()：包装层，但原 list 修改仍会反映
List<String> mutable = new ArrayList<>(Arrays.asList("A", "B", "C"));
List<String> wrapped = Collections.unmodifiableList(mutable);
mutable.add("D");
System.out.println(wrapped);  // [A, B, C, D]！wrapped 也变了

// Arrays.asList()：固定大小，可以 set，不能 add/remove
List<String> fixed = Arrays.asList("A", "B", "C");
fixed.set(0, "X");  // ✅ 可以
fixed.add("D");     // ❌ UnsupportedOperationException
```

## 5.6 Stream 与 ArrayList 配合

```java
// 收集 Stream 结果到 ArrayList
List<String> result = stream.collect(Collectors.toList());
// JDK 16+ 更简洁：
List<String> result = stream.toList();  // 返回不可变列表

// 需要可变列表时：
List<String> mutable = stream.collect(Collectors.toCollection(ArrayList::new));

// StudentGradeManager.java 中的完整模式：
List<Student> topStudents = students.stream()
    .filter(s -> s.getScore() >= 90)
    .sorted(Comparator.comparingDouble(Student::getScore).reversed())
    .collect(Collectors.toList());
```

## 5.7 本章总结

**六条核心实践**：
1. **预估容量**：数量可知时 `new ArrayList<>(n)`，避免扩容开销
2. **面向接口**：声明为 `List<T>`，而非 `ArrayList<T>`
3. **批量删除**：用 `removeIf`，不要循环内 `remove(index)`
4. **转数组**：用 `toArray(new T[0])`
5. **不可变列表**：JDK 9+ 用 `List.of()`，注意与 `Arrays.asList` 的区别
6. **Stream 收集**：`Collectors.toList()` 或 JDK 16+ `stream.toList()`

> **本章对应演示代码**：`StudentGradeManager.java`（完整业务场景）、`EventBus.java`（CopyOnWriteArrayList 实战）

**返回目录**：[README.md](../README.md)
