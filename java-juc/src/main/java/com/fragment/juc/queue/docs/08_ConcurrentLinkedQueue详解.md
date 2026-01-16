# ConcurrentLinkedQueue详解

> **本章目标**：深入理解ConcurrentLinkedQueue的CAS无锁算法、非阻塞实现、性能优化技巧及应用场景

---

## 一、为什么需要ConcurrentLinkedQueue？

### 问题1：阻塞队列的性能瓶颈

#### 阻塞队列的问题

```java
// LinkedBlockingQueue：基于锁
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

// 入队：需要获取putLock
queue.put(task);  // 锁竞争

// 出队：需要获取takeLock
queue.take();     // 锁竞争

// 问题：
// 1. 锁竞争（高并发下性能下降）
// 2. 线程阻塞（上下文切换开销）
// 3. 死锁风险（锁的使用不当）
```

#### 无锁队列的优势

```java
// ConcurrentLinkedQueue：基于CAS
Queue<Task> queue = new ConcurrentLinkedQueue<>();

// 入队：CAS操作，无锁
queue.offer(task);  // 自旋重试，无阻塞

// 出队：CAS操作，无锁
queue.poll();       // 自旋重试，无阻塞

// 优势：
// 1. 无锁竞争（CAS原子操作）
// 2. 无线程阻塞（自旋重试）
// 3. 高并发性能好
```

---

### 问题2：ConcurrentLinkedQueue vs LinkedBlockingQueue

| 特性 | ConcurrentLinkedQueue | LinkedBlockingQueue |
|-----|----------------------|---------------------|
| **同步机制** | CAS无锁 | ReentrantLock |
| **阻塞语义** | 非阻塞 | 阻塞 |
| **容量** | 无界 | 可选有界 |
| **性能** | 高（无锁） | 中（锁竞争） |
| **适用场景** | 高并发、非阻塞 | 生产者-消费者 |

---

### 问题3：什么时候选择ConcurrentLinkedQueue？

**选择ConcurrentLinkedQueue**：
- ✅ 高并发场景
- ✅ 不需要阻塞语义
- ✅ 追求极致性能
- ✅ 无界队列可接受

**不选择ConcurrentLinkedQueue**：
- ❌ 需要阻塞等待
- ❌ 需要容量限制
- ❌ 需要背压机制

---

## 二、核心数据结构

### 2.1 类定义

```java
public class ConcurrentLinkedQueue<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {
    
    // 头节点
    private transient volatile Node<E> head;
    
    // 尾节点
    private transient volatile Node<E> tail;
    
    // 构造方法
    public ConcurrentLinkedQueue() {
        head = tail = new Node<E>(null);  // 哨兵节点
    }
}
```

### 2.2 节点结构

```java
private static class Node<E> {
    volatile E item;
    volatile Node<E> next;
    
    Node(E item) {
        UNSAFE.putObject(this, itemOffset, item);
    }
    
    // CAS操作
    boolean casItem(E cmp, E val) {
        return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
    }
    
    void lazySetNext(Node<E> val) {
        UNSAFE.putOrderedObject(this, nextOffset, val);
    }
    
    boolean casNext(Node<E> cmp, Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
    }
}
```

**关键点**：
- `item`和`next`都是`volatile`，保证可见性
- 使用`UNSAFE`进行CAS操作
- `lazySetNext`：延迟设置，性能优化

### 2.3 链表结构

```
初始状态（哨兵节点）：
head -> [null] -> null
         ↑
         tail

插入元素A后：
head -> [null] -> [A] -> null
         ↑         ↑
         (不变)    tail

插入元素B后：
head -> [null] -> [A] -> [B] -> null
         ↑                ↑
         (不变)           tail

取出元素后：
head -> [A] -> [B] -> null
         ↑      ↑
         (移动) tail
```

---

## 三、核心操作流程

### 3.1 入队操作（offer）

#### 流程图

```
offer(E e) 流程
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
│ 循环开始     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取tail    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取tail.next│
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
  null    非null
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│CAS设 │ │tail已过期 │
│置next│ │更新tail  │
└──┬───┘ └────┬─────┘
   │          │
  成功        │
   │          │
   ▼          │
┌──────┐      │
│CAS更 │      │
│新tail│      │
└──┬───┘      │
   │          │
   └────┬─────┘
        │
        ▼
   ┌─────────┐
   │ 返回true│
   └─────────┘
```

#### 源码分析

```java
public boolean offer(E e) {
    checkNotNull(e);
    
    // 1. 创建新节点
    final Node<E> newNode = new Node<E>(e);
    
    // 2. 自旋，直到成功
    for (Node<E> t = tail, p = t;;) {
        Node<E> q = p.next;
        
        // 情况1：p是尾节点（q == null）
        if (q == null) {
            // CAS设置p.next = newNode
            if (p.casNext(null, newNode)) {
                // 成功后，如果p != t，更新tail
                if (p != t)
                    casTail(t, newNode);  // 允许失败
                return true;
            }
            // CAS失败，继续循环
        }
        // 情况2：p已被删除（自引用）
        else if (p == q)
            p = (t != (t = tail)) ? t : head;
        // 情况3：tail已过期，向后查找
        else
            p = (p != t && t != (t = tail)) ? t : q;
    }
}
```

**关键点分析**：

1. **为什么不每次都更新tail？**
```java
if (p.casNext(null, newNode)) {
    // 只有当p != t时才更新tail
    if (p != t)
        casTail(t, newNode);
    return true;
}

// 优化：减少CAS操作
// tail允许滞后，不影响正确性
// 每隔一个节点才更新tail
```

**tail的滞后**：
```
插入A：
head -> [null] -> [A] -> null
                   ↑
                   tail（更新）

插入B：
head -> [null] -> [A] -> [B] -> null
                   ↑      ↑
                   tail   实际尾部（tail滞后）

插入C：
head -> [null] -> [A] -> [B] -> [C] -> null
                                 ↑
                                 tail（更新）
```

2. **自引用的作用**
```java
// 删除节点时，设置为自引用
p.next = p;  // 自引用

// 检测自引用
if (p == q)
    p = head;  // 从head重新开始
```

---

### 3.2 出队操作（poll）

#### 流程图

```
poll() 流程
    │
    ▼
┌─────────────┐
│ 循环开始     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取head    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 获取head.item│
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
  null    非null
   │       │
   ▼       ▼
┌──────┐ ┌──────────┐
│head  │ │CAS设置   │
│已过期│ │item=null │
└──┬───┘ └────┬─────┘
   │          │
   │         成功
   │          │
   │          ▼
   │     ┌──────────┐
   │     │更新head  │
   │     └────┬─────┘
   │          │
   └────┬─────┘
        │
        ▼
   ┌─────────┐
   │ 返回item│
   └─────────┘
```

#### 源码分析

```java
public E poll() {
    restartFromHead:
    for (;;) {
        for (Node<E> h = head, p = h, q;;) {
            E item = p.item;
            
            // 情况1：p有元素，尝试CAS删除
            if (item != null && p.casItem(item, null)) {
                // 成功后，如果p != h，更新head
                if (p != h)
                    updateHead(h, ((q = p.next) != null) ? q : p);
                return item;
            }
            // 情况2：p是尾节点（队列空）
            else if ((q = p.next) == null) {
                updateHead(h, p);
                return null;
            }
            // 情况3：p已被删除（自引用）
            else if (p == q)
                continue restartFromHead;
            // 情况4：向后查找
            else
                p = q;
        }
    }
}

final void updateHead(Node<E> h, Node<E> p) {
    if (h != p && casHead(h, p))
        h.lazySetNext(h);  // 自引用，帮助GC
}
```

**关键点**：

1. **head的滞后**
```java
// 类似tail，head也允许滞后
// 减少CAS操作

出队A：
head -> [A] -> [B] -> [C] -> null
         ↑
         (item设为null，head不变)

出队B：
head -> [A] -> [B] -> [C] -> null
                ↑
                (item设为null，head更新到B)
```

2. **自引用帮助GC**
```java
h.lazySetNext(h);  // 旧head自引用

// 断开与链表的连接
// 可以被快速GC回收
```

---

### 3.3 其他操作

#### peek方法

```java
public E peek() {
    restartFromHead:
    for (;;) {
        for (Node<E> h = head, p = h, q;;) {
            E item = p.item;
            
            // 找到第一个非null元素
            if (item != null || (q = p.next) == null) {
                updateHead(h, p);
                return item;
            }
            else if (p == q)
                continue restartFromHead;
            else
                p = q;
        }
    }
}
```

#### size方法

```java
public int size() {
    int count = 0;
    // 遍历整个链表
    for (Node<E> p = first(); p != null; p = succ(p))
        if (p.item != null)
            if (++count == Integer.MAX_VALUE)
                break;
    return count;
}
```

**注意**：
- `size()`不是常量时间操作，是O(n)
- 高并发下，返回值可能不准确
- 尽量避免频繁调用`size()`

---

## 四、核心设计精髓

### 4.1 CAS无锁算法

#### Michael-Scott队列算法

ConcurrentLinkedQueue基于Michael-Scott算法实现，这是一个经典的无锁队列算法。

**核心思想**：
1. 使用CAS操作保证原子性
2. 允许tail和head滞后（减少CAS）
3. 自旋重试（失败后重试）

**伪代码**：
```
enqueue(x):
    node = new Node(x)
    loop:
        tail = this.tail
        next = tail.next
        if tail == this.tail:  // tail未变化
            if next == null:   // tail是真正的尾节点
                if CAS(tail.next, null, node):
                    CAS(this.tail, tail, node)  // 允许失败
                    return
            else:  // tail已过期
                CAS(this.tail, tail, next)

dequeue():
    loop:
        head = this.head
        tail = this.tail
        next = head.next
        if head == this.head:
            if head == tail:  // 队列空
                if next == null:
                    return null
                CAS(this.tail, tail, next)
            else:
                value = next.value
                if CAS(this.head, head, next):
                    return value
```

---

### 4.2 松弛策略（Slack）

#### 什么是松弛？

```java
// 严格策略：每次操作都更新head/tail
offer(A): tail -> A
offer(B): tail -> B
offer(C): tail -> C

// 松弛策略：允许tail滞后
offer(A): tail -> A
offer(B): tail -> A (滞后)
offer(C): tail -> C (更新)
```

**优势**：
- 减少CAS操作（CAS有开销）
- 提高并发度（减少竞争）
- 性能提升

**代价**：
- 逻辑复杂（需要处理滞后）
- 遍历可能更长

---

### 4.3 ABA问题的解决

#### 什么是ABA问题？

```java
// 线程1：
Node oldTail = tail;  // A
// ... 被挂起

// 线程2：
tail = B;  // A -> B
tail = A;  // B -> A（又变回A）

// 线程1恢复：
CAS(tail, oldTail, newNode);  // 成功！但tail已经变化过
```

**ConcurrentLinkedQueue的解决**：
```java
// 不依赖引用相等性，而是依赖链表结构
for (Node<E> t = tail, p = t;;) {
    Node<E> q = p.next;
    if (q == null) {
        // 检查p.next，而不是tail
        if (p.casNext(null, newNode)) {
            // ...
        }
    }
}

// 即使tail变化，只要p.next正确，就能成功
```

---

### 4.4 内存模型保证

#### volatile的作用

```java
private static class Node<E> {
    volatile E item;      // 保证可见性
    volatile Node<E> next; // 保证可见性
}

private transient volatile Node<E> head;  // 保证可见性
private transient volatile Node<E> tail;  // 保证可见性
```

**happens-before关系**：
```java
// 线程1：入队
newNode.item = e;        // 写volatile
tail.next = newNode;     // 写volatile

// 线程2：出队
Node p = head.next;      // 读volatile
E item = p.item;         // 读volatile

// JMM保证：线程1的写 happens-before 线程2的读
```

---

## 五、应用场景与最佳实践

### 5.1 高并发消息队列

```java
public class HighConcurrencyMessageQueue {
    private final Queue<Message> queue = new ConcurrentLinkedQueue<>();
    
    // 多个生产者
    public void produce(Message msg) {
        queue.offer(msg);  // 无锁，高并发
    }
    
    // 多个消费者
    public Message consume() {
        return queue.poll();  // 无锁，高并发
    }
}
```

**优势**：
- 无锁，高并发性能好
- 无阻塞，适合高吞吐

**劣势**：
- 无阻塞语义（需要轮询）
- 无容量限制（可能OOM）

---

### 5.2 任务缓冲池

```java
public class TaskBufferPool {
    private final Queue<Task> buffer = new ConcurrentLinkedQueue<>();
    
    // 提交任务
    public void submit(Task task) {
        buffer.offer(task);
    }
    
    // 工作线程获取任务
    public Task getTask() {
        Task task = buffer.poll();
        if (task == null) {
            // 轮询或等待
            Thread.yield();
        }
        return task;
    }
}
```

---

### 5.3 常见陷阱

#### 陷阱1：频繁调用size()

```java
// ❌ 错误：频繁调用size
while (queue.size() > 0) {  // O(n)操作
    Task task = queue.poll();
}

// ✅ 正确：直接poll
Task task;
while ((task = queue.poll()) != null) {
    // 处理
}
```

#### 陷阱2：期望阻塞语义

```java
// ❌ 错误：期望阻塞
Task task = queue.poll();  // 返回null，不阻塞

// ✅ 正确：使用阻塞队列
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
Task task = queue.take();  // 阻塞
```

#### 陷阱3：无界队列OOM

```java
// ❌ 危险：无界队列
Queue<Task> queue = new ConcurrentLinkedQueue<>();
while (true) {
    queue.offer(new Task());  // 可能OOM
}

// ✅ 正确：监控队列大小
if (queue.size() < MAX_SIZE) {
    queue.offer(task);
} else {
    // 拒绝或等待
}
```

---

## 六、性能对比

### 6.1 ConcurrentLinkedQueue vs LinkedBlockingQueue

```java
// 测试场景：10个生产者 + 10个消费者，100万次操作

// ConcurrentLinkedQueue
Queue<Integer> queue = new ConcurrentLinkedQueue<>();
// 结果：约800万 ops/s

// LinkedBlockingQueue
BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
// 结果：约500万 ops/s

// ConcurrentLinkedQueue性能约为LinkedBlockingQueue的1.6倍
```

**性能优势**：
- 无锁，减少上下文切换
- 无阻塞，减少线程挂起/唤醒
- 高并发下性能更好

---

## 七、总结

### 核心要点

1. **设计思想**：
   - CAS无锁算法
   - Michael-Scott队列
   - 松弛策略（tail/head滞后）

2. **关键实现**：
   - volatile保证可见性
   - CAS保证原子性
   - 自旋重试（无阻塞）

3. **适用场景**：
   - 高并发、非阻塞
   - 无界队列可接受
   - 追求极致性能

4. **注意事项**：
   - 无阻塞语义
   - size()是O(n)操作
   - 无容量限制（OOM风险）

---

**下一章**：队列选型与对比 - 如何选择合适的并发队列
