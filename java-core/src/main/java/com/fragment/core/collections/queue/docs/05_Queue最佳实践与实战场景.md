# 第五章：Queue 最佳实践与实战场景

> **对应实战代码**：`project/DelayTaskScheduler.java`、`project/ProducerConsumerPool.java`

## 5.1 生产者-消费者模式的正确实现

生产者-消费者模式是 `BlockingQueue` 最核心的使用场景，但有几个常见的实现错误。

### 5.1.1 优雅关闭的四步顺序

这是最容易出错的地方。`ProducerConsumerPool.java` 中的 `shutdown()` 方法展示了正确的关闭顺序：

```java
// 对应代码：ProducerConsumerPool.java → shutdown()

public void shutdown() throws InterruptedException {
    // Step 1：停止生产者（不再接受新任务）
    producerPool.shutdown();
    producerPool.awaitTermination(10, TimeUnit.SECONDS);

    // Step 2：标记生产者已停止（消费者据此判断何时退出）
    producerStopped = true;

    // Step 3：等待消费者处理完队列剩余消息
    consumerPool.shutdown();
    consumerPool.awaitTermination(10, TimeUnit.SECONDS);
}
```

**为什么顺序重要**？如果先关闭消费者，队列里的剩余消息会丢失；如果不等生产者完成就标记 `producerStopped = true`，消费者可能在生产者还在写入时就退出了。

### 5.1.2 消费者的退出信号设计

消费者如何知道"生产者已经不会再来数据了，可以退出了"？有两种常见方案：

**方案一：毒药消息（Poison Pill）**

生产者结束时向队列发送一个特殊的"毒药"对象，消费者收到后退出：

```java
static final Task POISON = new Task("__POISON__", -1, () -> {});

// 生产者结束时
queue.put(POISON);

// 消费者
while (true) {
    Task task = queue.take();
    if (task == POISON) break;  // 收到毒药，退出
    process(task);
}
```

优点：简单直接。缺点：每个消费者线程都需要收到一个毒药消息，多消费者时需要发送 N 个毒药。

**方案二：volatile 标志位 + 超时 poll**

`ProducerConsumerPool.java` 采用这种方式：

```java
// 对应代码：ProducerConsumerPool.java → startConsumers() 内部逻辑

private volatile boolean producerStopped = false;  // 生产者停止标志

// 消费者
while (true) {
    // 不用 take()，用带超时的 poll()，避免永久阻塞
    Message msg = queue.poll(200, TimeUnit.MILLISECONDS);
    if (msg == null) {
        // 超时未取到数据，检查退出条件
        if (producerStopped && queue.isEmpty()) {
            break;  // 生产者停了且队列空了，消费者安全退出
        }
        continue;  // 否则继续等
    }
    process(msg);
}
```

**为什么用 `poll(timeout)` 而不是 `take()`**？

`take()` 会一直阻塞，在生产者停止后，消费者永远收不到 `producerStopped = true` 的通知（线程挂起中，检查不到这个标志）。用 `poll(timeout)` 每隔 200ms 醒来检查一次退出条件，是两全其美的做法。

### 5.1.3 反压（Back Pressure）的实现

反压是指当消费速度跟不上生产速度时，**主动阻塞或拒绝生产者**，防止内存堆积。

```java
// 对应代码：ProducerConsumerPool.java → submitProducer() 内部

// put()：队满时阻塞生产者线程（天然的反压）
// 生产者不会比消费者快太多，内存消耗有上界
queue.put(msg);
```

这是使用有界 `BlockingQueue` + `put()` 的最大价值：**不需要额外编写限流逻辑，队列满了生产者自然慢下来**，整个系统形成自适应的流量控制。

对比无界队列（`LinkedBlockingQueue` 不指定容量）：生产者永远不会被阻塞，队列无限增长，最终 OOM。

---

## 5.2 延迟任务调度的设计思路

`DelayTaskScheduler.java` 展示了基于 `DelayQueue` 的延迟调度器，这套模式在实际项目中非常常见。

### 5.2.1 Delayed 接口的实现要点

```java
// 对应代码：DelayTaskScheduler.java → DelayedTask 内部类

static class DelayedTask implements Delayed {

    // 存储到期的绝对纳秒时间（不存延迟时长，存绝对时间点）
    private final long expireNanos;

    DelayedTask(int id, String name, long delayMs, Runnable action) {
        // 创建时记录到期时间 = 当前时间 + 延迟
        this.expireNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        // 返回剩余时间（负数表示已过期）
        long remainNanos = expireNanos - System.nanoTime();
        return unit.convert(remainNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        // 剩余时间越短，优先级越高（最先出队）
        long diff = this.getDelay(TimeUnit.NANOSECONDS)
                  - other.getDelay(TimeUnit.NANOSECONDS);
        return Long.compare(diff, 0);
    }
}
```

**为什么存绝对时间而非延迟时长**？

如果存"还需要等 30 秒"，随着时间流逝这个值会变化，每次调用 `getDelay()` 都需要知道"已经过了多久"。存绝对时间点则不需要，直接用 `expireNanos - System.nanoTime()` 就得到剩余时间，逻辑简单且精确。

**`System.nanoTime()` 而非 `System.currentTimeMillis()`**：

`nanoTime()` 返回的是单调递增的时间（从某个固定起点到现在的纳秒数），**不受系统时钟调整影响**，适合计算时间差。`currentTimeMillis()` 可能因为 NTP 对时等原因向前/向后跳动，用来计算延迟不可靠。

### 5.2.2 单工作线程的合理性

```java
// 对应代码：DelayTaskScheduler.java → start()

workerThread = new Thread(() -> {
    while (running || !queue.isEmpty()) {
        DelayedTask task = queue.take();  // 阻塞直到有任务到期
        task.action.run();                // 执行任务
    }
});
```

`DelayQueue` 内部已经处理了"等多久"的问题（`take()` 精确休眠到下一个任务到期），所以这里只需要一个工作线程在循环 `take()`。

**注意**：示例代码中工作线程直接执行 `task.action.run()`。如果任务执行时间较长，会阻塞后续任务的调度。生产环境中，工作线程应该只负责"触发"，把实际执行提交到独立的线程池：

```java
// 生产级改进
DelayedTask task = queue.take();
executor.submit(task.action);  // 提交到线程池异步执行，不阻塞调度线程
```

### 5.2.3 典型使用场景

```java
// 对应代码：DelayTaskScheduler.java → main()

scheduler.schedule("订单#1001 超时取消", 1000, () ->
    System.out.println("→ 订单#1001 已超时，执行取消逻辑"));

scheduler.schedule("Token#2001 过期清除", 500, () ->
    System.out.println("→ Token#2001 已过期，从缓存移除"));
```

这三种场景（订单超时、Token 过期、消息重试）都有一个共同特点：**某个动作需要在将来的某个时间点执行，且这个时间点是动态确定的**。`DelayQueue` 天然地支持这种模式，而不需要定时轮询数据库或缓存。

---

## 5.3 批量消费：drainTo 的正确使用

在日志收集、数据库批量写入等场景中，消费者每次处理一条消息的效率很低，批量处理可以显著提升吞吐量。

```java
// 对应代码：BlockingQueueDemo.java → linkedBlockingQueueDemo()

List<Integer> batch = new ArrayList<>();
// drainTo：一次性取出多条，只加一次 takeLock
int drained = queue.drainTo(batch, 3);
```

**适用场景**：

```java
// 异步日志批量写磁盘（伪代码）
BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>(10000);

// 消费者线程：攒够一批或等超时后批量写
while (true) {
    List<LogEntry> batch = new ArrayList<>(200);
    // 最多等 100ms，或者取到 200 条就批量写
    logQueue.drainTo(batch, 200);
    if (batch.isEmpty()) {
        Thread.sleep(100);
        continue;
    }
    diskWriter.writeBatch(batch);  // 一次 I/O 写入所有日志
}
```

`drainTo` 的效率优势在于：相比循环调用 `poll()` N 次（N 次加锁/解锁），`drainTo` 加一次锁就取出所有元素，锁的开销大幅降低。

---

## 5.4 PriorityQueue 在任务调度中的实战模式

### 5.4.1 多维度优先级排序

业务中的优先级往往不是单一维度，可以通过 `Comparator` 链实现多维度排序：

```java
// 对应代码：PriorityQueueDemo.java → objectOrdering()

// 主排序：优先级数字（小的先执行）
// 次排序：任务名称（优先级相同时按名称字母序）
PriorityQueue<Task> pq = new PriorityQueue<>(
    Comparator.comparingInt((Task t) -> t.priority)
              .thenComparing(t -> t.name)
);
```

实际项目中可以扩展为更复杂的规则：

```java
// 业务优先级 → 创建时间（越早越优先） → 任务 ID
Comparator.comparingInt((Task t) -> t.level)
          .thenComparingLong(t -> t.createTime)
          .thenComparingInt(t -> t.id)
```

### 5.4.2 堆在 Dijkstra 算法中的典型应用

理解了 `PriorityQueue` 的堆结构后，Dijkstra 最短路径算法就很好理解了：

```java
// 最短路径：图中从 start 到所有节点的最短距离
// 伪代码，说明堆的使用方式
PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
// [节点, 从start到该节点的当前最短距离]
pq.offer(new int[]{start, 0});

while (!pq.isEmpty()) {
    int[] curr = pq.poll();      // 取出当前距离最短的节点（堆顶）
    int node = curr[0], dist = curr[1];

    // 松弛相邻节点
    for (int[] neighbor : graph[node]) {
        int newDist = dist + neighbor[1];
        if (newDist < distances[neighbor[0]]) {
            distances[neighbor[0]] = newDist;
            pq.offer(new int[]{neighbor[0], newDist});
        }
    }
}
```

`PriorityQueue` 保证每次 `poll()` 取出的都是"当前已知最短距离"的节点，这正是 Dijkstra 算法贪心策略的实现基础。

---

## 5.5 高并发场景下的选型实战

### 5.5.1 ConcurrentLinkedQueue 的适用场景

```java
// 对应代码：ConcurrentLinkedQueueDemo.java → concurrentSafetyDemo()

// 10 个生产者线程并发写入，10 个消费者线程并发读取
// 消费者采用"有就取，没有就继续"的非阻塞策略
while (count < perThread && System.currentTimeMillis() < deadline) {
    Integer val = queue.poll();    // 非阻塞，取不到返回 null
    if (val != null) {
        consumed.incrementAndGet();
        count++;
    }
    // 没取到就继续循环（不挂起线程）
}
```

这种"快速消费"模式适合：
- 事件收集器（多线程生产事件，单线程批量消费）
- 结果汇总（并行任务把结果写入队列，主线程收集）
- 非关键消息的异步处理（丢失一条消息可以接受）

**不适合**：消费者必须等到有数据才能工作的场景（应用 `LinkedBlockingQueue`）。

### 5.5.2 线程池内部的队列选型

Java 线程池的三种预设配置，体现了三种队列选型策略：

```java
// newFixedThreadPool：固定线程数 + 无界队列（可能 OOM！）
new ThreadPoolExecutor(n, n, 0L, MILLISECONDS,
    new LinkedBlockingQueue<Runnable>());  // 默认无界！

// newCachedThreadPool：弹性线程 + SynchronousQueue（任务不排队）
new ThreadPoolExecutor(0, MAX_VALUE, 60L, SECONDS,
    new SynchronousQueue<Runnable>());

// newScheduledThreadPool：定时/延迟任务
new ScheduledThreadPoolExecutor(n);  // 内部使用 DelayedWorkQueue（类似 DelayQueue）
```

**实际项目中应该避免用这三种预设**，而是明确地自定义线程池参数，特别是要指定有界队列和拒绝策略：

```java
// 生产级线程池配置
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                           // 核心线程数
    8,                           // 最大线程数
    60L, TimeUnit.SECONDS,       // 空闲线程存活时间
    new ArrayBlockingQueue<>(200), // 有界队列！防止内存堆积
    new ThreadFactoryBuilder().setNameFormat("biz-worker-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy()  // 队满时由调用线程执行（反压）
);
```

---

## 5.6 常见错误总结

### 错误1：用 Stack 类而不是 ArrayDeque

```java
// ❌ Stack 继承自 Vector，所有方法 synchronized，单线程场景无谓的锁开销
Stack<String> stack = new Stack<>();

// ✅ ArrayDeque，无锁，性能更好
Deque<String> stack = new ArrayDeque<>();
```

### 错误2：LinkedBlockingQueue 不设容量上限

```java
// ❌ 默认 Integer.MAX_VALUE，生产速度 > 消费速度时内存无限堆积
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

// ✅ 指定合理容量，启用反压
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(1000);
```

### 错误3：用 PriorityQueue 的 forEach/迭代器期望有序输出

```java
// ❌ 迭代器按数组顺序遍历，不保证优先级顺序
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(3); pq.offer(1); pq.offer(2);
for (int v : pq) {
    System.out.print(v + " ");  // 可能输出 1 3 2，不是 1 2 3 ！
}

// ✅ 只有 poll() 才保证优先级顺序
while (!pq.isEmpty()) {
    System.out.print(pq.poll() + " ");  // 保证输出 1 2 3
}
```

### 错误4：在高并发下频繁调用 ConcurrentLinkedQueue.size()

```java
// 对应代码：ConcurrentLinkedQueueDemo.java → basicOperations() 中的警告

// ❌ size() 是 O(n) 操作，10 万元素的队列调用一次要遍历 10 万个节点
if (queue.size() == 0) { ... }

// ✅ isEmpty() 是 O(1)
if (queue.isEmpty()) { ... }
```

### 错误5：消费者用 while(true) + take()，但没有退出机制

```java
// ❌ 生产者停止后，消费者永久阻塞在 take()，线程无法正常退出
while (true) {
    Task task = queue.take();  // 如果生产者停了，这里永久等待
    process(task);
}

// ✅ 使用超时 poll + 退出标志（参考 ProducerConsumerPool.java 的设计）
while (true) {
    Task task = queue.poll(200, TimeUnit.MILLISECONDS);
    if (task == null) {
        if (producerStopped && queue.isEmpty()) break;
        continue;
    }
    process(task);
}
```

---

## 5.7 性能调优建议

### 调优点1：ArrayBlockingQueue 的初始容量

容量选择影响系统行为：
- 太小 → 生产者频繁被阻塞，吞吐量下降
- 太大 → 内存占用高，且出问题时队列积压量大，延迟高

**经验公式**：`容量 = 消费者每秒处理能力 × 可接受的最大延迟（秒）`

例如：消费者每秒处理 1000 条，可接受延迟 2 秒，则容量设为 2000。

### 调优点2：批量 drainTo 的 batchSize 选择

`ProducerConsumerPool.java` 中每次最多消费固定数量的消息。批量大小的权衡：
- 太小 → 频繁触发 I/O 或数据库操作，效率低
- 太大 → 单次处理时间长，影响延迟

**建议**：根据下游处理时间动态调整，或者采用"时间/数量双触发"策略：

```java
// 攒够 100 条 OR 等了 50ms，取其先到者
List<Task> batch = new ArrayList<>(100);
queue.drainTo(batch, 100);
if (batch.isEmpty()) {
    Task first = queue.poll(50, TimeUnit.MILLISECONDS);
    if (first != null) {
        batch.add(first);
        queue.drainTo(batch, 99);  // 再捞取剩余的
    }
}
if (!batch.isEmpty()) {
    processBatch(batch);
}
```

### 调优点3：PriorityQueue 的初始容量

```java
// 如果预估元素数量，提前指定初始容量，避免扩容时的数组复制
PriorityQueue<Task> pq = new PriorityQueue<>(expectedSize);
```

`PriorityQueue` 默认初始容量是 11，扩容时按 `oldCapacity + (oldCapacity < 64 ? oldCapacity + 2 : oldCapacity / 2)` 增长，如果数量可预估，提前指定避免多次扩容。

---

## 5.8 本章总结与学习路径

通过这五章的学习，应该建立起对 Java Queue 体系的完整认知：

**知识体系回顾**：

```
第一章：为什么需要队列？体系全景与设计哲学
    ↓
第二章：ArrayDeque — 循环数组 + 无锁，是栈/队列的最优单线程解
    ↓
第三章：PriorityQueue — 二叉堆，O(log n) 优先级操作，TopK 的核心工具
    ↓
第四章：BlockingQueue 体系 — 阻塞语义、读写分离锁、CAS 无锁，并发编程基础设施
    ↓
第五章：实战场景 — 生产者-消费者、延迟任务、批量消费、线程池集成
```

**做决策时的三个核心问题**：

1. **需要线程安全吗？** → 否则用 `ArrayDeque` 或 `PriorityQueue`
2. **消费者需要等待数据吗？** → 是则用 `BlockingQueue`，否则用 `ConcurrentLinkedQueue`
3. **队列需要有界吗？** → 生产环境几乎总是需要，防止内存堆积

**对应代码文件索引**：

| 知识点 | 演示代码 | 实战代码 |
|--------|---------|---------|
| ArrayDeque 循环数组/栈/队列/双端队列 | `ArrayDequeDemo.java` | — |
| PriorityQueue 堆操作/TopK | `PriorityQueueDemo.java` | — |
| ArrayBlockingQueue 有界阻塞 | `BlockingQueueDemo.java` | `ProducerConsumerPool.java` |
| LinkedBlockingQueue 双锁高吞吐 | `BlockingQueueDemo.java` | — |
| SynchronousQueue 零容量握手 | `BlockingQueueDemo.java` | — |
| DelayQueue 延迟到期调度 | `BlockingQueueDemo.java` | `DelayTaskScheduler.java` |
| ConcurrentLinkedQueue CAS 无锁 | `ConcurrentLinkedQueueDemo.java` | — |
