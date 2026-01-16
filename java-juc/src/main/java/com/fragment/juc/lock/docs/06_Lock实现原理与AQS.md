# 第六章：Lock实现原理与AQS源码分析

> **学习目标**：深入理解Lock的底层实现原理，掌握AQS框架

---

## 一、ReentrantLock的实现架构

### 1.1 整体架构

```
ReentrantLock
    ├── Sync (抽象内部类，继承AQS)
    │   ├── NonfairSync (非公平锁实现)
    │   └── FairSync (公平锁实现)
    └── AQS (AbstractQueuedSynchronizer)
        ├── state (同步状态)
        ├── head (等待队列头节点)
        └── tail (等待队列尾节点)
```

### 1.2 核心类关系

```java
public class ReentrantLock implements Lock {
    // 同步器
    private final Sync sync;
    
    // 构造器：选择公平或非公平
    public ReentrantLock() {
        sync = new NonfairSync(); // 默认非公平
    }
    
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }
    
    // 抽象同步器（基于AQS）
    abstract static class Sync extends AbstractQueuedSynchronizer {
        // 子类实现具体的加锁逻辑
        abstract void lock();
    }
    
    // 非公平锁实现
    static final class NonfairSync extends Sync {
        final void lock() {
            // 非公平：直接尝试CAS获取锁
            if (compareAndSetState(0, 1))
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }
    }
    
    // 公平锁实现
    static final class FairSync extends Sync {
        final void lock() {
            // 公平：直接调用AQS的acquire
            acquire(1);
        }
    }
}
```

---

## 二、AQS核心原理

### 2.1 AQS是什么？

**AbstractQueuedSynchronizer（抽象队列同步器）**：

- Doug Lea设计的同步器框架
- 用于构建锁和同步组件的基础框架
- 使用一个int类型的state表示同步状态
- 使用FIFO队列管理等待线程

### 2.2 AQS的核心字段

```java
public abstract class AbstractQueuedSynchronizer {
    /**
     * 同步状态
     * - 0：未锁定
     * - >0：锁定（可重入次数）
     */
    private volatile int state;
    
    /**
     * 等待队列的头节点
     */
    private transient volatile Node head;
    
    /**
     * 等待队列的尾节点
     */
    private transient volatile Node tail;
    
    /**
     * 独占模式下持有锁的线程
     */
    private transient Thread exclusiveOwnerThread;
}
```

### 2.3 Node节点结构

```java
static final class Node {
    // 共享模式
    static final Node SHARED = new Node();
    // 独占模式
    static final Node EXCLUSIVE = null;
    
    // 节点状态
    static final int CANCELLED =  1;  // 取消
    static final int SIGNAL    = -1;  // 需要唤醒后继节点
    static final int CONDITION = -2;  // 在条件队列中
    static final int PROPAGATE = -3;  // 共享模式下传播
    
    volatile int waitStatus;          // 等待状态
    volatile Node prev;               // 前驱节点
    volatile Node next;               // 后继节点
    volatile Thread thread;           // 等待的线程
    Node nextWaiter;                  // 条件队列的下一个节点
}
```

---

## 三、加锁流程源码分析

### 3.1 非公平锁加锁流程

```java
// 步骤1：ReentrantLock.lock()
public void lock() {
    sync.lock();
}

// 步骤2：NonfairSync.lock()
final void lock() {
    // 非公平：直接尝试CAS获取锁
    if (compareAndSetState(0, 1))
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1); // 获取失败，进入AQS流程
}

// 步骤3：AQS.acquire()
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

**流程图**：

```
lock()
  ↓
CAS尝试获取锁
  ↓
成功？
  ├─ 是 → 设置exclusiveOwnerThread → 返回
  └─ 否 → acquire(1)
           ↓
         tryAcquire() 再次尝试
           ↓
         失败？
           ├─ 是 → addWaiter() 加入等待队列
           │        ↓
           │      acquireQueued() 自旋获取锁
           │        ↓
           │      park() 阻塞线程
           └─ 否 → 返回
```

### 3.2 tryAcquire() 源码分析

```java
// 非公平锁的tryAcquire
protected final boolean tryAcquire(int acquires) {
    return nonfairTryAcquire(acquires);
}

final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    
    // 情况1：锁未被占用
    if (c == 0) {
        // 非公平：直接CAS尝试获取
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    // 情况2：当前线程已持有锁（可重入）
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires; // state + 1
        if (nextc < 0) // 溢出检查
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    // 情况3：锁被其他线程占用
    return false;
}
```

**公平锁的tryAcquire**：

```java
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    
    if (c == 0) {
        // 公平：先检查队列中是否有等待线程
        if (!hasQueuedPredecessors() &&
            compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0)
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}

// 检查是否有前驱节点在等待
public final boolean hasQueuedPredecessors() {
    Node t = tail;
    Node h = head;
    Node s;
    return h != t &&
        ((s = h.next) == null || s.thread != Thread.currentThread());
}
```

### 3.3 addWaiter() 加入等待队列

```java
private Node addWaiter(Node mode) {
    // 创建新节点
    Node node = new Node(Thread.currentThread(), mode);
    
    // 快速尝试：直接CAS加到队尾
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        if (compareAndSetTail(pred, node)) {
            pred.next = node;
            return node;
        }
    }
    
    // 快速失败，使用完整的enq()方法
    enq(node);
    return node;
}

private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        // 队列为空，初始化
        if (t == null) {
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            // CAS加入队尾
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

**队列结构**：

```
初始状态（空队列）：
head = null, tail = null

第一个线程加入：
head → [dummy] ← tail
          ↓
       [Thread-1]

第二个线程加入：
head → [dummy] → [Thread-1] ← tail
                     ↓
                 [Thread-2]
```

### 3.4 acquireQueued() 自旋获取锁

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        // 自旋
        for (;;) {
            final Node p = node.predecessor(); // 获取前驱节点
            
            // 如果前驱是head，尝试获取锁
            if (p == head && tryAcquire(arg)) {
                setHead(node);      // 获取成功，设置为新head
                p.next = null;      // 帮助GC
                failed = false;
                return interrupted;
            }
            
            // 判断是否应该阻塞
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

**关键方法**：

```java
// 判断是否应该阻塞
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    
    // 前驱状态为SIGNAL，可以安全阻塞
    if (ws == Node.SIGNAL)
        return true;
    
    // 前驱被取消，跳过
    if (ws > 0) {
        do {
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {
        // 设置前驱状态为SIGNAL
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}

// 阻塞线程
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this); // 阻塞当前线程
    return Thread.interrupted();
}
```

---

## 四、解锁流程源码分析

### 4.1 unlock() 流程

```java
// 步骤1：ReentrantLock.unlock()
public void unlock() {
    sync.release(1);
}

// 步骤2：AQS.release()
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h); // 唤醒后继节点
        return true;
    }
    return false;
}
```

### 4.2 tryRelease() 源码分析

```java
protected final boolean tryRelease(int releases) {
    int c = getState() - releases; // state - 1
    
    // 检查是否是持有锁的线程
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    
    boolean free = false;
    // state减到0，完全释放锁
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```

**可重入锁的释放**：

```
初始：state = 3 (重入3次)
unlock() → state = 2
unlock() → state = 1
unlock() → state = 0 (完全释放，唤醒后继)
```

### 4.3 unparkSuccessor() 唤醒后继

```java
private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);
    
    // 找到下一个需要唤醒的节点
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        // 从尾部向前找第一个有效节点
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    
    // 唤醒线程
    if (s != null)
        LockSupport.unpark(s.thread);
}
```

---

## 五、关键技术点

### 5.1 CAS操作

```java
// 修改state
protected final boolean compareAndSetState(int expect, int update) {
    return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}

// 修改head
private final boolean compareAndSetHead(Node update) {
    return unsafe.compareAndSwapObject(this, headOffset, null, update);
}

// 修改tail
private final boolean compareAndSetTail(Node expect, Node update) {
    return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
}
```

### 5.2 LockSupport阻塞与唤醒

```java
// 阻塞当前线程
LockSupport.park(this);

// 唤醒指定线程
LockSupport.unpark(thread);
```

**原理**：
- 基于Unsafe类的park/unpark
- 每个线程有一个许可（permit）
- park()消费许可，unpark()提供许可
- 许可最多只有一个

### 5.3 自旋优化

```java
// acquireQueued中的自旋
for (;;) {
    final Node p = node.predecessor();
    // 只有前驱是head才尝试获取锁
    if (p == head && tryAcquire(arg)) {
        // 获取成功
        setHead(node);
        p.next = null;
        return interrupted;
    }
    // 失败后阻塞
    if (shouldParkAfterFailedAcquire(p, node) &&
        parkAndCheckInterrupt())
        interrupted = true;
}
```

**为什么只有前驱是head才尝试？**
- 减少无效的CAS操作
- 保证FIFO顺序（公平性）
- 避免惊群效应

---

## 六、公平锁 vs 非公平锁实现差异

### 6.1 加锁时的差异

```java
// 非公平锁：直接抢
final void lock() {
    // 1. 直接CAS尝试获取
    if (compareAndSetState(0, 1))
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1);
}

// 公平锁：先排队
final void lock() {
    // 直接进入acquire流程
    acquire(1);
}
```

### 6.2 tryAcquire的差异

```java
// 非公平锁：不检查队列
if (c == 0) {
    if (compareAndSetState(0, acquires)) {
        setExclusiveOwnerThread(current);
        return true;
    }
}

// 公平锁：检查队列
if (c == 0) {
    // 先检查是否有前驱在等待
    if (!hasQueuedPredecessors() &&
        compareAndSetState(0, acquires)) {
        setExclusiveOwnerThread(current);
        return true;
    }
}
```

### 6.3 性能对比

| 特性 | 非公平锁 | 公平锁 |
|------|---------|--------|
| **吞吐量** | 高 | 低 |
| **延迟** | 低 | 高 |
| **饥饿** | 可能 | 不会 |
| **上下文切换** | 少 | 多 |
| **适用场景** | 高并发 | 需要公平 |

---

## 七、完整流程图

### 7.1 加锁流程

```
Thread调用lock()
    ↓
尝试CAS获取锁（非公平）
    ↓
成功？
├─ 是 → 设置owner → 返回
└─ 否 → tryAcquire()
         ↓
       再次尝试获取
         ↓
       成功？
       ├─ 是 → 返回
       └─ 否 → addWaiter()
                ↓
              加入等待队列
                ↓
              acquireQueued()
                ↓
              自旋尝试获取
                ↓
              前驱是head？
              ├─ 是 → tryAcquire()
              │        ↓
              │      成功？
              │      ├─ 是 → 设置为新head → 返回
              │      └─ 否 → park()阻塞
              └─ 否 → park()阻塞
```

### 7.2 解锁流程

```
Thread调用unlock()
    ↓
tryRelease()
    ↓
state - 1
    ↓
state == 0？
├─ 是 → 完全释放
│        ↓
│      清除owner
│        ↓
│      unparkSuccessor()
│        ↓
│      找到后继节点
│        ↓
│      unpark()唤醒
│        ↓
│      被唤醒的线程继续acquireQueued()
│        ↓
│      尝试获取锁
└─ 否 → 返回（还有重入）
```

---

## 八、核心问题解答

### Q1: 为什么需要dummy head节点？

**答案**：
- 简化边界条件处理
- head节点不存储线程信息
- head.next才是第一个等待的线程
- 便于唤醒操作

### Q2: 为什么从tail向前遍历？

**答案**：
```java
// addWaiter中的操作顺序
node.prev = pred;              // 1. 先设置prev
if (compareAndSetTail(pred, node)) { // 2. CAS设置tail
    pred.next = node;          // 3. 最后设置next
    return node;
}
```
- prev是先设置的，一定有效
- next是后设置的，可能还未完成
- 从后向前遍历保证不会遗漏节点

### Q3: state为什么用volatile？

**答案**：
- 保证可见性：一个线程修改，其他线程立即可见
- 配合CAS使用：CAS保证原子性
- 不需要synchronized：减少性能开销

### Q4: 为什么只有前驱是head才尝试获取锁？

**答案**：
- 保证FIFO顺序
- 减少无效的CAS操作
- 避免惊群效应
- 只有head的后继才有机会获取锁

### Q5: 如何实现可重入？

**答案**：
```java
// 检查是否是当前线程持有锁
if (current == getExclusiveOwnerThread()) {
    int nextc = c + acquires; // state累加
    setState(nextc);
    return true;
}
```
- state记录重入次数
- 每次重入state+1
- 每次释放state-1
- state=0时完全释放

---

## 九、总结

### 9.1 AQS核心机制

1. **同步状态**：使用int类型的state表示
2. **等待队列**：FIFO双向链表
3. **CAS操作**：保证原子性
4. **LockSupport**：阻塞和唤醒线程
5. **模板方法**：子类实现tryAcquire/tryRelease

### 9.2 ReentrantLock实现要点

1. **可重入**：state记录重入次数
2. **公平性**：hasQueuedPredecessors检查队列
3. **非公平**：直接CAS抢锁
4. **阻塞**：park()阻塞线程
5. **唤醒**：unpark()唤醒后继

### 9.3 关键设计思想

1. **模板方法模式**：AQS定义框架，子类实现细节
2. **自旋+阻塞**：先自旋尝试，失败后阻塞
3. **CAS+volatile**：无锁化并发控制
4. **双向链表**：方便插入和遍历
5. **状态机**：Node的waitStatus状态转换

---

## 十、扩展阅读

### 10.1 其他基于AQS的同步器

- **Semaphore**：共享模式，state表示许可数
- **CountDownLatch**：共享模式，state表示计数
- **ReentrantReadWriteLock**：读写分离，state高16位表示读，低16位表示写
- **ThreadPoolExecutor.Worker**：独占模式，不可重入

### 10.2 推荐资源

- 《Java并发编程的艺术》第5章
- 《Java并发编程实战》第14章
- Doug Lea的AQS论文
- JDK源码：java.util.concurrent.locks包

---

**下一章预告**：深入学习Condition的实现原理和条件队列机制。

**Happy Learning! 🚀**
