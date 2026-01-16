# LinkedBlockingQueue详解

> **本章目标**：深入理解LinkedBlockingQueue的双锁优化设计、实现细节及与ArrayBlockingQueue的对比

---

## 一、为什么需要LinkedBlockingQueue？

### 问题1：ArrayBlockingQueue的性能瓶颈在哪？

#### 单锁的性能问题

```java
// ArrayBlockingQueue：单锁设计
public class ArrayBlockingQueue<E> {
    final ReentrantLock lock;  // 一把锁
    
    public void put(E e) {
        lock.lock();  // 生产者获取锁
        try {
            // 入队
        } finally {
            lock.unlock();
        }
    }
    
    public E take() {
        lock.lock();  // 消费者也要获取同一把锁
        try {
            // 出队
        } finally {
            lock.unlock();
        }
    }
}
```

**性能瓶颈分析**：

```
时间线：生产者和消费者互斥

T1: 生产者获取lock
T2: 消费者尝试获取lock（阻塞等待）
T3: 生产者入队
T4: 生产者释放lock
T5: 消费者获取lock
T6: 消费者出队
T7: 消费者释放lock

问题：生产者和消费者完全串行执行！
```

**为什么会互斥？**
- 生产者修改putIndex
- 消费者修改takeIndex
- 两者都修改count
- 单锁保护所有状态

---

### 问题2：如何提高并发度？

#### 双锁分离的思想

**核心思路**：读写分离
```java
// LinkedBlockingQueue：双锁设计
public class LinkedBlockingQueue<E> {
    private final ReentrantLock takeLock = new ReentrantLock();  // 读锁
    private final ReentrantLock putLock = new ReentrantLock();   // 写锁
    
    public void put(E e) {
        putLock.lock();  // 只获取写锁
        try {
            // 入队
        } finally {
            putLock.unlock();
        }
    }
    
    public E take() {
        takeLock.lock();  // 只获取读锁
        try {
            // 出队
        } finally {
            takeLock.unlock();
        }
    }
}
```

**性能提升**：

```
时间线：生产者和消费者并发执行

T1: 生产者获取putLock    消费者获取takeLock
T2: 生产者入队            消费者出队
T3: 生产者释放putLock    消费者释放takeLock

优势：生产者和消费者可以并发执行！
```

**吞吐量对比**：
- ArrayBlockingQueue：生产和消费串行
- LinkedBlockingQueue：生产和消费并行，吞吐量提升约2倍

---

### 问题3：为什么链表适合双锁？

#### 链表 vs 数组

**数组的问题**：
```java
// 数组：头尾可能重叠
[A, B, C, _, _]
 ↑        ↑
 head     tail

// 当队列满时：
[A, B, C, D, E]
 ↑  ↑
 head和tail可能指向相邻位置
 // 需要协调两个锁
```

**链表的优势**：
```java
// 链表：头尾天然分离
head -> [A] -> [B] -> [C] -> tail
 ↑                            ↑
 takeLock保护              putLock保护

// 头尾操作完全独立
// 无需协调
```

**选择LinkedBlockingQueue的理由**：
1. ✅ 高并发场景，追求吞吐量
2. ✅ 生产和消费速度相近
3. ✅ 容量可选（有界或无界）
4. ✅ 链表动态扩展

---

## 二、核心数据结构

### 2.1 类定义

```java
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    
    // 容量（可选，默认Integer.MAX_VALUE）
    private final int capacity;
    
    // 当前元素个数（原子变量）
    private final AtomicInteger count = new AtomicInteger();
    
    // 头节点（哨兵节点）
    transient Node<E> head;
    
    // 尾节点
    private transient Node<E> last;
    
    // 取元素的锁
    private final ReentrantLock takeLock = new ReentrantLock();
    
    // 非空条件
    private final Condition notEmpty = takeLock.newCondition();
    
    // 放元素的锁
    private final ReentrantLock putLock = new ReentrantLock();
    
    // 非满条件
    private final Condition notFull = putLock.newCondition();
}
```

### 2.2 节点结构

```java
static class Node<E> {
    E item;
    Node<E> next;
    
    Node(E x) { item = x; }
}
```

### 2.3 链表结构

```
初始状态：
head(哨兵) -> null
  ↑
  last

插入元素A后：
head(哨兵) -> [A] -> null
              ↑
              last

插入元素B、C后：
head(哨兵) -> [A] -> [B] -> [C] -> null
                            ↑
                            last

取出元素A后：
head(哨兵) -> [B] -> [C] -> null
  ↑                   ↑
  (head移动)          last
```

**哨兵节点的作用**：
- 简化边界条件处理
- head.next才是真正的第一个元素
- 避免空指针判断

---

## 三、核心操作流程

### 3.1 构造方法

```java
// 默认容量：Integer.MAX_VALUE（无界）
public LinkedBlockingQueue() {
    this(Integer.MAX_VALUE);
}

// 指定容量（有界）
public LinkedBlockingQueue(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException();
    this.capacity = capacity;
    // 初始化哨兵节点
    last = head = new Node<E>(null);
}

// 从集合初始化
public LinkedBlockingQueue(Collection<? extends E> c) {
    this(Integer.MAX_VALUE);
    final ReentrantLock putLock = this.putLock;
    putLock.lock();
    try {
        int n = 0;
        for (E e : c) {
            if (e == null)
                throw new NullPointerException();
            if (n == capacity)
                throw new IllegalStateException("Queue full");
            enqueue(new Node<E>(e));
            ++n;
        }
        count.set(n);
    } finally {
        putLock.unlock();
    }
}
```

**容量选择**：
- 无界（默认）：适合生产速度 ≤ 消费速度
- 有界：适合需要背压控制的场景

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
│ 创建新节点   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取putLock │
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
│await │ │ 入队节点  │
└──┬───┘ │ enqueue()│
   │     └────┬─────┘
   │          │
   │ 被唤醒    │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ count++  │
   └────┬─────┘
        │
        ▼
   ┌──────────────┐
   │ c == 0?      │ ← 之前是空队列？
   └────┬─────────┘
        │
       Yes
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notEmpty)│
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │释放putLock│
   └────┬─────┘
        │
        ▼
   ┌──────────────┐
   │ c+1 < capacity?│ ← 还有空间？
   └────┬──────────┘
        │
       Yes
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notFull) │
   └──────────┘
```

#### 源码分析

```java
public void put(E e) throws InterruptedException {
    // 1. 检查元素非空
    if (e == null) throw new NullPointerException();
    
    // 2. 预设c为-1（失败标志）
    int c = -1;
    
    // 3. 创建新节点
    Node<E> node = new Node<E>(e);
    
    final ReentrantLock putLock = this.putLock;
    final AtomicInteger count = this.count;
    
    // 4. 获取putLock（可中断）
    putLock.lockInterruptibly();
    
    try {
        // 5. 如果队列满，等待
        while (count.get() == capacity) {
            notFull.await();
        }
        
        // 6. 入队
        enqueue(node);
        
        // 7. count原子递增，返回旧值
        c = count.getAndIncrement();
        
        // 8. 如果入队后还有空间，唤醒其他生产者
        if (c + 1 < capacity)
            notFull.signal();
            
    } finally {
        // 9. 释放putLock
        putLock.unlock();
    }
    
    // 10. 如果之前队列是空的，唤醒消费者
    if (c == 0)
        signalNotEmpty();
}

// 入队核心逻辑
private void enqueue(Node<E> node) {
    // 在链表尾部添加节点
    last = last.next = node;
}

// 唤醒消费者（需要获取takeLock）
private void signalNotEmpty() {
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lock();
    try {
        notEmpty.signal();
    } finally {
        takeLock.unlock();
    }
}
```

**关键点分析**：

1. **为什么使用AtomicInteger？**
```java
// count被两个锁共享
private final AtomicInteger count = new AtomicInteger();

// putLock保护：count++
c = count.getAndIncrement();

// takeLock保护：count--
c = count.getAndDecrement();

// 原子操作保证线程安全
```

2. **为什么要signalNotEmpty？**
```java
// 场景：队列从空变为非空
if (c == 0)  // c是入队前的count
    signalNotEmpty();

// 此时可能有消费者在等待
// 需要唤醒一个消费者
```

3. **为什么signalNotEmpty需要获取takeLock？**
```java
private void signalNotEmpty() {
    takeLock.lock();  // 必须获取takeLock
    try {
        notEmpty.signal();  // notEmpty是takeLock的Condition
    } finally {
        takeLock.unlock();
    }
}

// Condition必须在对应的锁保护下使用
```

4. **为什么要signal(notFull)？**
```java
// 入队后，如果还有空间
if (c + 1 < capacity)
    notFull.signal();

// 唤醒其他等待的生产者
// 提高并发度
```

---

### 3.3 出队操作（take）

#### 完整流程图

```
take() 流程
    │
    ▼
┌─────────────┐
│ 获取takeLock│
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
│await │ │ 出队节点  │
└──┬───┘ │ dequeue()│
   │     └────┬─────┘
   │          │
   │ 被唤醒    │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │ count--  │
   └────┬─────┘
        │
        ▼
   ┌──────────────┐
   │ c == capacity?│ ← 之前是满队列？
   └────┬─────────┘
        │
       Yes
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notFull) │
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │释放takeLock│
   └────┬─────┘
        │
        ▼
   ┌──────────────┐
   │ c > 1?       │ ← 还有元素？
   └────┬──────────┘
        │
       Yes
        │
        ▼
   ┌──────────┐
   │ signal   │
   │(notEmpty)│
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
    E x;
    int c = -1;
    final AtomicInteger count = this.count;
    final ReentrantLock takeLock = this.takeLock;
    
    // 1. 获取takeLock
    takeLock.lockInterruptibly();
    
    try {
        // 2. 如果队列空，等待
        while (count.get() == 0) {
            notEmpty.await();
        }
        
        // 3. 出队
        x = dequeue();
        
        // 4. count原子递减，返回旧值
        c = count.getAndDecrement();
        
        // 5. 如果出队后还有元素，唤醒其他消费者
        if (c > 1)
            notEmpty.signal();
            
    } finally {
        // 6. 释放takeLock
        takeLock.unlock();
    }
    
    // 7. 如果之前队列是满的，唤醒生产者
    if (c == capacity)
        signalNotFull();
    
    return x;
}

// 出队核心逻辑
private E dequeue() {
    // head是哨兵节点，head.next是第一个元素
    Node<E> h = head;
    Node<E> first = h.next;
    h.next = h;  // 帮助GC
    head = first;  // 移动head
    E x = first.item;
    first.item = null;  // 清空引用
    return x;
}

// 唤醒生产者
private void signalNotFull() {
    final ReentrantLock putLock = this.putLock;
    putLock.lock();
    try {
        notFull.signal();
    } finally {
        putLock.unlock();
    }
}
```

**关键点**：

1. **哨兵节点的妙用**
```java
// 初始：head是哨兵
head(哨兵) -> [A] -> [B] -> null

// 出队后：
head([A]) -> [B] -> null
// 原来的head.next变成新的head
// 新head的item被清空，成为新的哨兵
```

2. **h.next = h的作用**
```java
h.next = h;  // 自引用

// 帮助GC：
// 旧head节点自引用，断开与链表的连接
// 可以被快速回收
```

---

### 3.4 双锁协调机制

#### 关键问题：如何协调两把锁？

**问题场景**：
```java
// 生产者在putLock保护下入队
putLock.lock();
enqueue(node);
count.getAndIncrement();
putLock.unlock();

// 消费者在takeLock保护下出队
takeLock.lock();
dequeue();
count.getAndDecrement();
takeLock.unlock();

// 问题：count被两个锁共享，如何保证一致性？
```

**解决方案：AtomicInteger**
```java
private final AtomicInteger count = new AtomicInteger();

// 原子操作，无需额外同步
count.getAndIncrement();  // 生产者
count.getAndDecrement();  // 消费者
```

#### 唤醒机制

**场景1：入队后唤醒消费者**
```java
public void put(E e) {
    putLock.lock();
    try {
        enqueue(node);
        c = count.getAndIncrement();
        // ...
    } finally {
        putLock.unlock();
    }
    
    // 在putLock外唤醒消费者
    if (c == 0)
        signalNotEmpty();  // 需要获取takeLock
}
```

**为什么在锁外唤醒？**
- 避免死锁：putLock -> takeLock
- 减少锁持有时间

**场景2：出队后唤醒生产者**
```java
public E take() {
    takeLock.lock();
    try {
        x = dequeue();
        c = count.getAndDecrement();
        // ...
    } finally {
        takeLock.unlock();
    }
    
    // 在takeLock外唤醒生产者
    if (c == capacity)
        signalNotFull();  // 需要获取putLock
}
```

---

### 3.5 非阻塞操作

#### offer方法

```java
public boolean offer(E e) {
    if (e == null) throw new NullPointerException();
    
    final AtomicInteger count = this.count;
    // 队列满，直接返回false
    if (count.get() == capacity)
        return false;
    
    int c = -1;
    Node<E> node = new Node<E>(e);
    final ReentrantLock putLock = this.putLock;
    putLock.lock();
    try {
        // 再次检查（double-check）
        if (count.get() < capacity) {
            enqueue(node);
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        }
    } finally {
        putLock.unlock();
    }
    
    if (c == 0)
        signalNotEmpty();
    
    return c >= 0;
}
```

**为什么需要double-check？**
```java
// 第一次检查（无锁）
if (count.get() == capacity)
    return false;

// 获取锁后再次检查
putLock.lock();
if (count.get() < capacity) {
    // 可能其他线程已经入队
}
```

#### poll方法

```java
public E poll() {
    final AtomicInteger count = this.count;
    // 队列空，直接返回null
    if (count.get() == 0)
        return null;
    
    E x = null;
    int c = -1;
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lock();
    try {
        // 再次检查
        if (count.get() > 0) {
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        }
    } finally {
        takeLock.unlock();
    }
    
    if (c == capacity)
        signalNotFull();
    
    return x;
}
```

---

## 四、核心设计精髓

### 4.1 双锁分离的精妙之处

#### 设计1：读写锁分离

**为什么能分离？**
```java
// 链表天然支持头尾分离
head -> [A] -> [B] -> [C] -> tail
 ↑                            ↑
 takeLock保护              putLock保护

// 操作互不干扰
```

**性能提升**：
```java
// 单锁：串行
生产者: ----[入队]----
消费者:              ----[出队]----

// 双锁：并行
生产者: ----[入队]----
消费者: ----[出队]----
```

#### 设计2：AtomicInteger共享计数

**为什么用原子类？**
```java
// count被两个锁共享
private final AtomicInteger count;

// 无需额外同步
putLock.lock();
count.getAndIncrement();  // 原子操作
putLock.unlock();

takeLock.lock();
count.getAndDecrement();  // 原子操作
takeLock.unlock();
```

**如果用int会怎样？**
```java
// ❌ 错误：int需要额外同步
private int count;

// 需要同时获取两把锁才能修改count
// 失去了双锁的意义
```

#### 设计3：锁外唤醒

**为什么在锁外唤醒？**
```java
public void put(E e) {
    putLock.lock();
    try {
        // 入队逻辑
    } finally {
        putLock.unlock();
    }
    
    // 在锁外唤醒消费者
    if (c == 0)
        signalNotEmpty();  // 获取takeLock
}
```

**优势**：
1. 避免死锁
2. 减少锁持有时间
3. 提高并发度

---

### 4.2 源码中的巧妙设计

#### 技巧1：哨兵节点

```java
// 初始化时创建哨兵
last = head = new Node<E>(null);

// 优势：
// 1. 简化边界条件
// 2. 避免空指针检查
// 3. 统一处理逻辑
```

**对比无哨兵**：
```java
// ❌ 无哨兵：需要特殊处理
if (head == null) {
    head = last = newNode;
} else {
    last.next = newNode;
    last = newNode;
}

// ✅ 有哨兵：统一处理
last = last.next = newNode;
```

#### 技巧2：自引用帮助GC

```java
private E dequeue() {
    Node<E> h = head;
    Node<E> first = h.next;
    h.next = h;  // 自引用，帮助GC
    head = first;
    // ...
}
```

**为什么自引用？**
```java
// 旧head断开与链表的连接
h.next = h;

// GC更容易识别为垃圾
// 可以更快回收
```

#### 技巧3：局部变量缓存

```java
public void put(E e) {
    final ReentrantLock putLock = this.putLock;  // 缓存
    final AtomicInteger count = this.count;      // 缓存
    
    putLock.lock();
    // 使用局部变量，减少字段访问
}
```

---

## 五、应用场景与最佳实践

### 5.1 适用场景

#### 场景1：高并发生产-消费

```java
// 高并发日志系统
BlockingQueue<LogEvent> logQueue = new LinkedBlockingQueue<>(10000);

// 多个生产者
ExecutorService producers = Executors.newFixedThreadPool(10);
for (int i = 0; i < 10; i++) {
    producers.submit(() -> {
        while (true) {
            logQueue.put(generateLog());
        }
    });
}

// 多个消费者
ExecutorService consumers = Executors.newFixedThreadPool(5);
for (int i = 0; i < 5; i++) {
    consumers.submit(() -> {
        while (true) {
            LogEvent event = logQueue.take();
            writeToFile(event);
        }
    });
}
```

#### 场景2：线程池任务队列

```java
// Executors.newFixedThreadPool内部使用
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10, 20, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>()  // 无界队列
);
```

#### 场景3：消息队列

```java
// 简单的消息队列实现
public class SimpleMessageQueue {
    private final BlockingQueue<Message> queue = 
        new LinkedBlockingQueue<>(1000);
    
    public void send(Message msg) throws InterruptedException {
        queue.put(msg);
    }
    
    public Message receive() throws InterruptedException {
        return queue.take();
    }
}
```

---

### 5.2 常见陷阱

#### 陷阱1：无界队列OOM

```java
// ❌ 危险：默认无界
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

// 如果生产速度 > 消费速度，内存耗尽
while (true) {
    queue.put(new Task());  // 永不阻塞，直到OOM
}

// ✅ 正确：指定容量
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(10000);
```

#### 陷阱2：内存占用高

```java
// LinkedBlockingQueue每个元素都有Node包装
static class Node<E> {
    E item;
    Node<E> next;  // 额外8字节（64位JVM）
}

// 如果元素很小，Node开销占比大
// 考虑使用ArrayBlockingQueue
```

#### 陷阱3：GC压力

```java
// 频繁创建Node对象
queue.put(item);  // 创建Node
queue.take();     // Node变为垃圾

// 高频场景下GC压力大
// 考虑对象池或ArrayBlockingQueue
```

---

### 5.3 性能优化

#### 优化1：合理设置容量

```java
// 根据业务评估容量
// 容量 = 峰值TPS × 平均处理时间 × 安全系数
int capacity = 10000 * 0.1 * 2;  // 2000
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(capacity);
```

#### 优化2：批量操作

```java
// ✅ 批量消费
List<Task> batch = new ArrayList<>(100);
queue.drainTo(batch, 100);  // 一次取出最多100个
for (Task task : batch) {
    process(task);
}
```

#### 优化3：监控队列大小

```java
// 监控队列积压
int size = queue.size();
if (size > capacity * 0.8) {
    // 告警：队列积压严重
    alert("Queue is 80% full");
}
```

---

## 六、对比分析

### 6.1 LinkedBlockingQueue vs ArrayBlockingQueue

| 特性 | LinkedBlockingQueue | ArrayBlockingQueue |
|-----|---------------------|-------------------|
| **底层结构** | 链表 | 数组 |
| **锁机制** | 双锁（putLock + takeLock） | 单锁 |
| **容量** | 可选（默认无界） | 固定 |
| **内存占用** | 高（Node开销） | 低 |
| **GC压力** | 高（频繁创建Node） | 低 |
| **缓存性能** | 差（内存不连续） | 好（内存连续） |
| **吞吐量** | 高（读写并发） | 中（读写互斥） |
| **适用场景** | 高并发、吞吐优先 | 固定容量、内存敏感 |

### 6.2 性能测试对比

```java
// 测试场景：10个生产者 + 10个消费者
// 结果（仅供参考）：
// LinkedBlockingQueue: 500万 ops/s
// ArrayBlockingQueue:  300万 ops/s

// LinkedBlockingQueue吞吐量约为ArrayBlockingQueue的1.6倍
```

### 6.3 何时选择LinkedBlockingQueue？

**选择理由**：
1. ✅ 高并发场景
2. ✅ 追求吞吐量
3. ✅ 生产和消费速度相近
4. ✅ 需要动态容量

**不选择理由**：
1. ❌ 内存敏感
2. ❌ 元素很小（Node开销占比大）
3. ❌ 需要严格容量限制
4. ❌ GC敏感

---

## 七、总结

### 核心要点

1. **设计思想**：
   - 双锁分离，读写并发
   - AtomicInteger共享计数
   - 哨兵节点简化逻辑

2. **性能优势**：
   - 吞吐量高（约为ArrayBlockingQueue的1.6倍）
   - 生产和消费可并发执行

3. **代价**：
   - 内存占用高（Node开销）
   - GC压力大
   - 实现复杂

4. **适用场景**：
   - 高并发生产-消费
   - 线程池任务队列
   - 消息队列

5. **注意事项**：
   - 指定容量，避免OOM
   - 监控队列大小
   - 考虑GC影响

---

**下一章**：PriorityBlockingQueue详解 - 优先级队列的实现
