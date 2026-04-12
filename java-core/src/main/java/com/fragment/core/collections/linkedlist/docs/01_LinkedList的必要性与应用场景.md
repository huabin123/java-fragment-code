# 第一章：LinkedList 的必要性与应用场景

## 1.1 核心问题：ArrayList 解决不了什么？

LinkedList 的存在意义，必须从 ArrayList 的局限性说起。ArrayList 是基于**动态数组**实现的，数组有一个结构性约束：**元素在内存中连续排列**。

这个特性在随机访问时是优势（`O(1)` 下标寻址），但在头部/中间插入或删除时变成了负担：

```
// 在 index=2 处插入新元素：
[A][B][C][D][E]
         ↑ 需要把 C、D、E 全部向右移动一格
[A][B][新][C][D][E]
```

移动的元素数量与位置相关，**平均移动 n/2 个元素**，时间复杂度 O(n)。

`LinkedListPerformanceDemo.java → testHeadInsert()` 量化了这个差距：在头部连续插入 10000 个元素时，ArrayList 需要移动大量数据，LinkedList 通过修改指针完成，**性能差距可达数十倍**。

---

## 1.2 双向链表的设计：为什么选择"双向"？

LinkedList 底层是**双向链表**，每个节点有三个字段：

```
null ← [prev | item | next] ↔ [prev | item | next] ↔ [prev | item | next] → null
        ↑ first                                          ↑ last
```

之所以选择双向而不是单向，有两个关键原因：

**原因一：O(1) 的尾部操作**  
LinkedList 持有 `first` 和 `last` 两个指针。有了 `last`，`addLast`/`removeLast` 直接操作，不需要从头遍历到尾。单向链表要找尾部需要 O(n)。

**原因二：实现 Deque 接口**  
LinkedList 同时实现了 `Deque`（双端队列），需要支持从两端高效地添加和删除。双向链表天然满足这个需求，而单向链表只能从一端高效操作。

```java
// LinkedListBasicDemo.java → headAndTailOperations()
linkedList.addFirst("头部");  // O(1)：修改 first 指针
linkedList.addLast("尾部");   // O(1)：修改 last 指针
linkedList.removeFirst();     // O(1)：更新 first = first.next
linkedList.removeLast();      // O(1)：更新 last = last.prev（单向链表做不到！）
```

---

## 1.3 LinkedList 实现的两个接口：List 和 Deque

`LinkedList<E> extends AbstractSequentialList<E> implements List<E>, Deque<E>`

这是一个有趣的设计决定：LinkedList **同时扮演两个角色**。

| 角色 | 接口 | 典型方法 | 时间复杂度 |
|------|------|---------|-----------|
| 线性列表 | `List<E>` | `get(i)`, `add(i, e)`, `remove(i)` | O(n) 按索引 |
| 双端队列 | `Deque<E>` | `addFirst`, `addLast`, `pollFirst`, `pollLast` | O(1) |

作为 List 时，按索引访问 `get(i)` 需要从 first 或 last 遍历（取两端较近的那个），时间复杂度 O(n)。作为 Deque 时，两端操作都是 O(1)。

> **重要结论**：LinkedList 作为 Deque/Queue 使用时性能优秀；作为 List 使用时（尤其是随机访问），性能远不如 ArrayList。

---

## 1.4 三种典型使用场景

### 场景一：作为队列（FIFO）

队列的核心是"先进先出"：从尾部入队，从头部出队。LinkedList 的两端都是 O(1)，天然契合。

```java
// LinkedListAsQueueDemo.java → basicQueueOperations()
Queue<String> queue = new LinkedList<>();
queue.offer("任务1");  // 入队（尾部），O(1)
queue.offer("任务2");
queue.poll();          // 出队（头部），O(1) — 返回并删除
queue.peek();          // 查看队首，O(1) — 不删除
```

**注意**：现代 Java 代码中，纯队列场景更推荐 `ArrayDeque`（内存连续，缓存更友好）。LinkedList 适合需要同时用 List 接口特性的场景。

### 场景二：作为栈（LIFO）

```java
// LinkedListBasicDemo.java → useAsDeque()
LinkedList<String> stack = new LinkedList<>();
stack.push("C");   // 等价于 addFirst，压栈到头部
stack.push("B");
stack.push("A");
stack.pop();       // 等价于 removeFirst，从头部弹出 → "A"
stack.peek();      // 查看栈顶 → "B"，不删除
```

### 场景三：浏览器历史记录（双端操作）

`BrowserHistoryManager.java` 展示了 LinkedList 处理"历史记录 + 前进/后退"的完整逻辑：

```java
// BrowserHistoryManager.java
private final LinkedList<String> history = new LinkedList<>();

void visit(String url) {
    // 访问新页面时，清除当前位置之后的前进历史
    while (history.size() > currentIndex + 1) {
        history.removeLast();  // O(1)
    }
    history.addLast(url);      // O(1)
    currentIndex++;
    if (history.size() > maxSize) {
        history.removeFirst(); // O(1)，淘汰最老的记录
        currentIndex--;
    }
}
```

这个场景中，频繁地对链表两端进行添加/删除，正好发挥了 LinkedList 双向链表 O(1) 端操作的优势。

---

## 1.5 LinkedList 不适合的场景

理解什么时候**不该用** LinkedList 同样重要：

**不适合随机访问**  
`list.get(index)` 需要从 first 或 last 遍历，O(n)。`LinkedListPerformanceDemo.java → testRandomAccess()` 显示 ArrayList 随机访问比 LinkedList 快 **数百倍**。

**不适合按索引插入中间**  
虽然修改指针本身是 O(1)，但找到"第 i 个节点"需要遍历 O(n)。加上 LinkedList 节点对象的内存开销和缓存不友好，中间插入性能实测往往不如 ArrayList：

```
LinkedListPerformanceDemo.java → testMiddleInsert()
// ArrayList 中间插入（有数据移动，但 CPU 缓存命中率高）：x ms
// LinkedList 中间插入（无数据移动，但每次需要遍历到中间位置）：往往 > ArrayList
```

**不适合内存受限场景**  
每个节点除了数据本身，还需要 `prev`、`next` 两个指针各占 8 字节（64位JVM）。存储 100 万个 Integer 时，LinkedList 的节点开销约为 ArrayList 的 3 倍。

---

## 1.6 选型决策树

```
需要频繁在 头部/尾部 操作？
    是 → 操作侧重双端？
              是 → ArrayDeque（更优，内存连续）
                   LinkedList 同样可以
          操作侧重头部或尾部之一？
              是 → LinkedList 或 ArrayDeque 均可
    否 → 主要是随机访问/遍历？
              是 → ArrayList（首选）
         主要是 中间 插入/删除？
              具体位置已知（有迭代器）→ LinkedList 有优势
              按索引插入 → ArrayList 通常更快（缓存友好）
```

---

## 1.7 本章总结

- **存在原因**：ArrayList 的数组结构使头部/中间插入需要 O(n) 数据移动，LinkedList 的双向链表结构使两端操作为 O(1)
- **"双向"的价值**：支持 O(1) 的 `removeLast`，以及实现 Deque 接口（单向链表做不到）
- **双重角色**：既是 List（随机访问 O(n)）也是 Deque（两端操作 O(1)），实际主要用作后者
- **反直觉的性能**：中间插入场景 LinkedList 实测常输于 ArrayList（缓存效应）
- **for 循环大忌**：`for (int i=0; i<list.size(); i++) list.get(i)` 是 O(n²)，LinkedList 必须用迭代器遍历

> **本章对应演示代码**：`LinkedListBasicDemo.java`（基本操作与双端操作）、`LinkedListPerformanceDemo.java`（与 ArrayList 全面对比）、`BrowserHistoryManager.java`（实战场景）

**继续阅读**：[02_LinkedList核心原理与数据结构.md](./02_LinkedList核心原理与数据结构.md)
