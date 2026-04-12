# 第四章：BlockingQueue 并发设计与选型

> **对应演示代码**：`demo/BlockingQueueDemo.java`、`demo/ConcurrentLinkedQueueDemo.java`
> **对应实战代码**：`project/ProducerConsumerPool.java`

## 4.1 阻塞队列解决的核心问题

在多线程编程中，**生产者-消费者模式**是最经典的并发协调模型。没有 `BlockingQueue` 之前，自己实现这个模型需要手动管理锁、条件变量，稍有不慎就会产生死锁或竞态条件：

```java
// 没有 BlockingQueue 时，手动实现生产者-消费者（容易出错）
class ManualQueue<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final Object lock = new Object();

    public void put(T item) throws InterruptedException {
        synchronized (lock) {
            while (queue.size() >= capacity) {
                lock.wait();           // 队满时等待
            }
            queue.offer(item);
            lock.notifyAll();          // 通知消费者
        }
    }

    public T take() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty()) {
                lock.wait();           // 队空时等待
            }
            T item = queue.poll();
            lock.notifyAll();          // 通知生产者
            return item;
        }
    }
}
```

这段代码有几个问题：
1. `notifyAll()` 会唤醒所有等待线程，包括不需要被唤醒的（如消费者等待时，生产者完成入队后应该只唤醒消费者，而不是所有线程）
2. 使用 `synchronized` 把生产和消费串行化，并发吞吐量低
3. 业务代码和同步代码混在一起，难以维护

`BlockingQueue` 把这些细节封装在内部，让业务代码极度简洁，同时实现更高效的并发控制。

---

## 4.2 BlockingQueue 的方法体系

`BlockingQueue` 扩展了 `Queue` 接口，新增了两组方法（`put/take` 和超时版 `offer/poll`）：

```
操作    │ 抛异常    │ 返特殊值  │ 一直阻塞  │ 超时阻塞
───────────────────────────────────────────────────────
入队    │ add(e)   │ offer(e) │ put(e)   │ offer(e, time, unit)
出队    │ remove() │ poll()   │ take()   │ poll(time, unit)
查看    │element() │ peek()   │    —     │       —
```

（此表格也出现在 `BlockingQueueDemo.java` 文件头注释中，对应看代码会更清晰）

**四组方法的使用场景**：

- **抛异常组**（`add/remove/element`）：适合边界情况代表程序错误的场景，希望快速失败
- **返特殊值组**（`offer/poll/peek`）：非阻塞调用，调用方自行决定如何处理队满/队空
- **一直阻塞组**（`put/take`）：生产者-消费者模式的标准用法，线程协调的核心
- **超时阻塞组**：在阻塞的基础上加上"等不了太久"的限制，防止死等

```java
// 对应代码：BlockingQueueDemo.java → arrayBlockingQueueDemo()

// 超时版 offer：实现"宁可丢弃也不无限等待"的限流策略
boolean accepted = queue.offer(task, 100, TimeUnit.MILLISECONDS);
if (!accepted) {
    // 队列100ms内仍满，触发限流逻辑（记日志、返回429等）
    log.warn("队列满载，任务被丢弃: {}", task);
}
```

---

## 4.3 ArrayBlockingQueue：有界单锁

### 4.3.1 内部结构

```java
// JDK 源码（简化）
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, Serializable {

    final Object[] items;           // 底层数组（固定大小）
    int takeIndex;                  // 下一次 take 的位置
    int putIndex;                   // 下一次 put 的位置
    int count;                      // 当前元素数量

    final ReentrantLock lock;       // 单把锁（生产和消费共用！）
    private final Condition notEmpty; // 消费者等待条件
    private final Condition notFull;  // 生产者等待条件
}
```

**关键设计**：只有一把 `ReentrantLock`，生产和消费都要竞争这把锁。这意味着：在任何时刻，要么只有生产者在操作队列，要么只有消费者在操作队列，两者互斥。

### 4.3.2 put 的完整流程

```java
// JDK 源码
public void put(E e) throws InterruptedException {
    checkNotNull(e);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();    // 可中断地加锁
    try {
        while (count == items.length)  // 队满，循环等待（用 while 而非 if，防止虚假唤醒）
            notFull.await();           // 释放锁，挂起当前线程
        enqueue(e);                    // 入队，并唤醒 notEmpty 上等待的消费者
    } finally {
        lock.unlock();
    }
}

private void enqueue(E x) {
    items[putIndex] = x;
    if (++putIndex == items.length)    // 循环数组，到达末尾时绕回
        putIndex = 0;
    count++;
    notEmpty.signal();                 // 精确唤醒一个消费者（比 notifyAll 高效）
}
```

这里有两个细节值得注意：

**1. `while` 而非 `if`**：等待条件判断用 `while` 循环，而不是 `if`。这是为了防止**虚假唤醒**（Spurious Wakeup）。操作系统层面，线程可能在没有收到 `signal()` 的情况下被唤醒（这是 POSIX 标准允许的行为）。用 `while` 保证唤醒后再次检查条件，避免在队列仍满时错误地入队。

**2. `notEmpty.signal()` 而非 `notifyAll()`**：`Condition` 对象允许对不同的条件分别管理等待线程。`notEmpty.signal()` 只唤醒等待"队列非空"的消费者线程，`notFull.signal()` 只唤醒等待"队列未满"的生产者线程，避免了 `notifyAll()` 的无差别唤醒浪费。

### 4.3.3 公平模式

```java
// 对应代码：BlockingQueueDemo.java → ArrayBlockingQueue 构造

// 默认非公平模式（吞吐量更高，线程可能饿死）
ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

// 公平模式（按等待时间先后顺序获取锁，防止饥饿，但吞吐量下降）
ArrayBlockingQueue<String> fairQueue = new ArrayBlockingQueue<>(3, true);
```

公平模式底层使用 `ReentrantLock(true)`，等待最久的线程优先获取锁。适合对公平性有要求的场景（如任务调度），但吞吐量会降低约 20%~30%（需要维护等待队列顺序）。

---

## 4.4 LinkedBlockingQueue：有界双锁（读写分离）

### 4.4.1 内部结构与核心设计

```java
// JDK 源码（简化）
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, Serializable {

    private final int capacity;
    private final AtomicInteger count = new AtomicInteger(); // 原子计数

    transient Node<E> head;  // 队头（虚拟头节点，head.item == null）
    private transient Node<E> last;  // 队尾

    // ★ 核心差异：读写分离两把锁
    private final ReentrantLock takeLock = new ReentrantLock(); // 消费者锁
    private final Condition notEmpty = takeLock.newCondition();

    private final ReentrantLock putLock = new ReentrantLock();  // 生产者锁
    private final Condition notFull = putLock.newCondition();
}
```

**两把锁的设计思想**：

生产者操作队尾（`last`），消费者操作队头（`head`），两者操作的是链表的不同端，**天然不存在竞争**。那么为什么 `ArrayBlockingQueue` 不能这么做？

因为数组是连续内存，共享同一个 `count` 变量，且头尾可能因为循环相遇。链表则没有这个问题——头部和尾部是完全独立的节点，只有在 `count` 的更新上需要协调（通过 `AtomicInteger` 实现原子更新，无锁）。

### 4.4.2 put 和 take 的并发执行

```
生产者线程                              消费者线程
    │                                       │
    ├─ 获取 putLock                         ├─ 获取 takeLock
    ├─ 检查 count < capacity               ├─ 检查 count > 0
    ├─ 链表尾部追加新节点                    ├─ 摘除链表头部节点
    ├─ count.incrementAndGet()              ├─ count.decrementAndGet()
    ├─ 如果 count < capacity，唤醒其他生产者 ├─ 如果 count > 0，唤醒其他消费者
    └─ 释放 putLock                         ├─ 如果 count == capacity-1，唤醒生产者
                                            └─ 释放 takeLock
```

生产者和消费者可以**真正并发**地执行，不互相阻塞。这是 `LinkedBlockingQueue` 在高并发场景下吞吐量显著高于 `ArrayBlockingQueue` 的根本原因。

### 4.4.3 drainTo：批量消费的高效方式

```java
// 对应代码：BlockingQueueDemo.java → linkedBlockingQueueDemo()

List<Integer> batch = new ArrayList<>();
int drained = queue.drainTo(batch, 3);  // 一次性取出最多 3 个
```

`drainTo` 只需要加一次 `takeLock`，就能取出多个元素，比循环调用 `poll()` 减少了多次加锁/解锁的开销。在批量日志写入、批量数据库插入等场景下非常有用。

### 4.4.4 注意：默认无界容量是陷阱

```java
// ❌ 危险：不指定容量，默认 Integer.MAX_VALUE
LinkedBlockingQueue<Task> queue = new LinkedBlockingQueue<>();
// 生产速率 >> 消费速率时，队列无限增长 → OOM

// ✅ 正确：明确指定容量上限
LinkedBlockingQueue<Task> queue = new LinkedBlockingQueue<>(1000);
// 超过 1000 时生产者被阻塞（反压），不会 OOM
```

这在 `ProducerConsumerPool.java` 中有体现：使用 `ArrayBlockingQueue` 而非无界队列，配合 `put()` 的阻塞特性实现反压。

---

## 4.5 SynchronousQueue：零容量的握手协议

### 4.5.1 本质理解

`SynchronousQueue` 是一个非常特殊的队列：**它不存储任何元素**。`put()` 调用必须等到有线程来 `take()`，两者**同时到达才能完成传递**，就像两个人握手——双方必须都伸出手，交握才算完成。

```java
// 对应代码：BlockingQueueDemo.java → synchronousQueueDemo()

SynchronousQueue<String> queue = new SynchronousQueue<>();

// 消费者先等
Thread consumer = new Thread(() -> {
    String data = queue.take();  // 阻塞，等待生产者
    System.out.println("收到: " + data);
});

// 生产者后到
Thread producer = new Thread(() -> {
    queue.put("Hello");  // 阻塞，等待消费者准备好
    // put 返回后，数据已经被消费者拿走
});
```

### 4.5.2 为什么 newCachedThreadPool 使用 SynchronousQueue？

```java
// JDK 源码
public static ExecutorService newCachedThreadPool() {
    return new ThreadPoolExecutor(
        0, Integer.MAX_VALUE,    // 核心线程数=0，最大线程数=无界
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<E>()  // 使用 SynchronousQueue
    );
}
```

`newCachedThreadPool` 的语义是：**来一个任务立刻启动一个线程处理，线程空闲 60 秒后回收**。

用 `SynchronousQueue` 的原因：
- 任务来了，先尝试 `offer()` 到队列，如果当前有空闲线程在 `poll()` 等待，立即传递
- 如果没有空闲线程（`offer` 失败），线程池新建一个线程来消费
- 效果：**任务永远不排队**，有空闲线程就复用，没有就新建

如果改用 `LinkedBlockingQueue`，任务会先进队列等待，不符合"立即处理"的语义。

### 4.5.3 公平模式与非公平模式

```java
// 默认非公平模式（LIFO，后到的线程先配对，吞吐量更高）
SynchronousQueue<String> sq = new SynchronousQueue<>();

// 公平模式（FIFO，先到先配对）
SynchronousQueue<String> fairSQ = new SynchronousQueue<>(true);
```

---

## 4.6 DelayQueue：时间维度的优先级队列

### 4.6.1 工作原理

`DelayQueue` 是 `PriorityQueue` 的并发封装，区别在于：元素必须实现 `Delayed` 接口，`take()` 只在元素的延迟时间到期后才返回它。

```java
// 对应代码：project/DelayTaskScheduler.java → DelayedTask 内部类

public class DelayedTask implements Delayed {

    private final long expireNanos;  // 到期时间（绝对纳秒数）

    @Override
    public long getDelay(TimeUnit unit) {
        // 返回剩余延迟时间（负数表示已到期）
        long remainNanos = expireNanos - System.nanoTime();
        return unit.convert(remainNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        // 按剩余延迟时间排序，最先到期的在堆顶
        long diff = this.getDelay(TimeUnit.NANOSECONDS)
                  - other.getDelay(TimeUnit.NANOSECONDS);
        return Long.compare(diff, 0);
    }
}
```

**`take()` 的内部逻辑**：

```java
// JDK 源码（简化）
public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        for (;;) {
            E first = q.peek();           // 查看堆顶（最近到期的）
            if (first == null)
                available.await();        // 队列空，等待
            else {
                long delay = first.getDelay(NANOSECONDS);
                if (delay <= 0)
                    return q.poll();      // 已到期，出队并返回
                first = null;
                available.awaitNanos(delay); // 未到期，等待剩余时间
            }
        }
    } finally {
        lock.unlock();
    }
}
```

整个过程只有一个工作线程在 `take()` 上等待（Leader-Follower 模式的变体），避免了大量线程同时睡眠唤醒的开销。

### 4.6.2 使用 DelayQueue 的完整示例

`DelayTaskScheduler.java` 展示了一个完整的延迟任务调度器：

- 后台线程循环调用 `queue.take()`，阻塞直到有任务到期
- 业务代码调用 `scheduler.schedule("订单超时", 30_000, action)` 提交任务
- 任务按到期时间自动排序，到期即执行，无需定时轮询

这个模式相比用 `ScheduledThreadPoolExecutor` 的优势在于：任务和调度逻辑完全解耦，可以在运行时动态添加、批量提交任务。

---

## 4.7 ConcurrentLinkedQueue：无锁并发队列

### 4.7.1 CAS 无锁原理

`ConcurrentLinkedQueue` 使用 CAS（Compare-And-Swap）操作实现线程安全，不使用任何锁：

```
CAS 操作的语义：compareAndSet(expected, update)
  如果当前值 == expected，则将其改为 update，返回 true
  如果当前值 != expected（被其他线程修改了），返回 false，调用方重试
```

**入队（offer）的核心逻辑**（简化）：

```java
// 不断重试直到成功，没有锁
public boolean offer(E e) {
    Node<E> newNode = new Node<E>(e);
    for (Node<E> t = tail, p = t;;) {
        Node<E> q = p.next;
        if (q == null) {
            // p 是真正的尾节点，CAS 设置 next
            if (p.casNext(null, newNode)) {  // CAS 成功，入队完成
                // 更新 tail（允许 tail 滞后，减少 CAS 竞争）
                if (p != t) casTail(t, newNode);
                return true;
            }
            // CAS 失败：其他线程先入队了，重试
        }
        // ... 处理 tail 滞后的情况
    }
}
```

**CAS vs 锁的权衡**：

| 场景 | CAS（ConcurrentLinkedQueue） | 锁（LinkedBlockingQueue） |
|------|---------------------------|------------------------|
| 竞争低 | 快（几乎不重试） | 快（锁很快获取到） |
| 竞争高 | **慢**（大量重试，CPU 空转） | 较慢（线程挂起/唤醒开销） |
| 是否阻塞 | 不阻塞（自旋重试） | 阻塞（线程挂起） |

> **实践建议**：高竞争场景下，CAS 的自旋重试反而比锁更耗 CPU。`ConcurrentLinkedQueue` 更适合读多写少、竞争较低的场景。

### 4.7.2 size() 为什么是 O(n)？

```java
// 对应代码：ConcurrentLinkedQueueDemo.java → basicOperations()
// ⚠️ size（O(n)，慎用）: queue.size()

// JDK 源码
public int size() {
    int count = 0;
    for (Node<E> p = first(); p != null; p = succ(p))  // 遍历整个链表
        if (p.item != null)
            if (++count == Integer.MAX_VALUE)
                break;
    return count;
}
```

`ConcurrentLinkedQueue` 没有维护一个独立的 `count` 字段。原因是：在 CAS 无锁环境中，维护 `count` 需要保证与链表操作的原子性，而这两者用 CAS 难以同时保证正确性（会引入额外的 CAS 竞争）。

因此，`size()` 只能遍历链表数数。高并发下频繁调用 `size()` 会造成性能问题，**应用 `isEmpty()` 替代 `size() == 0` 的判断**（`isEmpty()` 是 O(1)）。

---

## 4.8 选型决策树

```
需要线程安全的队列？
├── 否 → ArrayDeque（单线程栈/队列）或 PriorityQueue（优先级）
└── 是
    ├── 需要阻塞等待（消费者等数据、生产者等空间）？
    │   ├── 是
    │   │   ├── 需要按优先级出队？ → PriorityBlockingQueue
    │   │   ├── 需要延迟到期出队？ → DelayQueue
    │   │   ├── 线程间直接一对一传递，不需要缓冲？ → SynchronousQueue
    │   │   ├── 需要严格限流（固定内存）、读写速率接近？ → ArrayBlockingQueue
    │   │   └── 需要高吞吐（读写速率差异大）？ → LinkedBlockingQueue（指定容量！）
    │   └── 否（非阻塞，有数据就取没数据就跳过）
    │       └── ConcurrentLinkedQueue
    └── 只需要读写安全，不需要阻塞 → ConcurrentLinkedQueue
```

---

## 4.9 ArrayBlockingQueue vs LinkedBlockingQueue 对比总结

| 维度 | ArrayBlockingQueue | LinkedBlockingQueue |
|------|-------------------|-------------------|
| **底层结构** | 数组（循环） | 链表 |
| **容量** | 固定（创建后不可变） | 可选有界（默认近似无界） |
| **锁** | 单锁（读写互斥） | 双锁（读写分离，并发更高） |
| **内存** | 创建时一次分配 | 每次入队分配 Node |
| **GC 压力** | 低 | 高（频繁分配/回收 Node） |
| **吞吐量** | 中 | 高 |
| **内存可预估性** | 好（固定大小数组） | 差（链表节点动态增长） |
| **适用场景** | 严格限流、内存敏感 | 高吞吐、生产消费速率差异大 |

> **实战体现**：`ProducerConsumerPool.java` 选用 `ArrayBlockingQueue` 而非 `LinkedBlockingQueue`，原因是限流语义更清晰——容量固定，超出时生产者被明确阻塞（反压），内存占用可以提前预估。

---

## 4.10 本章总结

`BlockingQueue` 体系的核心是**阻塞语义**：把线程协调的复杂性封装在队列内部，让生产者和消费者的业务代码专注于业务逻辑。

- **`ArrayBlockingQueue`**：单锁，有界，内存可预估，适合限流
- **`LinkedBlockingQueue`**：双锁（读写分离），高吞吐，必须指定容量
- **`SynchronousQueue`**：零容量，强制同步握手，`newCachedThreadPool` 的基础
- **`DelayQueue`**：按到期时间出队，实现延迟任务调度
- **`ConcurrentLinkedQueue`**：CAS 无锁，非阻塞，适合低竞争高并发写入

理解每种实现的内部锁机制（单锁/双锁/无锁/条件变量），是做出正确选型的关键。
