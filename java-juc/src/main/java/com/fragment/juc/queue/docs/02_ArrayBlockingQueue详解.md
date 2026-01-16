# ArrayBlockingQueue详解

> **本章目标**：深入理解ArrayBlockingQueue的设计原理、实现细节、应用场景及最佳实践

---

## 一、为什么需要ArrayBlockingQueue？

### 问题1：什么场景下需要有界阻塞队列？

#### 场景1：生产者-消费者模式

**业务场景**：日志异步写入系统

```java
// ❌ 问题：无界队列可能导致OOM
BlockingQueue<LogEvent> queue = new LinkedBlockingQueue<>(); // 无界

// 如果日志产生速度 > 写入速度，队列无限增长
while (true) {
    LogEvent event = generateLog();
    queue.put(event);  // 永不阻塞，内存可能耗尽
}
```

**解决方案**：使用有界队列
```java
// ✅ 有界队列，内存可控
BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(10000);

// 队列满时，生产者阻塞，形成背压
queue.put(event);  // 队列满时等待，保护系统
```

**有界队列的价值**：
1. **内存保护**：防止OOM
2. **背压机制**：生产速度过快时自动限流
3. **系统稳定性**：限制资源消耗

---

### 问题2：为什么选择数组而不是链表？

#### 数组 vs 链表对比

| 特性 | 数组（ArrayBlockingQueue） | 链表（LinkedBlockingQueue） |
|-----|---------------------------|----------------------------|
| **内存布局** | 连续内存 | 分散内存 |
| **缓存友好** | ✅ 高（局部性好） | ❌ 低（指针跳转） |
| **内存开销** | ✅ 低（无额外Node） | ❌ 高（每个元素一个Node） |
| **容量** | 固定 | 动态 |
| **GC压力** | ✅ 低 | ❌ 高（频繁创建Node） |

**选择ArrayBlockingQueue的场景**：
- ✅ 容量固定且已知
- ✅ 对内存敏感
- ✅ 追求缓存性能
- ✅ 减少GC压力

---

### 问题3：为什么使用单锁而不是双锁？

#### 单锁设计的权衡

**单锁的优势**：
```java
// 一把锁保护整个数组
final ReentrantLock lock;

// 实现简单，逻辑清晰
public void put(E e) {
    lock.lock();
    try {
        // 操作数组
    } finally {
        lock.unlock();
    }
}
```

**为什么不用双锁？**
- 数组是固定大小的循环队列
- takeIndex和putIndex可能指向同一位置
- 双锁需要复杂的协调机制
- 数组操作本身很快，锁竞争不是主要瓶颈

**设计哲学**：
> 简单性 > 性能（在数组场景下）

---

## 二、核心数据结构

### 2.1 类定义

```java
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    
    // 底层数组，存储元素
    final Object[] items;
    
    // 取元素的索引（队头）
    int takeIndex;
    
    // 放元素的索引（队尾）
    int putIndex;
    
    // 队列中的元素个数
    int count;
    
    // 全局锁
    final ReentrantLock lock;
    
    // 等待队列：队列非空条件
    private final Condition notEmpty;
    
    // 等待队列：队列非满条件
    private final Condition notFull;
}
```

### 2.2 循环数组结构

```
初始状态：capacity = 5
┌───┬───┬───┬───┬───┐
│   │   │   │   │   │
└───┴───┴───┴───┴───┘
  ↑
  takeIndex = 0
  putIndex = 0
  count = 0

插入3个元素后：
┌───┬───┬───┬───┬───┐
│ A │ B │ C │   │   │
└───┴───┴───┴───┴───┘
  ↑           ↑
  takeIndex   putIndex
  count = 3

取出1个元素后：
┌───┬───┬───┬───┬───┐
│   │ B │ C │   │   │
└───┴───┴───┴───┴───┘
      ↑       ↑
      takeIndex putIndex
      count = 2

继续插入3个元素（循环）：
┌───┬───┬───┬───┬───┐
│ E │ B │ C │ D │ E │  ← putIndex回到0
└───┴───┴───┴───┴───┘
      ↑   ↑
      │   putIndex = 0
      takeIndex = 1
      count = 5（队列满）
```

**循环数组的核心**：
```java
// 索引递增（循环）
final int inc(int i) {
    return (++i == items.length) ? 0 : i;
}

// 索引递减（循环）
final int dec(int i) {
    return ((i == 0) ? items.length : i) - 1;
}
```

---

## 三、核心操作流程

### 3.1 构造方法

```java
public ArrayBlockingQueue(int capacity) {
    this(capacity, false);  // 默认非公平锁
}

public ArrayBlockingQueue(int capacity, boolean fair) {
    if (capacity <= 0)
        throw new IllegalArgumentException();
    this.items = new Object[capacity];
    lock = new ReentrantLock(fair);  // 可选公平锁
    notEmpty = lock.newCondition();
    notFull = lock.newCondition();
}

public ArrayBlockingQueue(int capacity, boolean fair,
                          Collection<? extends E> c) {
    this(capacity, fair);
    // 批量添加元素
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        int i = 0;
        for (E e : c) {
            checkNotNull(e);
            items[i++] = e;
        }
        count = i;
        putIndex = (i == capacity) ? 0 : i;
    } finally {
        lock.unlock();
    }
}
```

**公平锁 vs 非公平锁**：
- **公平锁**：按FIFO顺序获取锁，避免饥饿，但性能较低
- **非公平锁**：允许插队，性能更高，但可能导致饥饿

---

### 3.2 入队操作（put）

#### 完整流程图

```
put(E e) 流程
    │
    ▼
┌─────────────┐
│ 检查元素非空 │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取锁       │ ← 可中断
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 队列满？     │
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
  Yes      No
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│await │ │ 入队元素  │
│(阻塞)│ │ enqueue()│
└──┬───┘ └────┬─────┘
   │          │
   │ 被唤醒    │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notEmpty)│
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ 释放锁    │
   └──────────┘
```

#### 源码分析

```java
public void put(E e) throws InterruptedException {
    // 1. 检查元素非空
    checkNotNull(e);
    
    // 2. 获取锁（可中断）
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();  // 响应中断
    
    try {
        // 3. 如果队列满，等待
        while (count == items.length)
            notFull.await();  // 释放锁，进入等待队列
        
        // 4. 入队
        enqueue(e);
    } finally {
        // 5. 释放锁
        lock.unlock();
    }
}

// 入队核心逻辑
private void enqueue(E x) {
    final Object[] items = this.items;
    
    // 1. 在putIndex位置放入元素
    items[putIndex] = x;
    
    // 2. putIndex循环递增
    if (++putIndex == items.length)
        putIndex = 0;
    
    // 3. 元素个数+1
    count++;
    
    // 4. 唤醒一个等待取元素的线程
    notEmpty.signal();
}
```

**关键点分析**：

1. **为什么用while而不是if？**
```java
// ❌ 错误：使用if
if (count == items.length)
    notFull.await();
// 被唤醒后，可能队列又满了（被其他线程抢先）

// ✅ 正确：使用while
while (count == items.length)
    notFull.await();
// 被唤醒后重新检查条件
```

2. **为什么用lockInterruptibly？**
```java
// 支持中断，避免线程永久阻塞
lock.lockInterruptibly();

// 使用场景：
Thread t = new Thread(() -> {
    try {
        queue.put(item);
    } catch (InterruptedException e) {
        // 线程被中断，可以优雅退出
        Thread.currentThread().interrupt();
    }
});
t.start();
t.interrupt();  // 可以中断阻塞的put操作
```

3. **signal vs signalAll**
```java
// ArrayBlockingQueue使用signal（只唤醒一个）
notEmpty.signal();

// 为什么不用signalAll？
// - 只需要唤醒一个消费者（入队了一个元素）
// - signalAll会唤醒所有等待线程，造成惊群效应
```

---

### 3.3 出队操作（take）

#### 完整流程图

```
take() 流程
    │
    ▼
┌─────────────┐
│ 获取锁       │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 队列空？     │
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
  Yes      No
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│await │ │ 出队元素  │
│(阻塞)│ │ dequeue()│
└──┬───┘ └────┬─────┘
   │          │
   │ 被唤醒    │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notFull) │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ 释放锁    │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ 返回元素  │
   └──────────┘
```

#### 源码分析

```java
public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    
    try {
        // 如果队列空，等待
        while (count == 0)
            notEmpty.await();
        
        // 出队
        return dequeue();
    } finally {
        lock.unlock();
    }
}

// 出队核心逻辑
private E dequeue() {
    final Object[] items = this.items;
    
    // 1. 取出takeIndex位置的元素
    @SuppressWarnings("unchecked")
    E x = (E) items[takeIndex];
    
    // 2. 清空引用（帮助GC）
    items[takeIndex] = null;
    
    // 3. takeIndex循环递增
    if (++takeIndex == items.length)
        takeIndex = 0;
    
    // 4. 元素个数-1
    count--;
    
    // 5. 更新迭代器（如果有）
    if (itrs != null)
        itrs.elementDequeued();
    
    // 6. 唤醒一个等待放元素的线程
    notFull.signal();
    
    return x;
}
```

**关键点**：
- **清空引用**：`items[takeIndex] = null` 防止内存泄漏
- **循环索引**：takeIndex自动循环
- **精准唤醒**：只唤醒一个生产者

---

### 3.4 非阻塞操作（offer/poll）

#### offer方法

```java
// 立即返回，不阻塞
public boolean offer(E e) {
    checkNotNull(e);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 队列满，直接返回false
        if (count == items.length)
            return false;
        else {
            enqueue(e);
            return true;
        }
    } finally {
        lock.unlock();
    }
}

// 超时版本
public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
    checkNotNull(e);
    long nanos = unit.toNanos(timeout);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    
    try {
        // 队列满，等待指定时间
        while (count == items.length) {
            if (nanos <= 0)
                return false;  // 超时返回false
            nanos = notFull.awaitNanos(nanos);  // 等待
        }
        enqueue(e);
        return true;
    } finally {
        lock.unlock();
    }
}
```

#### poll方法

```java
// 立即返回
public E poll() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 队列空，返回null
        return (count == 0) ? null : dequeue();
    } finally {
        lock.unlock();
    }
}

// 超时版本
public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    
    try {
        while (count == 0) {
            if (nanos <= 0)
                return null;  // 超时返回null
            nanos = notEmpty.awaitNanos(nanos);
        }
        return dequeue();
    } finally {
        lock.unlock();
    }
}
```

**方法对比**：

| 方法 | 队列满/空 | 返回值 | 是否阻塞 | 是否抛异常 |
|-----|----------|--------|---------|-----------|
| **put** | 队列满 | void | ✅ 阻塞 | ✅ InterruptedException |
| **offer** | 队列满 | false | ❌ 不阻塞 | ❌ |
| **offer(timeout)** | 队列满 | false | ✅ 超时阻塞 | ✅ InterruptedException |
| **take** | 队列空 | - | ✅ 阻塞 | ✅ InterruptedException |
| **poll** | 队列空 | null | ❌ 不阻塞 | ❌ |
| **poll(timeout)** | 队列空 | null | ✅ 超时阻塞 | ✅ InterruptedException |

---

### 3.5 其他操作

#### peek方法（查看队头）

```java
public E peek() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 返回队头元素，不移除
        return itemAt(takeIndex);
    } finally {
        lock.unlock();
    }
}

final E itemAt(int i) {
    return (E) items[i];
}
```

#### size方法

```java
public int size() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return count;
    } finally {
        lock.unlock();
    }
}
```

#### remainingCapacity方法

```java
public int remainingCapacity() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return items.length - count;
    } finally {
        lock.unlock();
    }
}
```

#### remove方法（移除指定元素）

```java
public boolean remove(Object o) {
    if (o == null) return false;
    final Object[] items = this.items;
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        if (count > 0) {
            final int putIndex = this.putIndex;
            int i = takeIndex;
            // 遍历查找
            do {
                if (o.equals(items[i])) {
                    removeAt(i);  // 移除元素
                    return true;
                }
                if (++i == items.length)
                    i = 0;
            } while (i != putIndex);
        }
        return false;
    } finally {
        lock.unlock();
    }
}

// 移除指定位置的元素
void removeAt(final int removeIndex) {
    final Object[] items = this.items;
    // 如果是队头，直接移除
    if (removeIndex == takeIndex) {
        items[takeIndex] = null;
        if (++takeIndex == items.length)
            takeIndex = 0;
        count--;
        if (itrs != null)
            itrs.elementDequeued();
    } else {
        // 否则需要移动元素
        final int putIndex = this.putIndex;
        for (int i = removeIndex;;) {
            int next = i + 1;
            if (next == items.length)
                next = 0;
            if (next != putIndex) {
                items[i] = items[next];  // 向前移动
                i = next;
            } else {
                items[i] = null;
                this.putIndex = i;
                break;
            }
        }
        count--;
        if (itrs != null)
            itrs.removedAt(removeIndex);
    }
    notFull.signal();
}
```

---

## 四、核心设计精髓

### 4.1 为什么这样设计？

#### 设计1：单锁 + 两个Condition

**问题**：为什么不用两把锁？

**回答**：
```java
// 数组是固定大小的循环结构
// takeIndex和putIndex可能重叠
// 双锁需要复杂的协调

// 示例：队列只有1个元素
takeIndex = 0, putIndex = 1

// 如果用双锁：
// 线程1: takeLock.lock() -> 取元素 -> putIndex变为0
// 线程2: putLock.lock() -> 放元素 -> putIndex变为1
// 两个线程同时操作putIndex，需要额外同步！
```

**单锁的优势**：
- ✅ 实现简单
- ✅ 逻辑清晰
- ✅ 无需协调多个锁

#### 设计2：循环数组

**问题**：为什么不用普通数组？

**回答**：
```java
// ❌ 普通数组：出队后需要移动元素
[A, B, C, D, E]
取出A后：
[B, C, D, E, _]  // 需要移动4个元素，O(n)

// ✅ 循环数组：只需移动索引
[A, B, C, D, E]
   ↑
   takeIndex
取出A后：
[_, B, C, D, E]
      ↑
      takeIndex  // 只需移动索引，O(1)
```

#### 设计3：signal而不是signalAll

**问题**：为什么不唤醒所有等待线程？

**回答**：
```java
// 入队一个元素，只需唤醒一个消费者
enqueue(e);
notEmpty.signal();  // 只唤醒一个

// 如果用signalAll：
notEmpty.signalAll();  // 唤醒所有消费者
// 问题：只有一个元素，其他线程被唤醒后又要等待
// 造成惊群效应，浪费CPU
```

---

### 4.2 源码中的巧妙设计

#### 技巧1：final局部变量优化

```java
public void put(E e) throws InterruptedException {
    checkNotNull(e);
    // 使用final局部变量，JVM可以优化
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    // ...
}
```

**为什么这样写？**
- JVM可以将final变量缓存到寄存器
- 避免每次从对象中读取字段
- 性能优化

#### 技巧2：清空引用帮助GC

```java
private E dequeue() {
    final Object[] items = this.items;
    E x = (E) items[takeIndex];
    items[takeIndex] = null;  // 清空引用
    // ...
}
```

**为什么要清空？**
```java
// 如果不清空：
[A, B, C, _, _]
   ↑
   takeIndex

// A虽然被取出，但数组仍持有引用
// 如果A是大对象，无法被GC回收

// 清空后：
[null, B, C, _, _]
       ↑
       takeIndex
// A可以被GC回收
```

#### 技巧3：迭代器弱一致性

```java
// 迭代器不会抛ConcurrentModificationException
Iterator<E> it = queue.iterator();
while (it.hasNext()) {
    E e = it.next();
    // 其他线程可以修改队列
    queue.put(newItem);  // 不会抛异常
}
```

**实现原理**：
- 迭代器创建时拍摄快照
- 后续修改不影响迭代
- 弱一致性，不保证实时性

---

## 五、应用场景与最佳实践

### 5.1 适用场景

#### 场景1：生产者-消费者（固定容量）

```java
// 日志异步写入
BlockingQueue<LogEvent> logQueue = new ArrayBlockingQueue<>(10000);

// 生产者：业务线程
public void log(String message) {
    try {
        logQueue.put(new LogEvent(message));
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}

// 消费者：日志写入线程
while (true) {
    LogEvent event = logQueue.take();
    writeToFile(event);
}
```

#### 场景2：限流器

```java
// 令牌桶限流
public class RateLimiter {
    private final BlockingQueue<Token> tokens;
    
    public RateLimiter(int rate) {
        tokens = new ArrayBlockingQueue<>(rate);
        // 定时添加令牌
        scheduleTokenGeneration();
    }
    
    public boolean tryAcquire() {
        return tokens.poll() != null;
    }
}
```

#### 场景3：线程池任务队列

```java
// 固定大小的任务队列
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10,  // 核心线程数
    20,  // 最大线程数
    60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(100)  // 任务队列
);
```

---

### 5.2 常见陷阱

#### 陷阱1：容量设置不合理

```java
// ❌ 容量过小
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1);
// 问题：频繁阻塞，吞吐量低

// ❌ 容量过大
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000000);
// 问题：内存占用高，可能OOM

// ✅ 合理设置
// 容量 = 生产速率 × 平均处理时间 × 安全系数
// 例如：1000 req/s × 0.1s × 2 = 200
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(200);
```

#### 陷阱2：中断处理不当

```java
// ❌ 错误：吞掉中断
try {
    queue.put(item);
} catch (InterruptedException e) {
    // 什么都不做
}

// ✅ 正确：恢复中断状态
try {
    queue.put(item);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // 恢复中断
    // 或者向上抛出
}
```

#### 陷阱3：使用add/remove而不是put/take

```java
// ❌ 错误：使用add
queue.add(item);  // 队列满时抛IllegalStateException

// ✅ 正确：使用put
queue.put(item);  // 队列满时阻塞等待
```

#### 陷阱4：在锁外判断队列状态

```java
// ❌ 错误：非原子操作
if (!queue.isEmpty()) {
    E item = queue.poll();  // 可能返回null
}

// ✅ 正确：使用阻塞方法
E item = queue.poll(1, TimeUnit.SECONDS);
if (item != null) {
    // 处理
}
```

---

### 5.3 性能优化建议

#### 优化1：选择合适的锁类型

```java
// 高并发场景：使用非公平锁（默认）
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);

// 需要严格FIFO：使用公平锁
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100, true);
```

**性能对比**：
- 非公平锁：吞吐量高，可能饥饿
- 公平锁：公平性好，性能较低

#### 优化2：批量操作

```java
// ❌ 低效：逐个添加
for (Task task : tasks) {
    queue.put(task);  // 每次都要获取锁
}

// ✅ 高效：批量添加
queue.addAll(tasks);  // 只获取一次锁
```

#### 优化3：避免频繁size()调用

```java
// ❌ 低效：频繁调用size
while (queue.size() > 0) {  // 每次都要获取锁
    E item = queue.poll();
}

// ✅ 高效：直接poll
E item;
while ((item = queue.poll()) != null) {
    // 处理
}
```

---

## 六、对比分析

### 6.1 ArrayBlockingQueue vs LinkedBlockingQueue

| 特性 | ArrayBlockingQueue | LinkedBlockingQueue |
|-----|-------------------|---------------------|
| **底层结构** | 数组 | 链表 |
| **容量** | 固定 | 可选（默认Integer.MAX_VALUE） |
| **锁机制** | 单锁 | 双锁 |
| **内存占用** | 低 | 高（Node开销） |
| **缓存性能** | 好 | 差 |
| **吞吐量** | 中 | 高 |
| **适用场景** | 固定容量、内存敏感 | 高并发、吞吐优先 |

### 6.2 何时选择ArrayBlockingQueue？

**选择ArrayBlockingQueue的理由**：
1. ✅ 容量固定且已知
2. ✅ 对内存占用敏感
3. ✅ 需要严格的容量限制
4. ✅ 元素较小，缓存友好
5. ✅ 减少GC压力

**不选择的理由**：
1. ❌ 需要动态扩容
2. ❌ 追求极致吞吐量
3. ❌ 容量很大（链表更合适）

---

## 七、总结

### 核心要点

1. **设计思想**：
   - 单锁 + 两个Condition
   - 循环数组，O(1)入队/出队
   - 有界队列，内存可控

2. **关键实现**：
   - while循环检查条件（防止虚假唤醒）
   - signal精准唤醒（避免惊群）
   - 清空引用帮助GC

3. **适用场景**：
   - 生产者-消费者模式
   - 固定容量的任务队列
   - 限流器

4. **注意事项**：
   - 合理设置容量
   - 正确处理中断
   - 使用put/take而不是add/remove

---

**下一章**：LinkedBlockingQueue详解 - 双锁优化的链表队列
