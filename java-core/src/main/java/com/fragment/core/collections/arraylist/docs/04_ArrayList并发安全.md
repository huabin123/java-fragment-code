# 第四章：ArrayList 并发安全

## 4.1 ArrayList 为什么线程不安全？

ArrayList 的 `add` 操作由两步组成，不是原子的：

```java
// ArrayList.add() 源码（简化）
public boolean add(E e) {
    ensureCapacityInternal(size + 1);  // 步骤1：检查/扩容
    elementData[size++] = e;           // 步骤2：赋值并递增 size
    // size++ 本身也是非原子的：读取 size → +1 → 写回
    return true;
}
```

**并发问题复现**（`ArrayListConcurrentDemo.java → demonstrateUnsafe()`）：

```
线程A 读取 size=5
线程B 读取 size=5
线程A 写入 elementData[5]=A，size=6
线程B 写入 elementData[5]=B，size=6  ← 覆盖了 A 写的值！丢失元素
```

10 个线程各写 1000 次，期望 10000，实际往往只有 9900+ 甚至更少。

---

## 4.2 三种线程安全方案

### 方案一：Collections.synchronizedList

```java
List<String> list = Collections.synchronizedList(new ArrayList<>());

// 每个单个操作（add/get/remove）都加 synchronized，线程安全
list.add("A");  // 安全
list.get(0);    // 安全

// ⚠️ 复合操作仍不安全：
if (!list.contains("A")) {  // check
    list.add("A");           // then act → check 和 act 之间可能被其他线程插入
}

// ✅ 复合操作需要手动加锁
synchronized (list) {
    if (!list.contains("A")) list.add("A");
}

// ⚠️ 遍历也需要手动加锁！
synchronized (list) {
    for (String s : list) { ... }  // 遍历期间加锁防止 ConcurrentModificationException
}
```

### 方案二：CopyOnWriteArrayList

```java
// EventBus.java 中使用了 CopyOnWriteArrayList 管理监听器
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();

// 写操作：加锁，复制整个数组，在新数组上修改，完成后替换引用
list.add("A");   // 写时复制，开销大

// 读操作：无锁，直接读当前快照
list.get(0);     // 无锁，极快
for (String s : list) { ... }  // 遍历快照，不会 ConcurrentModificationException
```

**CopyOnWriteArrayList 的设计权衡**：

| 维度 | 说明 |
|------|------|
| 读操作 | 无锁，性能极好 |
| 写操作 | 加锁 + 数组完整复制，开销与 size 成正比 |
| 遍历一致性 | 遍历的是写时创建的快照，期间的修改不可见 |
| 适合场景 | 监听器列表、路由规则、配置列表（读多写极少）|
| 不适合场景 | 高频写入（每次写都 O(n) 复制）|

### 方案三：局部变量避免共享

```java
// 最简单的方案：每个线程用自己的局部 ArrayList，最后合并
List<Future<List<Result>>> futures = new ArrayList<>();
for (int i = 0; i < threadCount; i++) {
    futures.add(executor.submit(() -> {
        List<Result> localResults = new ArrayList<>();  // 局部变量，无竞争
        // ... 处理逻辑
        return localResults;
    }));
}
// 最后合并所有线程的结果
List<Result> allResults = new ArrayList<>();
for (Future<List<Result>> f : futures) allResults.addAll(f.get());
```

---

## 4.3 fast-fail 机制：modCount

```java
// ArrayList 通过 modCount 检测并发修改
// 每次结构性修改（add/remove/clear 等）都会 modCount++
// Iterator 创建时记录 expectedModCount = modCount
// 每次 next() 调用都检查：modCount != expectedModCount → 抛出 ConcurrentModificationException

List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
for (String s : list) {
    if (s.equals("B")) list.remove(s);  // ❌ 遍历中修改结构 → CME
}

// ✅ 正确做法1：Iterator 的 remove()
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("B")) it.remove();  // Iterator 的 remove 不触发 CME
}

// ✅ 正确做法2：removeIf（内部用 BitSet 标记，一次遍历完成）
list.removeIf(s -> s.equals("B"));
```

---

## 4.4 本章总结

- **线程不安全根因**：`size++` 和 `elementData[size]=e` 两步非原子
- **synchronizedList**：每个操作加锁，复合操作和遍历需手动同步
- **CopyOnWriteArrayList**：写时复制，读无锁，适合读多写极少（监听器、配置）
- **局部变量**：最快方案，各线程独立处理后合并
- **fast-fail**：`modCount` 机制，遍历中结构修改抛 CME，用 `Iterator.remove()` 或 `removeIf` 解决

> **本章对应演示代码**：`ArrayListConcurrentDemo.java`（三种方案演示）、`EventBus.java`（CopyOnWriteArrayList 实战）

**继续阅读**：[05_ArrayList最佳实践.md](./05_ArrayList最佳实践.md)
