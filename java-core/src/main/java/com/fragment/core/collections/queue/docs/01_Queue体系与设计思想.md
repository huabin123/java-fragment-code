# 第一章：Queue 体系与设计思想

## 1.1 为什么需要队列？

在并发编程和日常业务开发中，我们经常遇到一个核心矛盾：**生产数据的速度和消费数据的速度不匹配**。

思考这样一个场景：电商系统在双 11 促销期间，每秒涌入 10 万笔下单请求，而库存扣减、支付处理等后端服务每秒只能处理 2 万笔。如果直接用线程池接收请求，线程数暴涨、内存耗尽，系统崩溃。

**队列就是解决这个矛盾的缓冲层**：

```
生产者（快）→ [队列缓冲] → 消费者（慢）
```

队列带来了三个核心价值：

1. **解耦**：生产者和消费者互不依赖，可以独立扩缩容
2. **削峰填谷**：瞬时流量由队列吸收，消费者匀速处理
3. **异步化**：生产者写入队列后立即返回，无需等待消费者处理完成

---

## 1.2 Java Queue 体系全景

Java 的队列体系经过多个版本的演进，形成了一棵完整的接口-实现树：

```
Iterable
  └── Collection
        └── Queue（基础队列接口）
              ├── Deque（双端队列接口）
              │     ├── ArrayDeque      ← 数组循环双端队列，推荐替代 Stack 和 LinkedList
              │     └── LinkedList      ← 链表双端队列（同时实现 List）
              │
              ├── PriorityQueue         ← 最小堆，按优先级出队
              │
              └── BlockingQueue（阻塞队列接口，在 java.util.concurrent 包）
                    ├── ArrayBlockingQueue     ← 有界，单锁
                    ├── LinkedBlockingQueue    ← 可选有界，双锁（读写分离）
                    ├── PriorityBlockingQueue  ← 无界，优先级阻塞
                    ├── SynchronousQueue       ← 零容量，线程间直接握手
                    ├── DelayQueue             ← 延迟到期才可出队
                    └── LinkedTransferQueue    ← 融合 SynchronousQueue 和 LinkedBlockingQueue

ConcurrentLinkedQueue   ← 实现 Queue 接口，无锁 CAS，非阻塞
```

这棵树的设计体现了一个重要的**接口分层原则**：
- `Queue` 定义基础语义（FIFO + 基础操作）
- `Deque` 在 `Queue` 基础上扩展（两端均可操作）
- `BlockingQueue` 在 `Queue` 基础上增加阻塞语义（线程协调）

不同层次的接口解决不同层次的问题，实现类只需选择合适的接口实现即可。

---

## 1.3 Queue 接口的方法设计哲学

Queue 接口为每种操作提供了**两套方法**，这是一个非常重要的设计决策：

| 操作 | 抛异常版 | 返回特殊值版 |
|------|---------|------------|
| 入队 | `add(e)` | `offer(e)` |
| 出队 | `remove()` | `poll()` |
| 查看队头 | `element()` | `peek()` |

**为什么要设计两套方法？**

这背后的考量是：**异常是昂贵的**。在高频操作场景（如每秒数万次的入队/出队），如果队列状态不满足（满/空）是一种"正常的业务状态"而非"程序错误"，那么用异常来表达这种状态会产生巨大的性能开销（创建异常对象、填充栈帧、抛出和捕获）。

```
// 演示代码见 ArrayDequeDemo.java → methodComparison() 方法

// ❌ 用抛异常方法处理"队列可能为空"的正常情况
try {
    String item = deque.remove();  // 空时抛 NoSuchElementException
    process(item);
} catch (NoSuchElementException e) {
    // 这只是队列空了，不是程序错误，但却用了昂贵的异常机制
}

// ✅ 用返回特殊值的方法
String item;
while ((item = deque.poll()) != null) {  // 空时返回 null，简洁高效
    process(item);
}
```

> **实践建议**：优先使用 `offer/poll/peek` 这组方法。`add/remove/element` 适合在"队列满/空代表程序逻辑错误"的场景下使用，错误应该被快速发现而不是被静默忽略。

---

## 1.4 FIFO、LIFO 与优先级：三种基本排队策略

队列的本质是**等待序列的管理策略**。Java 提供了三种策略：

### FIFO（先进先出）
最朴素的排队策略。`ArrayDeque`、`LinkedBlockingQueue` 等均遵循此策略。
适合：消息处理、请求排队、打印任务等"公平处理"的场景。

### LIFO（后进先出）
即栈结构。`ArrayDeque` 通过 `push/pop` 实现栈语义。
适合：函数调用栈、撤销操作、括号匹配、DFS 遍历等场景。

### 优先级（按权重出队）
`PriorityQueue` 实现。不管入队顺序，始终优先处理权重最高（最小值）的元素。
适合：任务调度、紧急告警、Dijkstra 最短路径等场景。

---

## 1.5 阻塞语义：从 Queue 到 BlockingQueue 的进化

`Queue` 接口的方法在队列满/空时只有两种选择：抛异常或返回 `null/false`。这意味着调用者必须自己处理"等待"逻辑，通常要写类似这样的代码：

```java
// 没有 BlockingQueue 时，手动实现等待（繁琐且容易出错）
while (queue.isEmpty()) {
    Thread.sleep(10);  // 轮询等待，浪费 CPU
}
String item = queue.poll();
```

`BlockingQueue` 通过增加 `put(e)` 和 `take()` 方法，将"等待"这个语义内化到队列本身：

```java
// 有了 BlockingQueue，消费者代码极度简洁
String item = queue.take();  // 队列为空时自动挂起，有数据时自动唤醒
```

这不只是便利性的提升，更是**并发正确性**的保证。手写的等待逻辑容易产生竞态条件，而 `BlockingQueue` 的实现经过严格验证，内部通过 `ReentrantLock + Condition` 实现精确的线程挂起和唤醒，零 CPU 浪费。

`BlockingQueue` 的方法分为四组（详见 `BlockingQueueDemo.java` 文件头注释）：

| 操作 | 抛异常 | 返特殊值 | 一直阻塞 | 超时阻塞 |
|------|--------|---------|---------|---------|
| 入队 | `add(e)` | `offer(e)` | `put(e)` | `offer(e, time, unit)` |
| 出队 | `remove()` | `poll()` | `take()` | `poll(time, unit)` |
| 查看 | `element()` | `peek()` | — | — |

`put/take` 是生产者-消费者模式的核心方法。超时版本 `offer(e, time, unit)` / `poll(time, unit)` 则在需要"等但不要等太久"的场景下使用，防止死等。

---

## 1.6 各实现的适用场景快速选型

| 场景 | 推荐实现 | 原因 |
|------|---------|------|
| 单线程栈或队列 | `ArrayDeque` | 无锁，无内存分配开销，性能最佳 |
| 单线程优先级处理 | `PriorityQueue` | 最小堆，O(log n) 出队 |
| 生产者-消费者（高吞吐） | `LinkedBlockingQueue`（指定容量） | 读写双锁，并发吞吐高 |
| 生产者-消费者（严格限流） | `ArrayBlockingQueue` | 固定容量，单锁，内存可预估 |
| 多线程优先级任务 | `PriorityBlockingQueue` | 优先级 + 线程安全 |
| 线程间直接一对一传递 | `SynchronousQueue` | 零缓冲，强迫同步 |
| 延迟/定时任务 | `DelayQueue` | 按到期时间自动排序 |
| 高并发非阻塞写入 | `ConcurrentLinkedQueue` | CAS 无锁，非阻塞 |

> **本章对应演示代码**：`demo/ArrayDequeDemo.java`、`demo/BlockingQueueDemo.java`
